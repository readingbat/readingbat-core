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

package com.github.readingbat.misc

import com.github.pambrose.common.util.isNotNull
import com.github.pambrose.common.util.isNull
import com.github.pambrose.common.util.newStringSalt
import com.github.pambrose.common.util.randomId
import com.github.readingbat.dsl.Challenge
import com.github.readingbat.dsl.FunctionInfo
import com.github.readingbat.dsl.InvalidConfigurationException
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.ClassCode.Companion.DISABLED_CLASS_CODE
import com.github.readingbat.misc.Constants.ADMIN_USERS
import com.github.readingbat.misc.Constants.RESP
import com.github.readingbat.misc.KeyConstants.ANSWER_HISTORY_KEY
import com.github.readingbat.misc.KeyConstants.AUTH_KEY
import com.github.readingbat.misc.KeyConstants.CHALLENGE_ANSWERS_KEY
import com.github.readingbat.misc.KeyConstants.CORRECT_ANSWERS_KEY
import com.github.readingbat.misc.KeyConstants.LIKE_DISLIKE_KEY
import com.github.readingbat.misc.KeyConstants.USER_CLASSES_KEY
import com.github.readingbat.misc.KeyConstants.USER_INFO_BROWSER_KEY
import com.github.readingbat.misc.KeyConstants.USER_INFO_KEY
import com.github.readingbat.misc.KeyConstants.USER_RESET_KEY
import com.github.readingbat.misc.RedisRoutines.scanKeys
import com.github.readingbat.posts.ChallengeHistory
import com.github.readingbat.posts.ChallengeNames
import com.github.readingbat.posts.ChallengeResults
import com.github.readingbat.posts.DashboardInfo
import com.github.readingbat.server.*
import com.github.readingbat.server.ChallengeName.Companion.ANY_CHALLENGE
import com.github.readingbat.server.Email.Companion.EMPTY_EMAIL
import com.github.readingbat.server.GroupName.Companion.ANY_GROUP
import com.github.readingbat.server.Invocation.Companion.ANY_INVOCATION
import com.github.readingbat.server.LanguageName.Companion.ANY_LANGUAGE
import com.github.readingbat.server.ResetId.Companion.EMPTY_RESET_ID
import com.github.readingbat.server.WsEndoints.classTopicName
import com.google.gson.Gson
import mu.KLogging
import redis.clients.jedis.Jedis
import redis.clients.jedis.Response
import redis.clients.jedis.Transaction
import kotlin.contracts.contract

internal class User private constructor(val id: String, val browserSession: BrowserSession?) {

  private val userInfoKey by lazy { keyOf(USER_INFO_KEY, id) }
  private val userInfoBrowserKey by lazy { keyOf(USER_INFO_BROWSER_KEY, id, browserSession?.id ?: "unassigned") }
  private val userInfoBrowserQueryKey by lazy { keyOf(USER_INFO_BROWSER_KEY, id, "*") }
  private val browserSpecificUserInfoKey by lazy {
    if (browserSession.isNull())
      logger.error { "NULL BROWSER SESSION VALUE" }
    if (browserSession.isNotNull()) userInfoBrowserKey else throw InvalidConfigurationException("Null browser session for $this")
  }
  private val userClassesKey by lazy { keyOf(USER_CLASSES_KEY, id) }
  // This key maps to a reset_id
  private val userPasswordResetKey by lazy { keyOf(USER_RESET_KEY, id) }

  fun browserSessions(redis: Jedis) = redis.scanKeys(userInfoBrowserQueryKey).toList()

  fun correctAnswers(redis: Jedis) = redis.scanKeys(correctAnswersKey(ANY_LANGUAGE, ANY_GROUP, ANY_CHALLENGE)).toList()

  fun likeDislikes(redis: Jedis) = redis.scanKeys(likeDislikeKey(ANY_LANGUAGE, ANY_GROUP, ANY_CHALLENGE)).toList()

  fun challenges(redis: Jedis) = redis.scanKeys(challengeAnswersKey(ANY_LANGUAGE, ANY_GROUP, ANY_CHALLENGE)).toList()

  fun invocations(redis: Jedis) =
    redis.scanKeys(answerHistoryKey(ANY_LANGUAGE, ANY_GROUP, ANY_CHALLENGE, ANY_INVOCATION)).toList()

  fun classCodes(redis: Jedis) = redis.smembers(userClassesKey).map { ClassCode(it) }

  fun email(redis: Jedis) = redis.hget(userInfoKey, EMAIL_FIELD)?.let { Email(it) } ?: EMPTY_EMAIL

  fun isAdmin(redis: Jedis) = email(redis).value in ADMIN_USERS

  fun isNotAdmin(redis: Jedis) = !isAdmin(redis)

  fun name(redis: Jedis) = redis.hget(userInfoKey, NAME_FIELD) ?: ""

