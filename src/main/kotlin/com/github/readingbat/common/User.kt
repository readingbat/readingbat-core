/*
 * Copyright © 2020 Paul Ambrose (pambrose@mac.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.github.readingbat.common

import com.github.pambrose.common.redis.RedisUtils.withRedisPool
import com.github.pambrose.common.util.isNotNull
import com.github.pambrose.common.util.isNull
import com.github.pambrose.common.util.newStringSalt
import com.github.pambrose.common.util.randomId
import com.github.readingbat.common.ClassCode.Companion.DISABLED_CLASS_CODE
import com.github.readingbat.common.Constants.RESP
import com.github.readingbat.common.Constants.UNASSIGNED
import com.github.readingbat.common.KeyConstants.ANSWER_HISTORY_KEY
import com.github.readingbat.common.KeyConstants.AUTH_KEY
import com.github.readingbat.common.KeyConstants.CHALLENGE_ANSWERS_KEY
import com.github.readingbat.common.KeyConstants.CORRECT_ANSWERS_KEY
import com.github.readingbat.common.KeyConstants.LIKE_DISLIKE_KEY
import com.github.readingbat.common.KeyConstants.USER_CLASSES_KEY
import com.github.readingbat.common.KeyConstants.USER_INFO_BROWSER_KEY
import com.github.readingbat.common.KeyConstants.USER_INFO_KEY
import com.github.readingbat.common.KeyConstants.USER_RESET_KEY
import com.github.readingbat.common.RedisUtils.scanKeys
import com.github.readingbat.dsl.Challenge
import com.github.readingbat.dsl.DataException
import com.github.readingbat.dsl.InvalidConfigurationException
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.posts.ChallengeHistory
import com.github.readingbat.posts.ChallengeNames
import com.github.readingbat.posts.ChallengeResults
import com.github.readingbat.posts.DashboardInfo
import com.github.readingbat.server.*
import com.github.readingbat.server.ChallengeName.Companion.ANY_CHALLENGE
import com.github.readingbat.server.Email.Companion.UNKNOWN_EMAIL
import com.github.readingbat.server.FullName.Companion.UNKNOWN_FULLNAME
import com.github.readingbat.server.GroupName.Companion.ANY_GROUP
import com.github.readingbat.server.Invocation.Companion.ANY_INVOCATION
import com.github.readingbat.server.LanguageName.Companion.ANY_LANGUAGE
import com.github.readingbat.server.ReadingBatServer.adminUsers
import com.github.readingbat.server.ReadingBatServer.redisPool
import com.github.readingbat.server.ReadingBatServer.useRdbms
import com.github.readingbat.server.ResetId.Companion.EMPTY_RESET_ID
import com.github.readingbat.server.WsEndoints.classTopicName
import com.google.gson.Gson
import mu.KLogging
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import redis.clients.jedis.Jedis
import redis.clients.jedis.Response
import redis.clients.jedis.Transaction
import redis.clients.jedis.params.SetParams
import kotlin.contracts.contract
import kotlin.time.measureTime
import kotlin.time.minutes

internal class User private constructor(redis: Jedis?, val userId: String, val browserSession: BrowserSession?) {
  private val userInfoKey = keyOf(USER_INFO_KEY, userId)
  private val dbmsId: Long
  private val saltBacking: String
  private var digestBacking: String
  val email: Email
  val name: FullName

  val salt: String
    get() = if (saltBacking.isBlank()) throw DataException("Missing salt field") else saltBacking
  val digest: String
    get() = if (digestBacking.isBlank()) throw DataException("Missing digest field") else digestBacking

  init {
    measureTime {
      if (useRdbms) {
        val row =
          transaction {
            Users.slice(Users.id, Users.email, Users.name, Users.salt, Users.digest)
              .select { Users.userId eq this@User.userId }
              .firstOrNull()
          }
        dbmsId = row?.get(Users.id)?.value ?: -1
        email = row?.get(Users.email)?.let { Email(it) } ?: UNKNOWN_EMAIL
        name = row?.get(Users.name)?.let { FullName(it) } ?: UNKNOWN_FULLNAME
        saltBacking = row?.get(Users.salt) ?: ""
        digestBacking = row?.get(Users.digest) ?: ""
      }
      else {
        dbmsId = -1
        email = redis?.hget(userInfoKey, EMAIL_FIELD)?.let { Email(it) } ?: UNKNOWN_EMAIL
        name = redis?.hget(userInfoKey, NAME_FIELD)?.let { FullName(it) } ?: UNKNOWN_FULLNAME
        saltBacking = redis?.hget(userInfoKey, SALT_FIELD) ?: ""
        digestBacking = redis?.hget(userInfoKey, DIGEST_FIELD) ?: ""
      }
    }.also { logger.info { "Selected user info in $it" } }
  }

  private val userInfoBrowserKey by lazy { keyOf(USER_INFO_BROWSER_KEY, userId, browserSession?.id ?: UNASSIGNED) }
  internal val userInfoBrowserQueryKey by lazy { keyOf(USER_INFO_BROWSER_KEY, userId, "*") }
  private val browserSpecificUserInfoKey by lazy {
    if (browserSession.isNotNull()) userInfoBrowserKey else throw InvalidConfigurationException("Null browser session for $this")
  }
  private val userClassesKey by lazy { keyOf(USER_CLASSES_KEY, userId) }

  // This key maps to a reset_id
  private val userPasswordResetKey by lazy { keyOf(USER_RESET_KEY, userId) }

  fun browserSessions(redis: Jedis) =
    if (useRdbms)
      transaction {
        BrowserSessions.slice(BrowserSessions.session_id)
          .select { (BrowserSessions.userRef eq dbmsId) }
          .map { it[BrowserSessions.session_id].toString() }
      }
    else
      redis.scanKeys(userInfoBrowserQueryKey).toList()

  fun correctAnswers(redis: Jedis) =
    if (useRdbms)
      transaction {
        UserChallengeInfo.slice(UserChallengeInfo.correct)
          .select { (UserChallengeInfo.userRef eq dbmsId) and UserChallengeInfo.correct }
          .map { it.toString() }
      }
    else
      redis.scanKeys(correctAnswersKey(ANY_LANGUAGE, ANY_GROUP, ANY_CHALLENGE))
        //.filter { it.split(KEY_SEP).size == 4 }
        //.map { redis[it].toBoolean() }
        //.filter { it }
        //.map { it.toString() }
        .toList()

  fun likeDislikes(redis: Jedis) =
    if (useRdbms)
      transaction {
        UserChallengeInfo.slice(UserChallengeInfo.likeDislike)
          .select { (UserChallengeInfo.userRef eq dbmsId) and ((UserChallengeInfo.likeDislike eq 1) or (UserChallengeInfo.likeDislike eq 2)) }
          .map { it.toString() }
      }
    else
      redis.scanKeys(likeDislikeKey(ANY_LANGUAGE, ANY_GROUP, ANY_CHALLENGE))
        //.filter { it.split(KEY_SEP).size == 4 }
        //.map { redis[it].toInt() }
        //.filter { it > 0 }
        //.map { it.toString() }
        .toList()

  fun classCodes(redis: Jedis) = redis.smembers(userClassesKey).map { ClassCode(it) }

  fun isValidUserInfoKey(redis: Jedis) = redis.hlen(userInfoKey) > 0

  fun assignDigest(redis: Jedis, newDigest: String) =
    if (useRdbms)
      transaction {
        Users.update({ Users.id eq dbmsId }) { row ->
          row[digest] = newDigest
          digestBacking = newDigest
        }
      }
    else
      redis.hset(userInfoKey, DIGEST_FIELD, newDigest)

  fun assignDigest(tx: Transaction, newDigest: String) {
    if (useRdbms)
      transaction {
        Users.update({ Users.id eq dbmsId }) { row ->
          row[digest] = newDigest
          digestBacking = newDigest
        }
      }
    else
      tx.hset(userInfoKey, DIGEST_FIELD, newDigest)
  }

  fun passwordResetKey(redis: Jedis): String? = redis.get(userPasswordResetKey)

  fun deletePasswordResetKey(tx: Transaction): Response<Long> = tx.del(userPasswordResetKey)

  fun invocations(redis: Jedis) =
    redis.scanKeys(answerHistoryKey(ANY_LANGUAGE, ANY_GROUP, ANY_CHALLENGE, ANY_INVOCATION)).toList()

  fun challenges(redis: Jedis) = redis.scanKeys(challengeAnswersKey(ANY_LANGUAGE, ANY_GROUP, ANY_CHALLENGE)).toList()

  private fun correctAnswersKey(names: ChallengeNames) =
    correctAnswersKey(names.languageName, names.groupName, names.challengeName)

  fun correctAnswersKey(languageName: LanguageName, groupName: GroupName, challengeName: ChallengeName) =
    keyOf(CORRECT_ANSWERS_KEY,
          AUTH_KEY,
          userId,
          if (languageName == ANY_LANGUAGE && groupName == ANY_GROUP && challengeName == ANY_CHALLENGE)
            "*"
          else
            md5Of(languageName, groupName, challengeName))

  private fun likeDislikeKey(names: ChallengeNames) =
    likeDislikeKey(names.languageName, names.groupName, names.challengeName)

  fun likeDislikeKey(languageName: LanguageName, groupName: GroupName, challengeName: ChallengeName) =
    keyOf(LIKE_DISLIKE_KEY,
          AUTH_KEY,
          userId,
          if (languageName == ANY_LANGUAGE && groupName == ANY_GROUP && challengeName == ANY_CHALLENGE)
            "*"
          else
            md5Of(languageName, groupName, challengeName))

  private fun challengeAnswersKey(names: ChallengeNames) =
    challengeAnswersKey(names.languageName, names.groupName, names.challengeName)

  private fun challengeAnswersKey(languageName: LanguageName, groupName: GroupName, challengeName: ChallengeName) =
    keyOf(CHALLENGE_ANSWERS_KEY,
          AUTH_KEY,
          userId,
          if (languageName == ANY_LANGUAGE && groupName == ANY_GROUP && challengeName == ANY_CHALLENGE)
            "*"
          else
            md5Of(languageName, groupName, challengeName))

  private fun answerHistoryKey(names: ChallengeNames, invocation: Invocation) =
    answerHistoryKey(names.languageName, names.groupName, names.challengeName, invocation)

  fun answerHistoryKey(languageName: LanguageName,
                       groupName: GroupName,
                       challengeName: ChallengeName,
                       invocation: Invocation) =
    keyOf(ANSWER_HISTORY_KEY,
          AUTH_KEY,
          userId,
          if (languageName == ANY_LANGUAGE && groupName == ANY_GROUP && challengeName == ANY_CHALLENGE && invocation == ANY_INVOCATION)
            "*"
          else
            md5Of(languageName, groupName, challengeName, invocation))

  private fun assignEnrolledClassCode(classCode: ClassCode, tx: Transaction): Response<Long> =
    tx.hset(userInfoKey, ENROLLED_CLASS_CODE_FIELD, classCode.value)

  fun assignActiveClassCode(classCode: ClassCode, resetPreviousClassCode: Boolean, redis: Jedis) {
    redis.hset(browserSpecificUserInfoKey, ACTIVE_CLASS_CODE_FIELD, classCode.value)

    if (resetPreviousClassCode)
      redis.hset(browserSpecificUserInfoKey, PREVIOUS_TEACHER_CLASS_CODE_FIELD, classCode.value)
  }

  fun resetActiveClassCode(tx: Transaction) {
    tx.hset(browserSpecificUserInfoKey, ACTIVE_CLASS_CODE_FIELD, DISABLED_CLASS_CODE.value)
    tx.hset(browserSpecificUserInfoKey, PREVIOUS_TEACHER_CLASS_CODE_FIELD, DISABLED_CLASS_CODE.value)
  }

  private fun interestedInActiveClassCode(classCode: ClassCode, redis: Jedis?): Boolean {
    return when {
      isNull() || redis.isNull() -> false
      else -> {
        val pattern = userInfoBrowserQueryKey
        redis.scanKeys(pattern).filter { redis.hget(it, ACTIVE_CLASS_CODE_FIELD) == classCode.value }.any()
      }
    }
  }

  internal fun isEnrolled(classCode: ClassCode, redis: Jedis) =
    redis.sismember(classCode.classCodeEnrollmentKey, userId) ?: false

  internal fun isNotEnrolled(classCode: ClassCode, redis: Jedis) = !isEnrolled(classCode, redis)

  fun enrollInClass(classCode: ClassCode, redis: Jedis) {
    when {
      classCode.isNotEnabled -> throw DataException("Not reportable class code")
      classCode.isNotValid(redis) -> throw DataException("Invalid class code: $classCode")
      isEnrolled(classCode, redis) -> throw DataException("Already enrolled in class $classCode")
      else -> {
        val previousClassCode = fetchEnrolledClassCode(redis)
        redis.multi().also { tx ->
          // Remove if already enrolled in another class
          if (previousClassCode.isEnabled)
            previousClassCode.removeEnrollee(this, tx)

          assignEnrolledClassCode(classCode, tx)

          classCode.addEnrollee(this, tx)

          tx.exec()
        }
      }
    }
  }

  fun withdrawFromClass(classCode: ClassCode, redis: Jedis) {
    if (classCode.isNotEnabled)
      throw DataException("Not enrolled in a class")

    // This should always be true
    val enrolled = classCode.isValid(redis) && isEnrolled(classCode, redis)
    redis.multi().also { tx ->
      assignEnrolledClassCode(DISABLED_CLASS_CODE, tx)
      if (enrolled)
        classCode.removeEnrollee(this, tx)
      tx.exec()
    }
  }

  fun deleteClassCode(classCode: ClassCode, enrollees: List<User>, tx: Transaction) {
    // Reset every enrollee's enrolled class and remove from class
    enrollees
      .forEach {
        logger.info { "Removing ${it.userId} from $classCode" }
        classCode.removeEnrollee(it, tx)

        logger.info { "Assigning ${it.userId} to $DISABLED_CLASS_CODE" }
        it.assignEnrolledClassCode(DISABLED_CLASS_CODE, tx)
      }

    // Delete class description
    tx.del(classCode.classInfoKey)

    // Remove classcode from list of classes created by user
    tx.srem(userClassesKey, classCode.value)

    // Delete enrollee list
    classCode.deleteAllEnrollees(tx)
  }

  fun addClassCreated(classCode: ClassCode, tx: Transaction) {
    tx.sadd(userClassesKey, classCode.value)
  }

  fun classCount(redis: Jedis) = redis.smembers(userClassesKey).count()

  fun isUniqueClassDesc(classDesc: String, redis: Jedis) =
    redis.smembers(userClassesKey)
      .asSequence()
      .map { ClassCode(it) }
      .filter { classCode -> classDesc == classCode.fetchClassDesc(redis) }
      .none()

  fun savePasswordResetKey(email: Email, previousResetId: ResetId, newResetId: ResetId, tx: Transaction) {
    if (previousResetId.isNotBlank()) {
      tx.del(userPasswordResetKey)
      tx.del(previousResetId.passwordResetKey)
    }

    val expiration = SetParams().ex(15.minutes.inSeconds.toInt())
    tx.set(userPasswordResetKey, newResetId.value, expiration)
    tx.set(newResetId.passwordResetKey, email.value, expiration)
  }

  fun deleteUser(redis: Jedis) {
    val classCodes = classCodes(redis)
    val browserSessions = browserSessions(redis)
    val correctAnswers = correctAnswers(redis)
    val likeDislikes = likeDislikes(redis)
    val challenges = challenges(redis)
    val invocations = invocations(redis)

    val previousResetId = redis.get(userPasswordResetKey)?.let { ResetId(it) } ?: EMPTY_RESET_ID
    val enrolleePairs = classCodes.map { it to it.fetchEnrollees(redis) }

    logger.info { "Deleting User: $userId $name" }
    logger.info { "User Email: $email" }
    logger.info { "User Info Key: $userInfoKey" }
    logger.info { "User Info browser sessions: ${browserSessions.size}" }
    logger.info { "Correct Answers: ${correctAnswers.size}" }
    logger.info { "Likes/Dislikes: ${likeDislikes.size}" }
    logger.info { "Challenges: ${challenges.size}" }
    logger.info { "Invocations: ${invocations.size}" }
    logger.info { "User Classes: $classCodes" }

    val classCode = fetchEnrolledClassCode(redis)
    val enrolled = classCode.isEnabled && classCode.isValid(redis) && isEnrolled(classCode, redis)

    redis.multi()
      .also { tx ->
        if (previousResetId.isNotBlank()) {
          tx.del(userPasswordResetKey)
          tx.del(previousResetId.passwordResetKey)
        }

        tx.del(email.userEmailKey)
        tx.del(userInfoKey)
        tx.del(userClassesKey)

        // Withdraw from class
        if (enrolled) {
          logger.info { "Withdrawing from $classCode" }
          classCode.removeEnrollee(this, tx)
        }

        browserSessions.forEach { tx.del(it) }
        correctAnswers.forEach { tx.del(it) }
        likeDislikes.forEach { tx.del(it) }
        challenges.forEach { tx.del(it) }
        invocations.forEach { tx.del(it) }

        // Delete class info
        enrolleePairs.forEach { (classCode, enrollees) -> deleteClassCode(classCode, enrollees, tx) }

        tx.exec()
      }
  }

  fun publishAnswers(classCode: ClassCode,
                     challengeMd5: ChallengeMd5,
                     maxHistoryLength: Int,
                     complete: Boolean,
                     numCorrect: Int,
                     history: ChallengeHistory,
                     redis: Jedis) {
    // Publish to challenge dashboard
    logger.debug { "Publishing user answers to $classCode on $challengeMd5 for $this" }
    val dashboardInfo = DashboardInfo(userId, complete, numCorrect, maxHistoryLength, history)
    redis.publish(classTopicName(classCode, challengeMd5.value), gson.toJson(dashboardInfo))
  }

  fun resetHistory(funcInfo: FunctionInfo,
                   languageName: LanguageName,
                   groupName: GroupName,
                   challengeName: ChallengeName,
                   maxHistoryLength: Int,
                   redis: Jedis) {
    val classCode = fetchEnrolledClassCode(redis)
    val shouldPublish = shouldPublish(classCode, redis)

    funcInfo.invocations
      .map { ChallengeResults(invocation = it) }
      .forEach { result ->
        // Reset the history of each answer on a per-invocation basis
        val answerHistoryKey = answerHistoryKey(languageName, groupName, challengeName, result.invocation)
        if (answerHistoryKey.isNotEmpty()) {
          val history = ChallengeHistory(result.invocation).apply { markUnanswered() }
          logger.debug { "Resetting $answerHistoryKey" }
          redis.set(answerHistoryKey, gson.toJson(history))

          if (shouldPublish)
            publishAnswers(classCode, funcInfo.challengeMd5, maxHistoryLength, false, 0, history, redis)
        }
      }
  }

  override fun toString() = "User(userId='$userId')"

  companion object : KLogging() {

    internal const val EMAIL_FIELD = "email"
    internal const val SALT_FIELD = "salt"
    internal const val DIGEST_FIELD = "digest"
    internal const val NAME_FIELD = "name"

    // Class code a user is enrolled in. Will report answers to when in student mode
    // This is not browser-id specific
    internal const val ENROLLED_CLASS_CODE_FIELD = "enrolled-class-code"

    // Class code you will observe updates on when in teacher mode
    // This is browser-id specific
    private const val ACTIVE_CLASS_CODE_FIELD = "active-class-code"

    // Previous teacher class code that a user had
    // This is browser-id specific
    private const val PREVIOUS_TEACHER_CLASS_CODE_FIELD = "previous-teacher-class-code"

    internal val gson = Gson()

    fun String.toUser(redis: Jedis, browserSession: BrowserSession?) = User(redis, this, browserSession)

    fun String.toUser(browserSession: BrowserSession?) =
      redisPool?.withRedisPool { redis ->
        User(redis, this, browserSession)
      } ?: User(null, this, browserSession)

    private fun newUser(browserSession: BrowserSession?) = User(null, randomId(25), browserSession)

    fun User?.fetchActiveClassCode(redis: Jedis?): ClassCode {
      return when {
        isNull() || redis.isNull() -> DISABLED_CLASS_CODE
        else -> redis.hget(browserSpecificUserInfoKey, ACTIVE_CLASS_CODE_FIELD)?.let { ClassCode(it) }
          ?: DISABLED_CLASS_CODE
      }
    }

    fun User?.fetchPreviousTeacherClassCode(redis: Jedis?) =
      when {
        isNull() || redis.isNull() -> DISABLED_CLASS_CODE
        else -> redis.hget(browserSpecificUserInfoKey, PREVIOUS_TEACHER_CLASS_CODE_FIELD)?.let { ClassCode(it) }
          ?: DISABLED_CLASS_CODE
      }

    fun User?.fetchEnrolledClassCode(redis: Jedis) =
      when {
        isNull() -> DISABLED_CLASS_CODE
        else -> redis.hget(userInfoKey, ENROLLED_CLASS_CODE_FIELD)?.let { ClassCode(it) } ?: DISABLED_CLASS_CODE
      }

    fun User?.likeDislikeKey(browserSession: BrowserSession?, names: ChallengeNames) =
      this?.likeDislikeKey(names) ?: browserSession?.likeDislikeKey(names) ?: ""

    fun User?.likeDislikeKey(browserSession: BrowserSession?,
                             languageName: LanguageName,
                             groupName: GroupName,
                             challengeName: ChallengeName) =
      this?.likeDislikeKey(languageName, groupName, challengeName)
        ?: browserSession?.likeDislikeKey(languageName, groupName, challengeName)
        ?: ""

    fun User?.correctAnswersKey(browserSession: BrowserSession?, names: ChallengeNames) =
      this?.correctAnswersKey(names) ?: browserSession?.correctAnswersKey(names) ?: ""

    fun User?.correctAnswersKey(browserSession: BrowserSession?, challenge: Challenge) =
      this?.correctAnswersKey(challenge.languageName, challenge.groupName, challenge.challengeName)
        ?: browserSession?.correctAnswersKey(challenge.languageName, challenge.groupName, challenge.challengeName) ?: ""

    fun User?.correctAnswersKey(browserSession: BrowserSession?,
                                languageName: LanguageName,
                                groupName: GroupName,
                                challengeName: ChallengeName) =
      this?.correctAnswersKey(languageName, groupName, challengeName)
        ?: browserSession?.correctAnswersKey(languageName, groupName, challengeName)
        ?: ""

    fun User?.challengeAnswersKey(browserSession: BrowserSession?, names: ChallengeNames) =
      this?.challengeAnswersKey(names) ?: browserSession?.challengeAnswerKey(names) ?: ""

    fun User?.challengeAnswersKey(browserSession: BrowserSession?,
                                  languageName: LanguageName,
                                  groupName: GroupName,
                                  challengeName: ChallengeName) =
      this?.challengeAnswersKey(languageName, groupName, challengeName)
        ?: browserSession?.challengeAnswerKey(languageName, groupName, challengeName)
        ?: ""

    fun User?.challengeAnswersKey(browserSession: BrowserSession?, challenge: Challenge) =
      challengeAnswersKey(browserSession,
                          challenge.languageType.languageName,
                          challenge.groupName,
                          challenge.challengeName)

    private fun User?.answerHistoryKey(browserSession: BrowserSession?, names: ChallengeNames, invocation: Invocation) =
      this?.answerHistoryKey(names, invocation) ?: browserSession?.answerHistoryKey(names, invocation) ?: ""

    fun User?.fetchPreviousResponses(challenge: Challenge,
                                     browserSession: BrowserSession?,
                                     redis: Jedis?): Map<String, String> =
      if (redis.isNull())
        kotlinx.html.emptyMap
      else {
        val languageName = challenge.languageType.languageName
        val groupName = challenge.groupName
        val challengeName = challenge.challengeName
        val challengeAnswersKey = challengeAnswersKey(browserSession, languageName, groupName, challengeName)
        if (challengeAnswersKey.isNotEmpty()) redis.hgetAll(challengeAnswersKey) else emptyMap()
      }

    fun createUser(name: FullName,
                   email: Email,
                   password: Password,
                   browserSession: BrowserSession?,
                   redis: Jedis): User {
      // The userName (email) is stored in a single KV pair, enabling changes to the userName
      // Three things are stored:
      // email -> userId
      // userId -> salt and sha256-encoded digest

      val user = newUser(browserSession)
      val salt = newStringSalt()
      logger.info { "Created user $email ${user.userId}" }

      redis.multi().also { tx ->
        tx.set(email.userEmailKey, user.userId)
        tx.hset(user.userInfoKey, mapOf(NAME_FIELD to name.value,
                                        EMAIL_FIELD to email.value,
                                        SALT_FIELD to salt,
                                        DIGEST_FIELD to password.sha256(salt),
                                        ENROLLED_CLASS_CODE_FIELD to DISABLED_CLASS_CODE.value))

        tx.hset(user.userInfoBrowserKey, mapOf(ACTIVE_CLASS_CODE_FIELD to DISABLED_CLASS_CODE.value,
                                               PREVIOUS_TEACHER_CLASS_CODE_FIELD to DISABLED_CLASS_CODE.value))

        tx.exec()
      }

      return user
    }

    fun User?.saveChallengeAnswers(browserSession: BrowserSession?,
                                   content: ReadingBatContent,
                                   names: ChallengeNames,
                                   paramMap: Map<String, String>,
                                   funcInfo: FunctionInfo,
                                   userResponses: List<Map.Entry<String, List<String>>>,
                                   results: List<ChallengeResults>,
                                   redis: Jedis) {
      val correctAnswersKey = correctAnswersKey(browserSession, names)
      val challengeAnswerKey = challengeAnswersKey(browserSession, names)

      val complete = results.all { it.correct }
      val numCorrect = results.count { it.correct }

      // Record if all answers were correct
      if (correctAnswersKey.isNotEmpty())
        redis.set(correctAnswersKey, complete.toString())

      if (challengeAnswerKey.isNotEmpty()) {
        // Save the last answers given
        userResponses.indices
          .map { i ->
            val userResponse =
              paramMap[RESP + i]?.trim() ?: throw InvalidConfigurationException("Missing user response")
            //if (userResponses.isNotEmpty()) funcInfo.invocations[i] to userResp else null
            funcInfo.invocations[i] to userResponse
          }
          .toMap()
          .forEach { (invocation, userResponse) ->
            redis.hset(challengeAnswerKey, invocation.value, userResponse)
          }
      }

      val classCode = fetchEnrolledClassCode(redis)
      val shouldPublish = shouldPublish(classCode, redis)

      // Save the history of each answer on a per-invocation basis
      for (result in results) {
        val answerHistoryKey = answerHistoryKey(browserSession, names, result.invocation)
        if (answerHistoryKey.isNotEmpty()) {
          val history =
            gson.fromJson(redis[answerHistoryKey], ChallengeHistory::class.java) ?: ChallengeHistory(result.invocation)

          when {
            !result.answered -> history.markUnanswered()
            result.correct -> history.markCorrect(result.userResponse)
            else -> history.markIncorrect(result.userResponse)
          }

          val json = gson.toJson(history)
          logger.debug { "Saving: $json to $answerHistoryKey" }
          redis.set(answerHistoryKey, json)

          if (shouldPublish)
            this?.publishAnswers(classCode,
                                 funcInfo.challengeMd5,
                                 content.maxHistoryLength,
                                 complete,
                                 numCorrect,
                                 history,
                                 redis)
        }
      }
    }

    private fun User?.shouldPublish(classCode: ClassCode, redis: Jedis) =
      when {
        isNull() -> false
        classCode.isEnabled -> {
          // Check to see if the teacher that owns class has it set as their active class in one of the sessions
          val teacherId = classCode.fetchClassTeacherId(redis)
          val publish =
            teacherId.isNotEmpty() && teacherId.toUser(redis, null).interestedInActiveClassCode(classCode, redis)
          logger.debug { "Publishing teacherId: $teacherId for $classCode" }
          publish
        }
        else -> false
      }

    fun User?.saveLikeDislike(browserSession: BrowserSession?, names: ChallengeNames, likeVal: Int, redis: Jedis) =
      likeDislikeKey(browserSession, names).let {
        if (it.isNotEmpty())
          redis.apply { if (likeVal == 0) del(it) else set(it, likeVal.toString()) }
      }

    fun isRegisteredEmail(email: Email, redis: Jedis) = lookupUserByEmail(email, redis).isNotNull()

    fun isNotRegisteredEmail(email: Email, redis: Jedis) = !isRegisteredEmail(email, redis)

    fun lookupUserByEmail(email: Email, redis: Jedis): User? {
      val id = redis.get(email.userEmailKey) ?: ""
      return if (id.isNotEmpty()) id.toUser(redis, null) else null
    }
  }

  internal data class ChallengeAnswers(val id: String, val correctAnswers: MutableMap<String, String> = mutableMapOf())
}

internal fun User?.isAdminUser(redis: Jedis) = isValidUser(redis) && email.value in adminUsers

internal fun User?.isNotAdminUser(redis: Jedis) = !isAdminUser(redis)

internal fun User?.isNotValidUser(redis: Jedis): Boolean {
  contract {
    returns(false) implies (this@isNotValidUser is User)
  }
  return !isValidUser(redis)
}

internal fun User?.isValidUser(redis: Jedis): Boolean {
  contract {
    returns(true) implies (this@isValidUser is User)
  }
  return if (isNull()) false else isValidUserInfoKey(redis)
}