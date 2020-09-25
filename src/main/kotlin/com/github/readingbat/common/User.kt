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
import com.github.readingbat.common.CommonUtils.keyOf
import com.github.readingbat.common.CommonUtils.md5Of
import com.github.readingbat.common.Constants.RESP
import com.github.readingbat.common.Constants.UNASSIGNED
import com.github.readingbat.common.KeyConstants.ANSWER_HISTORY_KEY
import com.github.readingbat.common.KeyConstants.AUTH_KEY
import com.github.readingbat.common.KeyConstants.CHALLENGE_ANSWERS_KEY
import com.github.readingbat.common.KeyConstants.CORRECT_ANSWERS_KEY
import com.github.readingbat.common.KeyConstants.KEY_SEP
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
import com.github.readingbat.server.ReadingBatServer.usePostgres
import com.github.readingbat.server.ResetId.Companion.EMPTY_RESET_ID
import com.github.readingbat.server.WsEndoints.classTopicName
import com.google.gson.Gson
import mu.KLogging
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.joda.time.DateTimeZone.UTC
import redis.clients.jedis.Jedis
import redis.clients.jedis.Transaction
import redis.clients.jedis.params.SetParams
import kotlin.contracts.contract
import kotlin.time.measureTime
import kotlin.time.minutes

internal class User private constructor(redis: Jedis?, val userId: String, val browserSession: BrowserSession?) {
  val dbmsId: Long
  val email: Email
  val name: FullName
  var enrolledClassCode: ClassCode
  private val saltBacking: String
  private var digestBacking: String

  private val userInfoKey = keyOf(USER_INFO_KEY, userId)

  val salt: String
    get() = if (saltBacking.isBlank()) throw DataException("Missing salt field") else saltBacking
  val digest: String
    get() = if (digestBacking.isBlank()) throw DataException("Missing digest field") else digestBacking

  init {
    measureTime {
      if (usePostgres) {
        val row =
          transaction {
            Users
              .select { Users.userId eq this@User.userId }
              .firstOrNull()
          }
        dbmsId = row?.get(Users.id)?.value ?: -1
        email = row?.get(Users.email)?.let { Email(it) } ?: UNKNOWN_EMAIL
        name = row?.get(Users.name)?.let { FullName(it) } ?: UNKNOWN_FULLNAME
        enrolledClassCode = row?.get(Users.enrolledClassCode)?.let { ClassCode(it) } ?: DISABLED_CLASS_CODE
        saltBacking = row?.get(Users.salt) ?: ""
        digestBacking = row?.get(Users.digest) ?: ""
      }
      else {
        dbmsId = -1
        email = redis?.hget(userInfoKey, EMAIL_FIELD)?.let { Email(it) } ?: UNKNOWN_EMAIL
        name = redis?.hget(userInfoKey, NAME_FIELD)?.let { FullName(it) } ?: UNKNOWN_FULLNAME
        enrolledClassCode =
          redis?.hget(userInfoKey, ENROLLED_CLASS_CODE_FIELD)?.let { ClassCode(it) } ?: DISABLED_CLASS_CODE
        saltBacking = redis?.hget(userInfoKey, SALT_FIELD) ?: ""
        digestBacking = redis?.hget(userInfoKey, DIGEST_FIELD) ?: ""
      }
    }.also { logger.info { "Selected user info in $it" } }
  }

  private val userInfoBrowserKey by lazy { keyOf(USER_INFO_BROWSER_KEY, userId, browserSession?.id ?: UNASSIGNED) }
  val userInfoBrowserQueryKey by lazy { keyOf(USER_INFO_BROWSER_KEY, userId, "*") }
  private val browserSpecificUserInfoKey by lazy {
    if (browserSession.isNotNull()) userInfoBrowserKey else throw InvalidConfigurationException("Null browser session for $this")
  }
  val userClassesKey by lazy { keyOf(USER_CLASSES_KEY, userId) }

  // This key maps to a reset_id
  private val userPasswordResetKey by lazy { keyOf(USER_RESET_KEY, userId) }

  private val sessionDbmsId: Long
    get() = browserSession?.sessionDbmsId ?: throw InvalidConfigurationException("Missing browser session id")