  fun salt(redis: Jedis) = redis.hget(userInfoKey, SALT_FIELD) ?: throw DataException("Missing salt field: $this")

  fun digest(redis: Jedis) = redis.hget(userInfoKey, DIGEST_FIELD) ?: throw DataException("Missing digest field: $this")

  fun lookupDigestInfoByUser(redis: Jedis): Pair<String, String> {
    val salt = redis.hget(userInfoKey, SALT_FIELD) ?: ""
    val digest = redis.hget(userInfoKey, DIGEST_FIELD) ?: ""
    return salt to digest
  }

  fun passwordResetKey(redis: Jedis): String? = redis.get(userPasswordResetKey)

  fun isValidUserInfoKey(redis: Jedis) = redis.hlen(userInfoKey) > 0

  fun assignDigest(redis: Jedis, newDigest: String): Long = redis.hset(userInfoKey, DIGEST_FIELD, newDigest)

  fun assignDigest(tx: Transaction, newDigest: String): Response<Long> = tx.hset(userInfoKey, DIGEST_FIELD, newDigest)

  private fun correctAnswersKey(names: ChallengeNames) =
    correctAnswersKey(names.languageName, names.groupName, names.challengeName)

  fun deletePasswordResetKey(tx: Transaction): Response<Long> = tx.del(userPasswordResetKey)

  fun correctAnswersKey(languageName: LanguageName, groupName: GroupName, challengeName: ChallengeName) =
    keyOf(CORRECT_ANSWERS_KEY, AUTH_KEY, id, md5Of(languageName, groupName, challengeName))

  private fun likeDislikeKey(names: ChallengeNames) =
    likeDislikeKey(names.languageName, names.groupName, names.challengeName)

  fun likeDislikeKey(languageName: LanguageName, groupName: GroupName, challengeName: ChallengeName) =
    keyOf(LIKE_DISLIKE_KEY, AUTH_KEY, id, md5Of(languageName, groupName, challengeName))

  private fun challengeAnswersKey(names: ChallengeNames) =
    challengeAnswersKey(names.languageName, names.groupName, names.challengeName)

  private fun challengeAnswersKey(languageName: LanguageName, groupName: GroupName, challengeName: ChallengeName) =
    keyOf(CHALLENGE_ANSWERS_KEY, AUTH_KEY, id, md5Of(languageName, groupName, challengeName))

  private fun answerHistoryKey(names: ChallengeNames, invocation: Invocation) =
    answerHistoryKey(names.languageName, names.groupName, names.challengeName, invocation)

  fun answerHistoryKey(languageName: LanguageName,
                       groupName: GroupName,
                       challengeName: ChallengeName,
                       invocation: Invocation) =
    keyOf(ANSWER_HISTORY_KEY, AUTH_KEY, id, md5Of(languageName, groupName, challengeName, invocation))

  private fun assignEnrolledClassCode(classCode: ClassCode, tx: Transaction): Response<Long> =
    tx.hset(userInfoKey, ENROLLED_CLASS_CODE_FIELD, classCode.value)

  fun assignActiveClassCode(classCode: ClassCode, resetPreviousClassCode: Boolean, redis: Jedis) {
    redis.hset(browserSpecificUserInfoKey, ACTIVE_CLASS_CODE_FIELD, classCode.value)

    if (resetPreviousClassCode)
      redis.hset(browserSpecificUserInfoKey, PREVIOUS_TEACHER_CLASS_CODE_FIELD, classCode.value)
  }

  fun resetActiveClassCode(tx: Transaction): Response<Long> =
    tx.hset(browserSpecificUserInfoKey, ACTIVE_CLASS_CODE_FIELD, DISABLED_CLASS_CODE.value)

  private fun interestedInActiveClassCode(classCode: ClassCode, redis: Jedis?): Boolean {
    return when {
      isNull() || redis.isNull() -> false
      else -> {
        val pattern = userInfoBrowserQueryKey
        redis.scanKeys(pattern).filter { redis.hget(it, ACTIVE_CLASS_CODE_FIELD) == classCode.value }.any()
      }
    }
  }

