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

import com.github.pambrose.common.util.isNotNull
import com.github.pambrose.common.util.isNull
import com.github.pambrose.common.util.newStringSalt
import com.github.pambrose.common.util.randomId
import com.github.readingbat.common.BrowserSession.Companion.createBrowserSession
import com.github.readingbat.common.ClassCode.Companion.DISABLED_CLASS_CODE
import com.github.readingbat.common.CommonUtils.keyOf
import com.github.readingbat.common.CommonUtils.md5Of
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
import com.github.readingbat.dsl.Challenge
import com.github.readingbat.dsl.DataException
import com.github.readingbat.dsl.InvalidConfigurationException
import com.github.readingbat.dsl.LanguageType.Companion.defaultLanguageType
import com.github.readingbat.dsl.LanguageType.Companion.toLanguageType
import com.github.readingbat.dsl.MissingBrowserSessionException
import com.github.readingbat.dsl.isPostgresEnabled
import com.github.readingbat.posts.ChallengeHistory
import com.github.readingbat.posts.ChallengeNames
import com.github.readingbat.posts.ChallengeResults
import com.github.readingbat.posts.DashboardInfo
import com.github.readingbat.server.*
import com.github.readingbat.server.ChallengeName.Companion.ANY_CHALLENGE
import com.github.readingbat.server.Email.Companion.EMPTY_EMAIL
import com.github.readingbat.server.Email.Companion.UNKNOWN_EMAIL
import com.github.readingbat.server.FullName.Companion.EMPTY_FULLNAME
import com.github.readingbat.server.FullName.Companion.UNKNOWN_FULLNAME
import com.github.readingbat.server.GroupName.Companion.ANY_GROUP
import com.github.readingbat.server.Invocation.Companion.ANY_INVOCATION
import com.github.readingbat.server.LanguageName.Companion.ANY_LANGUAGE
import com.github.readingbat.server.ReadingBatServer.adminUsers
import com.github.readingbat.server.ResetId.Companion.EMPTY_RESET_ID
import com.github.readingbat.server.WsEndoints.classTopicName
import com.github.readingbat.utils.upsert
import com.google.gson.Gson
import mu.KLogging
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.joda.time.DateTimeZone.UTC
import redis.clients.jedis.Jedis
import kotlin.contracts.contract
import kotlin.time.measureTime