  fun browserSessions(redis: Jedis) =
    if (usePostgres)
      transaction {
        UserSessions
          .slice(UserSessions.sessionRef)
          .select { UserSessions.userRef eq dbmsId }
          .map { it[UserSessions.sessionRef].toString() }
      }
    else
      redis.scanKeys(userInfoBrowserQueryKey).toList()

  private fun interestedInActiveClassCode(classCode: ClassCode, redis: Jedis?) =
    when {
      isNull() || redis.isNull() -> false
      usePostgres ->
        transaction {
          UserSessions
            .slice(UserSessions.id.count())
            .select { (UserSessions.userRef eq dbmsId) and (UserSessions.activeClassCode eq classCode.value) }
            .map { it[UserSessions.id.count()].toInt() }
            .first() > 0
        }
      else ->
        redis.scanKeys(userInfoBrowserQueryKey)
          .filter { redis.hget(it, ACTIVE_CLASS_CODE_FIELD) == classCode.value }
          .any()
    }

  fun correctAnswers(redis: Jedis) =
    if (usePostgres)
      transaction {
        UserChallengeInfo
          .slice(UserChallengeInfo.allCorrect)
          .select { (UserChallengeInfo.userRef eq dbmsId) and UserChallengeInfo.allCorrect }
          .map { it.toString() }
      }
    else
      redis.scanKeys(correctAnswersKey(ANY_LANGUAGE, ANY_GROUP, ANY_CHALLENGE))
        .filter { it.split(KEY_SEP).size == 4 }
        .map { redis[it].toBoolean() }
        .filter { it }
        .map { it.toString() }
        .toList()

  fun likeDislikes(redis: Jedis) =
    if (usePostgres)
      transaction {
        UserChallengeInfo.slice(UserChallengeInfo.likeDislike)
          .select { (UserChallengeInfo.userRef eq dbmsId) and ((UserChallengeInfo.likeDislike eq 1) or (UserChallengeInfo.likeDislike eq 2)) }
          .map { it.toString() }
      }
    else
      redis.scanKeys(likeDislikeKey(ANY_LANGUAGE, ANY_GROUP, ANY_CHALLENGE))
        .filter { it.split(KEY_SEP).size == 4 }
        .map { redis[it].toInt() }
        .filter { it > 0 }
        .map { it.toString() }
        .toList()

  fun classCount(redis: Jedis) =
    if (usePostgres)
      transaction {
        Classes
          .slice(Classes.classCode.count())
          .select { Classes.userRef eq dbmsId }
          .map { it[Classes.classCode.count()].toInt() }
          .first().also { logger.info { "classCount() returned $it" } }
      }
    else
      redis.smembers(userClassesKey).count()

  fun addClassCode(classCode: ClassCode, classDesc: String) =
    transaction {
      Classes
        .insert { row ->
          row[userRef] = dbmsId
          row[Classes.classCode] = classCode.value
          row[description] = classDesc
        }
    }

  fun addClassCode(classCode: ClassCode, classDesc: String, tx: Transaction) {
    classCode.initializeWith(classDesc, this, tx)
    tx.sadd(userClassesKey, classCode.value)
    // Create class with no one enrolled to prevent class from being created a 2nd time
    classCode.addEnrolleePlaceholder(tx)
  }

  fun classCodes(redis: Jedis) =
    if (usePostgres)
      transaction {
        Classes
          .slice(Classes.classCode)
          .select { Classes.userRef eq dbmsId }
          .map { ClassCode(it[Classes.classCode]) }
      }
    else
      redis.smembers(userClassesKey).map { ClassCode(it) }

  fun isInDbms(redis: Jedis) =
    if (usePostgres)
      transaction {
        Users.slice(Users.id.count())
          .select { Users.id eq dbmsId }
          .map { it[Users.id.count()].toInt() }
          .first() > 0
      }
    else
      redis.hlen(userInfoKey) > 0

  fun assignDigest(newDigest: String) =
    Users
      .update({ Users.id eq dbmsId }) { row ->
        row[updated] = DateTime.now(UTC)
        row[digest] = newDigest
        digestBacking = newDigest
      }

  fun assignDigest(tx: Transaction, newDigest: String) {
    tx.hset(userInfoKey, DIGEST_FIELD, newDigest)
    digestBacking = newDigest
  }