  private fun isEnrolled(classCode: ClassCode, redis: Jedis) =
    redis.sismember(classCode.classCodeEnrollmentKey, id) ?: false

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
    if (classCode.isNotEnabled) {
      throw DataException("Not enrolled in a class")
    }
    else {
      // This should always be true
      val enrolled = classCode.isValid(redis) && isEnrolled(classCode, redis)
      redis.multi().also { tx ->
        assignEnrolledClassCode(DISABLED_CLASS_CODE, tx)
        if (enrolled)
          classCode.removeEnrollee(this, tx)
        tx.exec()
      }
    }
  }

  fun deleteClassCode(classCode: ClassCode, enrollees: List<User>, tx: Transaction) {
    // Delete class description
    tx.del(classCode.classInfoKey)

    // Remove classcode from list of classes created by user
    tx.srem(userClassesKey, classCode.value)

    // Reset every enrollee's enrolled class
    enrollees.forEach { it.assignEnrolledClassCode(DISABLED_CLASS_CODE, tx) }

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

    tx.set(userPasswordResetKey, newResetId.value)
    tx.set(newResetId.passwordResetKey, email.value)
  }

  fun deleteUser(redis: Jedis) {
    val name = name(redis)
    val email = email(redis)
    val classCodes = classCodes(redis)
    val browserSessions = browserSessions(redis)
    val correctAnswers = correctAnswers(redis)
    val likeDislikes = likeDislikes(redis)
    val challenges = challenges(redis)
    val invocations = invocations(redis)

    val previousResetId = redis.get(userPasswordResetKey)?.let { ResetId(it) } ?: EMPTY_RESET_ID
    val enrolleePairs = classCodes.map { it to it.fetchEnrollees(redis) }

    logger.info { "Deleting User: $id $name" }
    logger.info { "User Email: $email" }
    logger.info { "User Info Key: $userInfoKey" }
    logger.info { "User Info browser sessions: ${browserSessions.size}" }
    logger.info { "Correct Answers: ${correctAnswers.size}" }
    logger.info { "Likes/Dislikes: ${likeDislikes.size}" }
    logger.info { "Challenges: ${challenges.size}" }
    logger.info { "Invocations: ${invocations.size}" }
    logger.info { "User Classes: $classCodes" }

    redis.multi()
      .also { tx ->
        if (previousResetId.isNotBlank()) {
          tx.del(userPasswordResetKey)
          tx.del(previousResetId.passwordResetKey)
        }

        tx.del(email(redis).userEmailKey)
        tx.del(userInfoKey)
        tx.del(userClassesKey)

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
    val dashboardInfo = DashboardInfo(id, complete, numCorrect, maxHistoryLength, history)
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
          logger.info { "Resetting $answerHistoryKey" }
          redis.set(answerHistoryKey, gson.toJson(history))

          if (shouldPublish)
            publishAnswers(classCode, funcInfo.challengeMd5, maxHistoryLength, false, 0, history, redis)
        }
      }
  }

  override fun toString() = "User(id='$id')"

  companion object : KLogging() {

    private const val EMAIL_FIELD = "email"
    private const val SALT_FIELD = "salt"
    private const val DIGEST_FIELD = "digest"
    private const val NAME_FIELD = "name"

    // Class code a user is enrolled in. Will report answers to when in student mode
    // This is not browser-id specific
    private const val ENROLLED_CLASS_CODE_FIELD = "enrolled-class-code"

    // Class code you will observe updates on when in teacher mode
    // This is browser-id specific
    private const val ACTIVE_CLASS_CODE_FIELD = "active-class-code"

    // Previous teacher class code that a user had
    // This is browser-id specific
    private const val PREVIOUS_TEACHER_CLASS_CODE_FIELD = "previous-teacher-class-code"

    internal val gson = Gson()

    fun String.toUser(browserSession: BrowserSession?) = User(this, browserSession)

    private fun newUser(browserSession: BrowserSession?) = User(randomId(25), browserSession)

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

    fun User?.fetchPreviousAnswers(challenge: Challenge,
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
      logger.info { "Created user $email ${user.id}" }

      redis.multi().also { tx ->
        tx.set(email.userEmailKey, user.id)
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
          val publish = teacherId.isNotEmpty() && teacherId.toUser(null).interestedInActiveClassCode(classCode, redis)
          logger.debug { "Publishing teacherId: $teacherId for $classCode" }
          publish
        }
        else -> false
      }

    fun User?.saveLikeDislike(browserSession: BrowserSession?, names: ChallengeNames, likeVal: Int, redis: Jedis) {
      val likeDislikeKey = likeDislikeKey(browserSession, names)

      // Record like/dislike
      if (likeDislikeKey.isNotEmpty()) {
        if (likeVal == 0)
          redis.del(likeDislikeKey)
        else
          redis.set(likeDislikeKey, likeVal.toString())
      }
    }

    fun isRegisteredEmail(email: Email, redis: Jedis) = lookupUserByEmail(email, redis).isNotNull()

    fun lookupUserByEmail(email: Email, redis: Jedis): User? {
      val id = redis.get(email.userEmailKey) ?: ""
      return if (id.isNotEmpty()) id.toUser(null) else null
    }
  }

  internal data class ChallengeAnswers(val id: String, val correctAnswers: MutableMap<String, String> = mutableMapOf())
}

internal fun User?.isValidUser(redis: Jedis): Boolean {
  contract {
    returns(true) implies (this@isValidUser is User)
  }
  return if (isNull()) false else isValidUserInfoKey(redis)
}