internal class User private constructor(val userId: String,
                                        val browserSession: BrowserSession?,
                                        initFields: Boolean) {
  var userDbmsId: Long = -1
  var email: Email = EMPTY_EMAIL
  var fullName: FullName = EMPTY_FULLNAME
  var enrolledClassCode: ClassCode = DISABLED_CLASS_CODE
  var defaultLanguage = defaultLanguageType
  private var saltBacking: String = ""
  private var digestBacking: String = ""

  private val userInfoKey = keyOf(USER_INFO_KEY, userId)

  val salt: String
    get() = if (saltBacking.isBlank()) throw DataException("Missing salt field") else saltBacking
  val digest: String
    get() = if (digestBacking.isBlank()) throw DataException("Missing digest field") else digestBacking

  init {
    if (initFields && isPostgresEnabled()) {
      measureTime {
        val row =
          transaction {
            Users
              .select { Users.userId eq this@User.userId }
              .firstOrNull()
          }
        userDbmsId = row?.get(Users.id)?.value ?: -1
        email = row?.get(Users.email)?.let { Email(it) } ?: UNKNOWN_EMAIL
        fullName = row?.get(Users.name)?.let { FullName(it) } ?: UNKNOWN_FULLNAME
        enrolledClassCode = row?.get(Users.enrolledClassCode)?.let { ClassCode(it) } ?: DISABLED_CLASS_CODE
        defaultLanguage = row?.get(Users.defaultLanguage)?.toLanguageType() ?: defaultLanguageType
        saltBacking = row?.get(Users.salt) ?: ""
        digestBacking = row?.get(Users.digest) ?: ""
      }.also { logger.info { "Selected user info in $it" } }
    }
  }

  private val userInfoBrowserKey by lazy { keyOf(USER_INFO_BROWSER_KEY, userId, browserSession?.id ?: UNASSIGNED) }
  val userInfoBrowserQueryKey by lazy { keyOf(USER_INFO_BROWSER_KEY, userId, "*") }

  private val browserSpecificUserInfoKey by lazy {
    if (browserSession.isNotNull()) userInfoBrowserKey else throw InvalidConfigurationException("Null browser session for $this")
  }
  val userClassesKey by lazy { keyOf(USER_CLASSES_KEY, userId) }

  // This key maps to a reset_id
  private val userPasswordResetKey by lazy { keyOf(USER_RESET_KEY, userId) }

  private fun sessionDbmsId() =
    try {
      browserSession?.sessionDbmsId() ?: browserSession.createBrowserSession()
    } catch (e: MissingBrowserSessionException) {
      logger.info { "Creating BrowserSession for ${e.message}" }
      browserSession.createBrowserSession()
    }

  fun browserSessions() =
    transaction {
      (BrowserSessions innerJoin UserSessions)
        .slice(BrowserSessions.session_id)
        .select { UserSessions.userRef eq userDbmsId }
        .map { it[0] as String }
    }

  // Look across all possible browser sessions
  private fun interestedInActiveClassCode(classCode: ClassCode) =
    transaction {
      UserSessions
        .slice(Count(UserSessions.id))
        .select { (UserSessions.userRef eq userDbmsId) and (UserSessions.activeClassCode eq classCode.value) }
        .map { it[0] as Long }
        .first() > 0
    }

  fun correctAnswers() =
    transaction {
      UserChallengeInfo
        .slice(UserChallengeInfo.allCorrect)
        .select { (UserChallengeInfo.userRef eq userDbmsId) and UserChallengeInfo.allCorrect }
        .map { (it[0] as Boolean).toString() }
    }

  fun likeDislikes() =
    transaction {
      UserChallengeInfo.slice(UserChallengeInfo.likeDislike)
        .select { (UserChallengeInfo.userRef eq userDbmsId) and ((UserChallengeInfo.likeDislike eq 1) or (UserChallengeInfo.likeDislike eq 2)) }
        .map { it.toString() }
    }

  fun classCount() =
    transaction {
      Classes
        .slice(Count(Classes.classCode))
        .select { Classes.userRef eq userDbmsId }
        .map { it[0] as Long }
        .first().also { logger.info { "classCount() returned $it" } }
        .toInt()
    }

  fun addClassCode(classCode: ClassCode, classDesc: String) =
    transaction {
      Classes
        .insert { row ->
          row[userRef] = userDbmsId
          row[Classes.classCode] = classCode.value
          row[description] = classDesc
        }
    }

  fun classCodes() =
    transaction {
      Classes
        .slice(Classes.classCode)
        .select { Classes.userRef eq userDbmsId }
        .map { ClassCode(it[0] as String) }
    }

  fun isInDbms() =
    transaction {
      Users.slice(Count(Users.id))
        .select { Users.id eq userDbmsId }
        .map { it[0] as Long }
        .first() > 0
    }

  fun assignDigest(newDigest: String) =
    transaction {
      PasswordResets.deleteWhere { PasswordResets.userRef eq userDbmsId }

      Users
        .update({ Users.id eq userDbmsId }) { row ->
          row[updated] = DateTime.now(UTC)
          row[digest] = newDigest
          digestBacking = newDigest
        }
    }

  private fun assignEnrolledClassCode(classCode: ClassCode) =
    Users
      .update({ Users.id eq userDbmsId }) { row ->
        row[updated] = DateTime.now(UTC)
        row[enrolledClassCode] = classCode.value
        this@User.enrolledClassCode = classCode
      }

  fun challenges() =
    transaction {
      UserChallengeInfo
        .slice(UserChallengeInfo.md5)
        .select { UserChallengeInfo.userRef eq userDbmsId }
        .map { it[0] as String }.also { logger.info { "challenges() return ${it.size}" } }
    }

  fun invocations() =
    transaction {
      UserAnswerHistory
        .slice(UserAnswerHistory.md5)
        .select { UserAnswerHistory.userRef eq userDbmsId }
        .map { it[0] as String }.also { logger.info { "invocations() return ${it.size}" } }
    }

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

  fun historyExists(md5: String, invocation: Invocation) =
    UserAnswerHistory
      .slice(Count(UserAnswerHistory.id))
      .select { (UserAnswerHistory.userRef eq userDbmsId) and (UserAnswerHistory.md5 eq md5) and (UserAnswerHistory.invocation eq invocation.value) }
      .map { it[0] as Long }
      .first() > 0

  fun answerHistory(md5: String, invocation: Invocation) =
    UserAnswerHistory
      .slice(UserAnswerHistory.invocation,
             UserAnswerHistory.correct,
             UserAnswerHistory.incorrectAttempts,
             UserAnswerHistory.historyJson)
      .select { (UserAnswerHistory.userRef eq userDbmsId) and (UserAnswerHistory.md5 eq md5) and (UserAnswerHistory.invocation eq invocation.value) }
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

  fun assignActiveClassCode(classCode: ClassCode, resetPreviousClassCode: Boolean) =
    transaction {
      UserSessions
        .upsert(conflictIndex = userSessionIndex) { row ->
          row[sessionRef] = sessionDbmsId()
          row[userRef] = userDbmsId
          row[updated] = DateTime.now(UTC)
          row[activeClassCode] = classCode.value
          if (resetPreviousClassCode)
            row[previousTeacherClassCode] = classCode.value
        }
    }

  fun resetActiveClassCode() {
    logger.info { "Resetting $fullName ($email) active class code" }
    UserSessions
      .upsert(conflictIndex = userSessionIndex) { row ->
        row[sessionRef] = sessionDbmsId()
        row[userRef] = userDbmsId
        row[updated] = DateTime.now(UTC)
        row[activeClassCode] = DISABLED_CLASS_CODE.value
        row[previousTeacherClassCode] = DISABLED_CLASS_CODE.value
      }
  }

  fun isEnrolled(classCode: ClassCode) =
    transaction {
      Enrollees
        .slice(Count(Enrollees.id))
        .select { Enrollees.userRef eq userDbmsId }
        .map { it[0] as Long }
        .first().also { logger.info { "isEnrolled() returned $it for $classCode" } } > 0
    }

  fun isNotEnrolled(classCode: ClassCode) = !isEnrolled(classCode)

  fun enrollInClass(classCode: ClassCode) {
    when {
      classCode.isNotEnabled -> throw DataException("Not reportable class code")
      classCode.isNotValid() -> throw DataException("Invalid class code: $classCode")
      isEnrolled(classCode) -> throw DataException("Already enrolled in class $classCode")
      else ->
        transaction {
          val previousClassCode = enrolledClassCode
          if (previousClassCode.isEnabled)
            previousClassCode.removeEnrollee(this@User)
          assignEnrolledClassCode(classCode)
          classCode.addEnrollee(this@User)
        }
    }
  }

  fun withdrawFromClass(classCode: ClassCode) {
    if (classCode.isNotEnabled)
      throw DataException("Not enrolled in a class")

    // This should always be true
    val enrolled = classCode.isValid() && isEnrolled(classCode)

    transaction {
      assignEnrolledClassCode(DISABLED_CLASS_CODE)
      if (enrolled)
        classCode.removeEnrollee(this@User)
    }
  }

  fun unenrollEnrolleesClassCode(classCode: ClassCode, enrollees: List<User>) {
    logger.info { "Deleting ${enrollees.size} enrollees for class code $classCode for $fullName ($email)" }
    // Reset every enrollee's enrolled class and remove from class
    enrollees
      .forEach { enrollee ->
        logger.info { "Assigning ${enrollee.email} to $DISABLED_CLASS_CODE" }
        Users
          .update({ Users.id eq enrollee.userDbmsId }) { row ->
            row[updated] = DateTime.now(UTC)
            row[enrolledClassCode] = DISABLED_CLASS_CODE.value
          }
      }
  }

  fun isUniqueClassDesc(classDesc: String) =
    transaction {
      Classes
        .slice(Count(Classes.id))
        .select { Classes.description eq classDesc }
        .map { it[0] as Long }
        .first() == 0L
    }

  fun userPasswordResetId() =
    transaction {
      PasswordResets
        .slice(PasswordResets.resetId)
        .select { PasswordResets.userRef eq userDbmsId }
        .map { it[0] as String }.also { logger.info { "userPasswordResetId() returned $it" } }
        .map { ResetId(it) }
        .firstOrNull() ?: EMPTY_RESET_ID
    }

  fun savePasswordResetId(email: Email, previousResetId: ResetId, newResetId: ResetId) {
    transaction {
      PasswordResets
        .upsert(conflictIndex = passwordResetsIndex) { row ->
          row[userRef] = userDbmsId
          row[updated] = DateTime.now(UTC)
          row[resetId] = newResetId.value
          row[PasswordResets.email] = email.value
        }
    }
  }

  fun deleteUser() {
    val classCodes = classCodes()
    val browserSessions = browserSessions()
    val correctAnswers = correctAnswers()
    val likeDislikes = likeDislikes()
    val challenges = challenges()
    val invocations = invocations()

    val enrolleePairs = classCodes.map { it to it.fetchEnrollees() }

    logger.info { "Deleting User: $userId $fullName" }
    logger.info { "User Email: $email" }
    logger.info { "User Info Key: $userInfoKey" }
    logger.info { "User Info browser sessions: ${browserSessions.size}" }
    logger.info { "Correct Answers: ${correctAnswers.size}" }
    logger.info { "Likes/Dislikes: ${likeDislikes.size}" }
    logger.info { "Challenges: ${challenges.size}" }
    logger.info { "Invocations: ${invocations.size}" }
    logger.info { "User Classes: $classCodes" }

    transaction {
      // Reset enrollees in all classes created by User
      enrolleePairs.forEach { (classCode, enrollees) -> unenrollEnrolleesClassCode(classCode, enrollees) }

      // Classes delete on cascade
      // UserAnswerHistory delete on cascade
      // UserChallengeInfo delete on cascade
      // UserSessions delete on cascade
      // PasswordResets delete on cascade
      Users.deleteWhere { Users.id eq userDbmsId }
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
                   challenge: Challenge,
                   maxHistoryLength: Int,
                   redis: Jedis) {
    val classCode = enrolledClassCode
    val shouldPublish = shouldPublish(classCode)

    logger.debug { "Resetting challenge: $challenge" }

    funcInfo.invocations
      .map { ChallengeResults(invocation = it) }
      .forEach { result ->
        // Reset the history of each answer on a per-invocation basis
        logger.debug { "Resetting invocation: ${result.invocation}" }
        val history = ChallengeHistory(result.invocation).apply { markUnanswered() }

        transaction {
          UserAnswerHistory
            .upsert(conflictIndex = userAnswerHistoryIndex) { row ->
              row[userRef] = userDbmsId
              row[md5] = challenge.md5(result.invocation)
              row[updated] = DateTime.now(UTC)
              row[invocation] = history.invocation.value
              row[correct] = false
              row[incorrectAttempts] = 0
              row[historyJson] = gson.toJson(emptyList<String>())
            }
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
    internal const val DEFAULT_LANGUAGE_FIELD = "default-language"
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

    fun String.toUser(browserSession: BrowserSession?) = User(this, browserSession, true)

    fun fetchActiveClassCode(user: User?) =
      when {
        user.isNull() || !isPostgresEnabled() -> DISABLED_CLASS_CODE
        else ->
          transaction {
            UserSessions
              .slice(UserSessions.activeClassCode)
              .select { (UserSessions.sessionRef eq user.sessionDbmsId()) and (UserSessions.userRef eq user.userDbmsId) }
              .map { it[0] as String }
              .firstOrNull()?.let { ClassCode(it) } ?: DISABLED_CLASS_CODE
          }
      }

    fun fetchPreviousTeacherClassCode(user: User?) =
      when {
        user.isNull() || !isPostgresEnabled() -> DISABLED_CLASS_CODE
        else ->
          transaction {
            UserSessions
              .slice(UserSessions.previousTeacherClassCode)
              .select { (UserSessions.sessionRef eq user.sessionDbmsId()) and (UserSessions.userRef eq user.userDbmsId) }
              .map { it[0] as String }
              .firstOrNull()?.let { ClassCode(it) } ?: DISABLED_CLASS_CODE
          }
      }

    fun createUser(name: FullName,
                   email: Email,
                   password: Password,
                   browserSession: BrowserSession?): User {

      val user = User(randomId(25), browserSession, false)
      val salt = newStringSalt()
      val digest = password.sha256(salt)

      transaction {
        val userId =
          Users
            .insertAndGetId { row ->
              row[userId] = user.userId
              row[Users.name] = name.value
              row[Users.email] = email.value
              row[enrolledClassCode] = DISABLED_CLASS_CODE.value
              row[defaultLanguage] = defaultLanguageType.languageName.value
              row[Users.salt] = salt
              row[Users.digest] = digest
            }.value

        val browserId = browserSession?.sessionDbmsId() ?: browserSession.createBrowserSession()

        UserSessions
          .insert { row ->
            row[sessionRef] = browserId
            row[userRef] = userId
            row[activeClassCode] = DISABLED_CLASS_CODE.value
            row[previousTeacherClassCode] = DISABLED_CLASS_CODE.value
          }
      }

      logger.info { "Created user $email ${user.userId}" }

      return user
    }

    fun User?.shouldPublish(classCode: ClassCode) =
      when {
        isNull() || !isPostgresEnabled() -> false
        classCode.isEnabled -> {
          // Check to see if the teacher that owns class has it set as their active class in one of the sessions
          val teacherId = classCode.fetchClassTeacherId()
          teacherId.isNotEmpty() && teacherId.toUser(null).interestedInActiveClassCode(classCode)
            .also { logger.debug { "Publishing teacherId: $teacherId for $classCode" } }
        }
        else -> false
      }

    private fun isRegisteredEmail(email: Email) = lookupUserByEmail(email).isNotNull()

    fun isNotRegisteredEmail(email: Email) = !isRegisteredEmail(email)

    fun lookupUserByEmail(email: Email): User? =
      transaction {
        Users
          .slice(Users.userId)
          .select { Users.email eq email.value }
          .map { (it[0] as String).toUser(null) }
          .firstOrNull().also { logger.info { "lookupUserByEmail() returned ${it?.fullName ?: "email not found"}" } }
      }
  }

  internal data class ChallengeAnswers(val id: String, val correctAnswers: MutableMap<String, String> = mutableMapOf())
}

internal fun userDbmsIdByUserId(userId: String) =
  Users
    .slice(Users.id)
    .select { Users.userId eq userId }
    .map { it[Users.id].value }
    .firstOrNull() ?: throw InvalidConfigurationException("Invalid user id: $userId")

internal fun User?.isAdminUser() = isValidUser() && email.value in adminUsers

internal fun User?.isNotAdminUser() = !isAdminUser()

internal fun User?.isNotValidUser(): Boolean {
  contract {
    returns(false) implies (this@isNotValidUser is User)
  }
  return !isValidUser()
}

internal fun User?.isValidUser(): Boolean {
  contract {
    returns(true) implies (this@isValidUser is User)
  }
  return if (isNull()) false else isInDbms()
}