  private fun assignEnrolledClassCode(classCode: ClassCode) =
    Users
      .update({ Users.id eq dbmsId }) { row ->
        row[updated] = DateTime.now(UTC)
        row[enrolledClassCode] = classCode.value
        this@User.enrolledClassCode = classCode
      }

  private fun assignEnrolledClassCode(classCode: ClassCode, tx: Transaction) {
    tx.hset(userInfoKey, ENROLLED_CLASS_CODE_FIELD, classCode.value)
    enrolledClassCode = classCode
  }

  fun challenges(redis: Jedis) =
    if (usePostgres)
      transaction {
        UserChallengeInfo
          .slice(UserChallengeInfo.md5)
          .select { UserChallengeInfo.userRef eq dbmsId }
          .map { it[UserChallengeInfo.md5] }.also { logger.info { "challenges() return ${it.size}" } }
      }
    else
      redis.scanKeys(challengeAnswersKey(ANY_LANGUAGE, ANY_GROUP, ANY_CHALLENGE))
        .filter { it.split(KEY_SEP).size == 4 }
        .toList()

  fun invocations(redis: Jedis) =
    if (usePostgres)
      transaction {
        UserAnswerHistory
          .slice(UserAnswerHistory.md5)
          .select { UserAnswerHistory.userRef eq dbmsId }
          .map { it[UserAnswerHistory.md5] }.also { logger.info { "invocations() return ${it.size}" } }
      }
    else
      redis.scanKeys(answerHistoryKey(ANY_LANGUAGE, ANY_GROUP, ANY_CHALLENGE, ANY_INVOCATION))
        .filter { it.split(KEY_SEP).size == 4 }
        .toList()

  fun correctAnswersKey(names: ChallengeNames) =
    correctAnswersKey(names.languageName, names.groupName, names.challengeName)

  fun correctAnswersKey(languageName: LanguageName, groupName: GroupName, challengeName: ChallengeName) =
    keyOf(CORRECT_ANSWERS_KEY,
          AUTH_KEY,
          userId,
          if (languageName == ANY_LANGUAGE && groupName == ANY_GROUP && challengeName == ANY_CHALLENGE)
            "*"
          else
            md5Of(languageName, groupName, challengeName))

  fun likeDislikeKey(names: ChallengeNames) =
    likeDislikeKey(names.languageName, names.groupName, names.challengeName)

  fun likeDislikeKey(languageName: LanguageName, groupName: GroupName, challengeName: ChallengeName) =
    keyOf(LIKE_DISLIKE_KEY,
          AUTH_KEY,
          userId,
          if (languageName == ANY_LANGUAGE && groupName == ANY_GROUP && challengeName == ANY_CHALLENGE)
            "*"
          else
            md5Of(languageName, groupName, challengeName))

  fun challengeAnswersKey(names: ChallengeNames) =
    challengeAnswersKey(names.languageName, names.groupName, names.challengeName)

  fun challengeAnswersKey(languageName: LanguageName, groupName: GroupName, challengeName: ChallengeName) =
    keyOf(CHALLENGE_ANSWERS_KEY,
          AUTH_KEY,
          userId,
          if (languageName == ANY_LANGUAGE && groupName == ANY_GROUP && challengeName == ANY_CHALLENGE)
            "*"
          else
            md5Of(languageName, groupName, challengeName))

  fun answerHistoryKey(names: ChallengeNames, invocation: Invocation) =
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

  fun historyExists(md5: String) =
    UserAnswerHistory
      .slice(UserAnswerHistory.id.count())
      .select { (UserAnswerHistory.userRef eq dbmsId) and (UserAnswerHistory.md5 eq md5) }
      .map { it[UserAnswerHistory.id.count()].toInt() }
      .first() > 0

  fun answerHistory(md5: String, invocation: Invocation) =
    UserAnswerHistory
      .slice(UserAnswerHistory.invocation,
             UserAnswerHistory.correct,
             UserAnswerHistory.incorrectAttempts,
             UserAnswerHistory.historyJson)
      .select { (UserAnswerHistory.userRef eq dbmsId) and (UserAnswerHistory.md5 eq md5) }
      .map {
        val json = it[UserAnswerHistory.historyJson]
        val history =
          mutableListOf<String>().apply { addAll(gson.fromJson(json, List::class.java) as List<String>) }

        ChallengeHistory(Invocation(it[UserAnswerHistory.invocation]),
                         it[UserAnswerHistory.correct],
                         it[UserAnswerHistory.incorrectAttempts].toInt(),
                         history)
      }
      .firstOrNull() ?: ChallengeHistory(invocation)

  fun assignActiveClassCode(classCode: ClassCode, resetPreviousClassCode: Boolean, redis: Jedis) {
    if (usePostgres)
      transaction {
        UserSessions
          .update({ (UserSessions.userRef eq dbmsId) and (UserSessions.sessionRef eq sessionDbmsId) }) { row ->
            row[updated] = DateTime.now(UTC)
            row[activeClassCode] = classCode.value
            if (resetPreviousClassCode)
              row[previousTeacherClassCode] = classCode.value
          }
      }
    else {
      redis.hset(browserSpecificUserInfoKey, ACTIVE_CLASS_CODE_FIELD, classCode.value)
      if (resetPreviousClassCode)
        redis.hset(browserSpecificUserInfoKey, PREVIOUS_TEACHER_CLASS_CODE_FIELD, classCode.value)
    }
  }

  fun resetActiveClassCode() {
    logger.info { "Resetting $name ($email) active class code" }
    UserSessions
      .update({ (UserSessions.userRef eq dbmsId) and (UserSessions.sessionRef eq sessionDbmsId) }) { row ->
        row[updated] = DateTime.now(UTC)
        row[activeClassCode] = DISABLED_CLASS_CODE.value
        row[previousTeacherClassCode] = DISABLED_CLASS_CODE.value
      }
  }

  fun resetActiveClassCode(tx: Transaction) {
    logger.info { "Resetting $name ($email) active class code" }
    tx.hset(browserSpecificUserInfoKey, ACTIVE_CLASS_CODE_FIELD, DISABLED_CLASS_CODE.value)
    tx.hset(browserSpecificUserInfoKey, PREVIOUS_TEACHER_CLASS_CODE_FIELD, DISABLED_CLASS_CODE.value)
  }

  fun isEnrolled(classCode: ClassCode, redis: Jedis) =
    if (usePostgres)
      transaction {
        Enrollees
          .slice(Enrollees.id.count())
          .select { Enrollees.userRef eq dbmsId }
          .map { it[Enrollees.id.count()].toInt() }
          .first().also { logger.info { "isEnrolled() returned $it for $classCode" } } > 0
      }
    else
      redis.sismember(classCode.classCodeEnrollmentKey, userId) ?: false

  fun isNotEnrolled(classCode: ClassCode, redis: Jedis) = !isEnrolled(classCode, redis)

  fun enrollInClass(classCode: ClassCode, redis: Jedis) {
    when {
      classCode.isNotEnabled -> throw DataException("Not reportable class code")
      classCode.isNotValid(redis) -> throw DataException("Invalid class code: $classCode")
      isEnrolled(classCode, redis) -> throw DataException("Already enrolled in class $classCode")
      else -> {
        val previousClassCode = enrolledClassCode

        if (usePostgres)
          transaction {
            if (previousClassCode.isEnabled)
              previousClassCode.removeEnrollee(this@User)
            assignEnrolledClassCode(classCode)
            classCode.addEnrollee(this@User)
          }
        else
          redis.multi()
            .also { tx ->
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

    if (usePostgres)
      transaction {
        assignEnrolledClassCode(DISABLED_CLASS_CODE)
        if (enrolled)
          classCode.removeEnrollee(this@User)
      }
    else
      redis.multi()
        .also { tx ->
          assignEnrolledClassCode(DISABLED_CLASS_CODE, tx)
          if (enrolled)
            classCode.removeEnrollee(this, tx)
          tx.exec()
        }
  }

  fun deleteClassCode(classCode: ClassCode, enrollees: List<User>) {
    logger.info { "Deleting ${enrollees.size} enrollees for class code $classCode for $name ($email)" }
    // Reset every enrollee's enrolled class and remove from class
    enrollees
      .forEach { enrollee ->
        logger.info { "Assigning ${enrollee.email} to $DISABLED_CLASS_CODE" }
        Users
          .update({ Users.id eq enrollee.dbmsId }) { row ->
            row[updated] = DateTime.now(UTC)
            row[enrolledClassCode] = DISABLED_CLASS_CODE.value
          }
      }

    // Delete class
    Classes.deleteWhere { Classes.classCode eq classCode.value }
  }

  fun deleteClassCode(classCode: ClassCode, enrollees: List<User>, tx: Transaction) {
    logger.info { "Deleting ${enrollees.size} enrollees for class code $classCode for $name ($email)" }
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

  fun isUniqueClassDesc(classDesc: String, redis: Jedis) =
    if (usePostgres)
      transaction {
        Classes
          .slice(Classes.id.count())
          .select { Classes.description eq classDesc }
          .map { it[Classes.id.count()].toInt() }
          .first() == 0
      }
    else
      redis.smembers(userClassesKey)
        .asSequence()
        .map { ClassCode(it) }
        .filter { classCode -> classDesc == classCode.fetchClassDesc(redis) }
        .none()

  fun userPasswordResetId(redis: Jedis): String? =
    if (usePostgres)
      transaction {
        PasswordResets
          .slice(PasswordResets.resetId)
          .select { PasswordResets.userRef eq dbmsId }
          .map { it[PasswordResets.resetId] }.also { logger.info { "userPasswordResetId() returned $it" } }
          .first()
      }
    else
      redis.get(userPasswordResetKey)

  fun deleteUserPasswordResetId() {
    PasswordResets.deleteWhere { PasswordResets.userRef eq dbmsId }
  }

  fun deleteUserPasswordResetId(tx: Transaction) {
    tx.del(userPasswordResetKey)
  }

  fun savePasswordResetId(email: Email, previousResetId: ResetId, newResetId: ResetId, redis: Jedis) {
    if (usePostgres)
      transaction {
        PasswordResets
          .insert { row ->
            row[updated] = DateTime.now(UTC)
            row[userRef] = dbmsId
            row[resetId] = newResetId.value
            row[PasswordResets.email] = email.value
          }
      }
    else
      redis.multi()
        .also { tx ->
          if (previousResetId.isNotBlank())
            tx.del(previousResetId.passwordResetKey)

          val expiration = SetParams().ex(15.minutes.inSeconds.toInt())
          tx.set(userPasswordResetKey, newResetId.value, expiration)
          tx.set(newResetId.passwordResetKey, email.value, expiration)

          tx.exec()
        }
  }

  fun deleteUser(redis: Jedis) {
    val classCodes = classCodes(redis)
    val browserSessions = browserSessions(redis)
    val correctAnswers = correctAnswers(redis)
    val likeDislikes = likeDislikes(redis)
    val challenges = challenges(redis)
    val invocations = invocations(redis)

    val previousResetId = userPasswordResetId(redis)?.let { ResetId(it) } ?: EMPTY_RESET_ID
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

    val classCode = enrolledClassCode
    val enrolled = classCode.isEnabled && classCode.isValid(redis) && isEnrolled(classCode, redis)

    // TODO
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
    val classCode = enrolledClassCode
    val shouldPublish = shouldPublish(classCode, redis)

    logger.debug { "Resetting $languageName $groupName $challengeName" }

    funcInfo.invocations
      .map { ChallengeResults(invocation = it) }
      .forEach { result ->
        // Reset the history of each answer on a per-invocation basis
        logger.debug { "Resetting invocation: ${result.invocation}" }
        val history = ChallengeHistory(result.invocation).apply { markUnanswered() }

        if (usePostgres)
          transaction {
            val md5 = md5Of(languageName, groupName, challengeName, result.invocation)
            UserAnswerHistory
              .update({ (UserAnswerHistory.userRef eq dbmsId) and (UserAnswerHistory.md5 eq md5) }) { row ->
                row[updated] = DateTime.now(UTC)
                row[correct] = false
                row[incorrectAttempts] = 0
                row[historyJson] = gson.toJson(emptyList<String>())
              }
          }
        else {
          val answerHistoryKey = answerHistoryKey(languageName, groupName, challengeName, result.invocation)
          redis.set(answerHistoryKey, gson.toJson(history))
        }

        if (shouldPublish)
          publishAnswers(classCode, funcInfo.challengeMd5, maxHistoryLength, false, 0, history, redis)
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

    fun fetchActiveClassCode(user: User?, redis: Jedis?) =
      when {
        user.isNull() || redis.isNull() -> DISABLED_CLASS_CODE
        usePostgres ->
          if (user.isNotNull())
            transaction {
              UserSessions
                .slice(UserSessions.activeClassCode)
                .select { UserSessions.userRef eq user.dbmsId }
                .map { it[UserSessions.activeClassCode] }
                .firstOrNull()?.let { ClassCode(it) } ?: DISABLED_CLASS_CODE
            }
          else
            DISABLED_CLASS_CODE
        else -> redis.hget(user.browserSpecificUserInfoKey, ACTIVE_CLASS_CODE_FIELD)?.let { ClassCode(it) }
          ?: DISABLED_CLASS_CODE
      }

    fun fetchPreviousTeacherClassCode(user: User?, redis: Jedis?) =
      when {
        user.isNull() || redis.isNull() -> DISABLED_CLASS_CODE
        usePostgres ->
          if (user.isNotNull())
            transaction {
              UserSessions
                .slice(UserSessions.previousTeacherClassCode)
                .select { UserSessions.userRef eq user.dbmsId }
                .map { it[UserSessions.previousTeacherClassCode] }
                .firstOrNull()?.let { ClassCode(it) } ?: DISABLED_CLASS_CODE
            }
          else
            DISABLED_CLASS_CODE
        else ->
          redis.hget(user.browserSpecificUserInfoKey, PREVIOUS_TEACHER_CLASS_CODE_FIELD)?.let { ClassCode(it) }
            ?: DISABLED_CLASS_CODE
      }

    fun fetchPreviousResponses(user: User?,
                               browserSession: BrowserSession?,
                               challenge: Challenge,
                               redis: Jedis?): Map<String, String> =
      if (redis.isNull())
        kotlinx.html.emptyMap
      else {
        val languageName = challenge.languageType.languageName
        val groupName = challenge.groupName
        val challengeName = challenge.challengeName
        val challengeAnswersKey = challengeAnswersKey(user, browserSession, languageName, groupName, challengeName)

        when {
          challengeAnswersKey.isEmpty() -> kotlinx.html.emptyMap as Map<String, String>
          usePostgres -> {
            val elems = challengeAnswersKey.split(KEY_SEP)
            val md5 = elems[2]
            when {
              user.isNotNull() -> {
                UserChallengeInfo
                  .slice(UserChallengeInfo.answersJson)
                  .select { (UserChallengeInfo.userRef eq user.dbmsId) and (UserChallengeInfo.md5 eq md5) }
                  .map { it[UserChallengeInfo.answersJson] }
                  .firstOrNull()
                  ?.let { gson.fromJson(it, Map::class.java) as Map<String, String> }
                  ?: throw InvalidConfigurationException("UserChallengeInfo not found: ${user.dbmsId} $md5")
              }
              browserSession.isNotNull() -> {
                val sessionDbmsId = browserSession.sessionDbmsId
                SessionChallengeInfo
                  .slice(SessionChallengeInfo.answersJson)
                  .select { (SessionChallengeInfo.sessionRef eq sessionDbmsId) and (SessionChallengeInfo.md5 eq md5) }
                  .map { it[SessionChallengeInfo.answersJson] }
                  .firstOrNull()
                  ?.let { gson.fromJson(it, Map::class.java) as Map<String, String> }
                  ?: throw InvalidConfigurationException("SessionChallengeInfo not found: $sessionDbmsId $md5")
              }
              else -> kotlinx.html.emptyMap
            }
          }
          else -> redis.hgetAll(challengeAnswersKey)
        }
      }

    // TODO
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
      val digest = password.sha256(salt)
      logger.info { "Created user $email ${user.userId}" }

      if (usePostgres)
        transaction {
          val userId =
            Users
              .insertAndGetId { row ->
                row[Users.name] = name.value
                row[Users.email] = email.value
                row[enrolledClassCode] = DISABLED_CLASS_CODE.value
                row[Users.salt] = salt
                row[Users.digest] = digest
              }.value

          val browserId =
            BrowserSessions
              .insertAndGetId { row ->
                row[session_id] = browserSession?.id ?: throw InvalidConfigurationException("Missing browser session")
              }.value

          UserSessions
            .insert { row ->
              row[sessionRef] = browserId
              row[userRef] = userId
              row[activeClassCode] = DISABLED_CLASS_CODE.value
              row[previousTeacherClassCode] = DISABLED_CLASS_CODE.value
            }

        }
      else
        redis.multi()
          .also { tx ->
            tx.set(email.userEmailKey, user.userId)
            tx.hset(user.userInfoKey, mapOf(NAME_FIELD to name.value,
                                            EMAIL_FIELD to email.value,
                                            SALT_FIELD to salt,
                                            DIGEST_FIELD to digest,
                                            ENROLLED_CLASS_CODE_FIELD to DISABLED_CLASS_CODE.value))

            tx.hset(user.userInfoBrowserKey, mapOf(ACTIVE_CLASS_CODE_FIELD to DISABLED_CLASS_CODE.value,
                                                   PREVIOUS_TEACHER_CLASS_CODE_FIELD to DISABLED_CLASS_CODE.value))

            tx.exec()
          }

      return user
    }

    fun saveChallengeAnswers(user: User?,
                             browserSession: BrowserSession?,
                             content: ReadingBatContent,
                             names: ChallengeNames,
                             paramMap: Map<String, String>,
                             funcInfo: FunctionInfo,
                             userResponses: List<Map.Entry<String, List<String>>>,
                             results: List<ChallengeResults>,
                             redis: Jedis) {
      val correctAnswersKey = correctAnswersKey(user, browserSession, names)
      val challengeAnswersKey = challengeAnswersKey(user, browserSession, names)
      val md5 = md5Of(names.languageName, names.groupName, names.challengeName)

      val complete = results.all { it.correct }
      val numCorrect = results.count { it.correct }

      // Record if all answers were correct
      if (usePostgres) {
        if (user.isNotNull())
          UserChallengeInfo
            .update({ (UserChallengeInfo.userRef eq user.dbmsId) and (UserChallengeInfo.md5 eq md5) }) {
              it[updated] = DateTime.now(UTC)
              it[allCorrect] = complete
            }
        else if (browserSession.isNotNull())
          SessionChallengeInfo
            .update({ (SessionChallengeInfo.sessionRef eq browserSession.sessionDbmsId) and (SessionChallengeInfo.md5 eq md5) }) {
              it[updated] = DateTime.now(UTC)
              it[allCorrect] = complete
            }
      }
      else {
        if (correctAnswersKey.isNotEmpty())
          redis.set(correctAnswersKey, complete.toString())
      }

      // Save the last answers given
      val invokeList =
        userResponses.indices
          .map { i ->
            val userResponse =
              paramMap[RESP + i]?.trim() ?: throw InvalidConfigurationException("Missing user response")
            funcInfo.invocations[i] to userResponse
          }
      if (usePostgres) {
        val invokeMap = invokeList.map { it.first.value to it.second }.toMap()
        if (user.isNotNull()) {
          UserChallengeInfo
            .update({ (UserChallengeInfo.userRef eq user.dbmsId) and (UserChallengeInfo.md5 eq md5) }) {
              it[updated] = DateTime.now(UTC)
              it[answersJson] = gson.toJson(invokeMap)
            }
        }
        else if (browserSession.isNotNull())
          SessionChallengeInfo
            .update({ (SessionChallengeInfo.sessionRef eq browserSession.sessionDbmsId) and (SessionChallengeInfo.md5 eq md5) }) {
              it[updated] = DateTime.now(UTC)
              it[answersJson] = gson.toJson(invokeMap)
            }
      }
      else {
        if (challengeAnswersKey.isNotEmpty()) {
          invokeList
            .toMap()
            .forEach { (invocation, userResponse) ->
              redis.hset(challengeAnswersKey, invocation.value, userResponse)
            }
        }
      }

      val classCode = user?.enrolledClassCode ?: DISABLED_CLASS_CODE
      val shouldPublish = user.shouldPublish(classCode, redis)

      // Save the history of each answer on a per-invocation basis
      for (result in results) {
        val answerHistoryKey = answerHistoryKey(user, browserSession, names, result.invocation)

        val history =
          if (usePostgres) {
            when {
              user.isNotNull() -> user.answerHistory(md5, result.invocation)
              browserSession.isNotNull() -> browserSession.answerHistory(md5, result.invocation)
              else -> ChallengeHistory(result.invocation)
            }
          }
          else
            gson.fromJson(redis[answerHistoryKey], ChallengeHistory::class.java) ?: ChallengeHistory(result.invocation)

        when {
          !result.answered -> history.markUnanswered()
          result.correct -> history.markCorrect(result.userResponse)
          else -> history.markIncorrect(result.userResponse)
        }

        // TODO
        if (usePostgres) {

        }
        else {
          val json = gson.toJson(history)
          logger.debug { "Saving: $json to $answerHistoryKey" }
          redis.set(answerHistoryKey, json)
        }

        if (shouldPublish) {
          val maxLength = content.maxHistoryLength
          user?.publishAnswers(classCode, funcInfo.challengeMd5, maxLength, complete, numCorrect, history, redis)
        }
      }
    }

    private fun User?.shouldPublish(classCode: ClassCode, redis: Jedis) =
      when {
        isNull() -> false
        classCode.isEnabled -> {
          // Check to see if the teacher that owns class has it set as their active class in one of the sessions
          val teacherId = classCode.fetchClassTeacherId(redis)
          teacherId.isNotEmpty() && teacherId.toUser(redis, null).interestedInActiveClassCode(classCode, redis)
            .also { logger.debug { "Publishing teacherId: $teacherId for $classCode" } }
        }
        else -> false
      }

    fun saveLikeDislike(user: User?,
                        browserSession: BrowserSession?,
                        names: ChallengeNames,
                        likeVal: Int,
                        redis: Jedis) {
      if (usePostgres)
        when {
          user.isNotNull() ->
            transaction {
              UserChallengeInfo
                .update({ UserChallengeInfo.userRef eq user.dbmsId }) { row ->
                  row[updated] = DateTime.now(UTC)
                  row[likeDislike] = likeVal.toShort()
                }
            }
          browserSession.isNotNull() ->
            transaction {
              SessionChallengeInfo
                .update({ SessionChallengeInfo.sessionRef eq (browserSession.sessionDbmsId) }) { row ->
                  row[updated] = DateTime.now(UTC)
                  row[likeDislike] = likeVal.toShort()
                }
            }
          else -> {
            // Do nothing
          }
        }
      else
        likeDislikeKey(user, browserSession, names)
          .let {
            if (it.isNotEmpty())
              redis.apply { if (likeVal == 0) del(it) else set(it, likeVal.toString()) }
          }
    }

    private fun isRegisteredEmail(email: Email, redis: Jedis) = lookupUserByEmail(email, redis).isNotNull()

    fun isNotRegisteredEmail(email: Email, redis: Jedis) = !isRegisteredEmail(email, redis)

    fun lookupUserByEmail(email: Email, redis: Jedis): User? =
      if (usePostgres)
        transaction {
          Users
            .slice(Users.userId)
            .select { Users.email eq email.value }
            .map { it[Users.userId].toUser(redis, null) }
            .firstOrNull().also { logger.info { "lookupUserByEmail() returned ${it?.name ?: "email not found"}" } }
        }
      else {
        val id = redis.get(email.userEmailKey) ?: ""
        if (id.isNotEmpty()) id.toUser(redis, null) else null
      }
  }

  internal data class ChallengeAnswers(val id: String, val correctAnswers: MutableMap<String, String> = mutableMapOf())
}

internal val User.userDbmsId: Long get() = this.userId.userDbmsId

internal val String.userDbmsId: Long
  get() =
    Users
      .slice(Users.id)
      .select { Users.userId eq this@userDbmsId }
      .map { it[Users.id].value }
      .firstOrNull() ?: throw InvalidConfigurationException("Invalid user id: ${this@userDbmsId}")

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
  return if (isNull()) false else isInDbms(redis)
}