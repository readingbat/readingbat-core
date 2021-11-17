/*
 * Copyright © 2021 Paul Ambrose (pambrose@mac.com)
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
import com.github.pambrose.common.util.maxLength
import com.github.pambrose.common.util.md5Of
import com.github.pambrose.common.util.newStringSalt
import com.github.pambrose.common.util.randomId
import com.github.readingbat.common.ClassCode.Companion.DISABLED_CLASS_CODE
import com.github.readingbat.common.Constants.UNKNOWN
import com.github.readingbat.common.Endpoints.THUMBS_DOWN
import com.github.readingbat.common.Endpoints.THUMBS_UP
import com.github.readingbat.common.KeyConstants.AUTH_KEY
import com.github.readingbat.common.KeyConstants.CHALLENGE_ANSWERS_KEY
import com.github.readingbat.common.KeyConstants.CORRECT_ANSWERS_KEY
import com.github.readingbat.common.KeyConstants.keyOf
import com.github.readingbat.dsl.DataException
import com.github.readingbat.dsl.LanguageType.Companion.defaultLanguageType
import com.github.readingbat.dsl.LanguageType.Companion.toLanguageType
import com.github.readingbat.dsl.challenge.Challenge
import com.github.readingbat.dsl.isDbmsEnabled
import com.github.readingbat.dsl.isMultiServerEnabled
import com.github.readingbat.posts.ChallengeHistory
import com.github.readingbat.posts.ChallengeResults
import com.github.readingbat.posts.DashboardHistory
import com.github.readingbat.posts.DashboardInfo
import com.github.readingbat.posts.LikeDislikeInfo
import com.github.readingbat.server.*
import com.github.readingbat.server.Email.Companion.EMPTY_EMAIL
import com.github.readingbat.server.Email.Companion.UNKNOWN_EMAIL
import com.github.readingbat.server.FullName.Companion.EMPTY_FULLNAME
import com.github.readingbat.server.FullName.Companion.UNKNOWN_FULLNAME
import com.github.readingbat.server.ReadingBatServer.adminUsers
import com.github.readingbat.server.ResetId.Companion.EMPTY_RESET_ID
import com.github.readingbat.server.ws.ChallengeWs.classTargetName
import com.github.readingbat.server.ws.ChallengeWs.multiServerWsWriteChannel
import com.github.readingbat.server.ws.ChallengeWs.singleServerWsChannel
import com.github.readingbat.server.ws.PubSubCommandsWs.ChallengeAnswerData
import com.github.readingbat.server.ws.PubSubCommandsWs.PubSubTopic.LIKE_DISLIKE
import com.github.readingbat.server.ws.PubSubCommandsWs.PubSubTopic.USER_ANSWERS
import com.pambrose.common.exposed.get
import com.pambrose.common.exposed.upsert
import kotlinx.html.Entities.nbsp
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KLogging
import org.jetbrains.exposed.sql.Count
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.joda.time.DateTime
import org.joda.time.DateTimeZone.UTC
import java.util.concurrent.ConcurrentHashMap
import kotlin.contracts.contract
import kotlin.time.measureTime

class User {

  private constructor(
    userId: String,
    browserSession: BrowserSession?,
    initFields: Boolean
  ) {
    this.userId = userId
    this.browserSession = browserSession

    if (initFields && isDbmsEnabled()) {
      measureTime {
        transaction {
          UsersTable
            .select { UsersTable.userId eq this@User.userId }
            .map { assignRowVals(it) }
            .firstOrNull() ?: error("UserId not found: ${this@User.userId}")
        }
      }.also { logger.debug { "Selected user info in $it" } }
    }
  }

  private constructor(
    userId: String,
    browserSession: BrowserSession?,
    row: ResultRow
  ) {
    this.userId = userId
    this.browserSession = browserSession
    assignRowVals(row)
  }

  val userId: String
  val browserSession: BrowserSession?
  var userDbmsId: Long = -1
  var email: Email = EMPTY_EMAIL
  var fullName: FullName = EMPTY_FULLNAME
  var enrolledClassCode: ClassCode = DISABLED_CLASS_CODE
  var defaultLanguage = defaultLanguageType
  private var saltBacking: String = ""
  private var digestBacking: String = ""

  val salt: String
    get() = saltBacking.ifBlank { throw DataException("Missing salt field") }
  val digest: String
    get() = digestBacking.ifBlank { throw DataException("Missing digest field") }

  private fun sessionDbmsId() =
    browserSession?.sessionDbmsId() ?: error("Null browser session")

  private fun assignRowVals(row: ResultRow) {
    userDbmsId = row[UsersTable.id].value
    email = Email(row[UsersTable.email])
    fullName = FullName(row[UsersTable.fullName])
    enrolledClassCode = ClassCode(row[UsersTable.enrolledClassCode])
    defaultLanguage = row[UsersTable.defaultLanguage].toLanguageType() ?: defaultLanguageType
    saltBacking = row[UsersTable.salt]
    digestBacking = row[UsersTable.digest]
  }

  fun browserSessions() =
    transaction {
      val sessionId = BrowserSessionsTable.sessionId
      val userRef = UserSessionsTable.userRef
      (BrowserSessionsTable innerJoin UserSessionsTable)
        .slice(sessionId)
        .select { userRef eq userDbmsId }
        .map { it[0] as String }
    }

  // Look across all possible browser sessions
  private fun interestedInActiveClassCode(classCode: ClassCode) =
    transaction {
      val id = UserSessionsTable.id
      val userRef = UserSessionsTable.userRef
      val activeClassCode = UserSessionsTable.activeClassCode
      UserSessionsTable
        .slice(Count(id))
        .select { (userRef eq userDbmsId) and (activeClassCode eq classCode.classCode) }
        .map { it[0] as Long }
        .first() > 0
    }

  fun correctAnswers() =
    transaction {
      val allCorrect = UserChallengeInfoTable.allCorrect
      val userRef = UserChallengeInfoTable.userRef
      UserChallengeInfoTable
        .slice(allCorrect)
        .select { (userRef eq userDbmsId) and allCorrect }
        .map { (it[0] as Boolean).toString() }
    }

  fun likeDislikeEmoji(likeDislike: Int) =
    when (likeDislike) {
      1 -> THUMBS_UP
      2 -> THUMBS_DOWN
      else -> nbsp.text
    }

  fun likeDislikeEmoji(challenge: Challenge) = likeDislikeEmoji(likeDislike(challenge))

  fun likeDislike(challenge: Challenge) =
    transaction {
      val likeDislike = UserChallengeInfoTable.likeDislike
      val userRef = UserChallengeInfoTable.userRef
      val md5 = UserChallengeInfoTable.md5
      UserChallengeInfoTable
        .slice(likeDislike)
        .select { (userRef eq userDbmsId) and (md5 eq challenge.md5()) }
        .map { it[likeDislike].toInt() }
        .firstOrNull() ?: 0
    }

  fun likeDislikes() =
    transaction {
      val userRef = UserChallengeInfoTable.userRef
      val likeDislike = UserChallengeInfoTable.likeDislike
      UserChallengeInfoTable.slice(likeDislike)
        .select { (userRef eq userDbmsId) and ((likeDislike eq 1) or (likeDislike eq 2)) }
        .map { it.toString() }
    }

  fun classCount() =
    transaction {
      val userRef = ClassesTable.userRef
      ClassesTable
        .slice(Count(ClassesTable.classCode))
        .select { userRef eq userDbmsId }
        .map { it[0] as Long }
        .first().also { logger.info { "classCount() returned $it" } }
        .toInt()
    }

  fun addClassCode(classCode: ClassCode, classDesc: String) =
    transaction {
      ClassesTable
        .insert { row ->
          row[userRef] = userDbmsId
          row[ClassesTable.classCode] = classCode.classCode
          row[description] = classDesc.maxLength(256)
        }
    }

  fun classCodes() =
    transaction {
      val classCode = ClassesTable.classCode
      val userRef = ClassesTable.userRef
      ClassesTable
        .slice(classCode)
        .select { userRef eq userDbmsId }
        .map { ClassCode(it[0] as String) }
    }

  fun isInDbms() =
    transaction {
      val id = UsersTable.id
      UsersTable
        .slice(Count(id))
        .select { id eq userDbmsId }
        .map { it[0] as Long }
        .first() > 0
    }

  fun assignDigest(newDigest: String) =
    transaction {
      PasswordResetsTable.deleteWhere { PasswordResetsTable.userRef eq userDbmsId }

      UsersTable
        .update({ UsersTable.id eq userDbmsId }) { row ->
          row[updated] = DateTime.now(UTC)
          row[digest] = newDigest
          digestBacking = newDigest
        }
    }

  private fun assignEnrolledClassCode(classCode: ClassCode) =
    UsersTable
      .update({ UsersTable.id eq userDbmsId }) { row ->
        row[updated] = DateTime.now(UTC)
        row[enrolledClassCode] = classCode.classCode
        this@User.enrolledClassCode = classCode
      }

  fun challenges() =
    transaction {
      UserChallengeInfoTable
        .slice(UserChallengeInfoTable.md5)
        .select { UserChallengeInfoTable.userRef eq userDbmsId }
        .map { it[0] as String }.also { logger.info { "challenges() return ${it.size}" } }
    }

  private val uahId = UserAnswerHistoryTable.id
  private val uahInvocation = UserAnswerHistoryTable.invocation
  private val uahUserRef = UserAnswerHistoryTable.userRef
  private val uahMd5 = UserAnswerHistoryTable.md5

  fun invocations() =
    transaction {
      UserAnswerHistoryTable
        .slice(uahMd5)
        .select { uahUserRef eq userDbmsId }
        .map { it[0] as String }.also { logger.info { "invocations() return ${it.size}" } }
    }

  fun historyExists(md5: String, invocation: Invocation) =
    UserAnswerHistoryTable
      .slice(Count(uahId))
      .select { (uahUserRef eq userDbmsId) and (uahMd5 eq md5) and (uahInvocation eq invocation.value) }
      .map { it[0] as Long }
      .first() > 0

  fun answerHistory(md5: String, invocation: Invocation): ChallengeHistory {
    val correct = UserAnswerHistoryTable.correct
    val incorrectAttempts = UserAnswerHistoryTable.incorrectAttempts
    val historyJson = UserAnswerHistoryTable.historyJson

    return UserAnswerHistoryTable
      .slice(uahInvocation, correct, incorrectAttempts, historyJson)
      .select { (uahUserRef eq userDbmsId) and (uahMd5 eq md5) and (uahInvocation eq invocation.value) }
      .map {
        val json = it[historyJson]
        val history = Json.decodeFromString<List<String>>(json).toMutableList()
        ChallengeHistory(Invocation(it[uahInvocation]), it[correct], it[incorrectAttempts].toInt(), history)
      }
      .firstOrNull() ?: ChallengeHistory(invocation)
  }

  fun assignActiveClassCode(classCode: ClassCode, resetPreviousClassCode: Boolean) =
    transaction {
      UserSessionsTable
        .upsert(conflictIndex = userSessionIndex) { row ->
          row[sessionRef] = sessionDbmsId()
          row[userRef] = userDbmsId
          row[updated] = DateTime.now(UTC)
          row[activeClassCode] = classCode.classCode
          if (resetPreviousClassCode)
            row[previousTeacherClassCode] = classCode.classCode
        }
    }

  fun resetActiveClassCode() {
    logger.info { "Resetting $fullName ($email) active class code" }
    UserSessionsTable
      .upsert(conflictIndex = userSessionIndex) { row ->
        row[sessionRef] = sessionDbmsId()
        row[userRef] = userDbmsId
        row[updated] = DateTime.now(UTC)
        row[activeClassCode] = DISABLED_CLASS_CODE.classCode
        row[previousTeacherClassCode] = DISABLED_CLASS_CODE.classCode
      }
  }

  fun isEnrolled(classCode: ClassCode) =
    transaction {
      EnrolleesTable
        .slice(Count(EnrolleesTable.id))
        .select { EnrolleesTable.userRef eq userDbmsId }
        .map { it[0] as Long }
        .first().also { logger.info { "isEnrolled() returned $it for $classCode" } } > 0
    }

  fun correctAnswersKey(languageName: LanguageName, groupName: GroupName, challengeName: ChallengeName) =
    keyOf(CORRECT_ANSWERS_KEY, AUTH_KEY, userId, md5Of(languageName, groupName, challengeName))

  fun challengeAnswersKey(languageName: LanguageName, groupName: GroupName, challengeName: ChallengeName) =
    keyOf(CHALLENGE_ANSWERS_KEY, AUTH_KEY, userId, md5Of(languageName, groupName, challengeName))

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
        UsersTable
          .update({ UsersTable.id eq enrollee.userDbmsId }) { row ->
            row[updated] = DateTime.now(UTC)
            row[enrolledClassCode] = DISABLED_CLASS_CODE.classCode
          }
      }
  }

  fun isUniqueClassDesc(classDesc: String) =
    transaction {
      ClassesTable
        .slice(Count(ClassesTable.id))
        .select { ClassesTable.description eq classDesc }
        .map { it[0] as Long }
        .first() == 0L
    }

  fun userPasswordResetId() =
    transaction {
      PasswordResetsTable
        .slice(PasswordResetsTable.resetId)
        .select { PasswordResetsTable.userRef eq userDbmsId }
        .map { it[0] as String }.also { logger.info { "userPasswordResetId() returned $it" } }
        .map { ResetId(it) }
        .firstOrNull() ?: EMPTY_RESET_ID
    }

  fun savePasswordResetId(email: Email, newResetId: ResetId) {
    transaction {
      PasswordResetsTable
        .upsert(conflictIndex = passwordResetsIndex) { row ->
          row[userRef] = userDbmsId
          row[updated] = DateTime.now(UTC)
          row[resetId] = newResetId.value
          row[PasswordResetsTable.email] = email.value
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
    logger.info { "UserId: $userId" }
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
      UsersTable.deleteWhere { UsersTable.id eq userDbmsId }
    }
  }

  suspend fun publishAnswers(
    challengeMd5: String,
    maxHistoryLength: Int,
    complete: Boolean,
    numCorrect: Int,
    history: ChallengeHistory
  ) {
    // Publish to challenge dashboard
    logger.debug { "Publishing user answers to $enrolledClassCode on $challengeMd5 for $this" }
    val dashboardHistory = DashboardHistory(
      history.invocation.value,
      history.correct,
      history.answers.asReversed().take(maxHistoryLength).joinToString("<br>")
    )
    val targetName = classTargetName(enrolledClassCode, challengeMd5)
    val dashboardInfo = DashboardInfo(userId, complete, numCorrect, dashboardHistory)
    (if (isMultiServerEnabled()) multiServerWsWriteChannel else singleServerWsChannel)
      .send(ChallengeAnswerData(USER_ANSWERS, targetName, dashboardInfo.toJson()))
  }

  suspend fun publishLikeDislike(challengeMd5: String, likeDislike: Int) {
    logger.debug { "Publishing user likeDislike to $enrolledClassCode on $challengeMd5 for $this" }
    val targetName = classTargetName(enrolledClassCode, challengeMd5)
    val emoji = likeDislikeEmoji(likeDislike)
    val likeDislikeInfo = LikeDislikeInfo(userId, emoji)
    (if (isMultiServerEnabled()) multiServerWsWriteChannel else singleServerWsChannel)
      .send(ChallengeAnswerData(LIKE_DISLIKE, targetName, likeDislikeInfo.toJson()))
  }

  suspend fun resetHistory(funcInfo: FunctionInfo, challenge: Challenge, maxHistoryLength: Int) {
    logger.debug { "Resetting challenge: $challenge" }

    funcInfo.invocations
      .map { ChallengeResults(invocation = it) }
      .forEach { result ->
        // Reset the history of each answer on a per-invocation basis
        logger.debug { "Resetting invocation: ${result.invocation}" }
        val history = ChallengeHistory(result.invocation).apply { markUnanswered() }
        transaction {
          UserAnswerHistoryTable
            .upsert(conflictIndex = userAnswerHistoryIndex) { row ->
              row[userRef] = userDbmsId
              row[md5] = challenge.md5(result.invocation)
              row[updated] = DateTime.now(UTC)
              row[invocation] = history.invocation.value
              row[correct] = false
              row[incorrectAttempts] = 0
              row[historyJson] = Json.encodeToString(emptyList<String>())
            }
        }

        if (shouldPublish())
          funcInfo.challengeMd5.value.also { md5 ->
            publishAnswers(md5, maxHistoryLength, false, 0, history)
            publishLikeDislike(md5, 0)
          }
      }
  }

  fun shouldPublish(classCode: ClassCode = enrolledClassCode) =
    when {
      !isDbmsEnabled() -> false
      classCode.isEnabled -> {
        // Check to see if the teacher that owns class has it set as their active class in one of the sessions
        val teacherId = classCode.fetchClassTeacherId()
        teacherId.isNotEmpty() && teacherId.toUser().interestedInActiveClassCode(classCode)
          .also { logger.debug { "Publishing teacherId: $teacherId for $classCode" } }
      }
      else -> false
    }

  override fun toString() = "User(userId='$userId', name='$fullName')"

  companion object : KLogging() {

    // Class code a user is enrolled in. Will report answers to when in student mode
    // This is not browser-id specific
    //internal const val ENROLLED_CLASS_CODE_FIELD = "enrolled-class-code"

    // Class code you will observe updates on when in teacher mode
    // This is browser-id specific
    //private const val ACTIVE_CLASS_CODE_FIELD = "active-class-code"

    // Previous teacher class code that a user had
    // This is browser-id specific
    //private const val PREVIOUS_TEACHER_CLASS_CODE_FIELD = "previous-teacher-class-code"

    val userIdCache = ConcurrentHashMap<String, Long>()
    val emailCache = ConcurrentHashMap<String, Email>()

    fun String.toUser(browserSession: BrowserSession? = null) = User(this, browserSession, true)

    fun String.toUser(row: ResultRow) = User(this, null, row)

    fun queryActiveTeachingClassCode(user: User?) =
      when {
        user.isNull() || !isDbmsEnabled() -> DISABLED_CLASS_CODE
        else ->
          transaction {
            UserSessionsTable
              .slice(UserSessionsTable.activeClassCode)
              .select { (UserSessionsTable.sessionRef eq user.sessionDbmsId()) and (UserSessionsTable.userRef eq user.userDbmsId) }
              .map { it[0] as String }
              .firstOrNull()?.let { ClassCode(it) } ?: DISABLED_CLASS_CODE
          }
      }

    fun queryPreviousTeacherClassCode(user: User?) =
      when {
        user.isNull() || !isDbmsEnabled() -> DISABLED_CLASS_CODE
        else ->
          transaction {
            UserSessionsTable
              .slice(UserSessionsTable.previousTeacherClassCode)
              .select { (UserSessionsTable.sessionRef eq user.sessionDbmsId()) and (UserSessionsTable.userRef eq user.userDbmsId) }
              .map { it[0] as String }
              .firstOrNull()?.let { ClassCode(it) } ?: DISABLED_CLASS_CODE
          }
      }

    fun userExists(userId: String) =
      transaction {
        UsersTable
          .slice(Count(UsersTable.id))
          .select { UsersTable.userId eq userId }
          .map { it[0] as Long }
          .first() > 0
      }

    fun fetchUserDbmsIdFromCache(userId: String) =
      userIdCache.computeIfAbsent(userId) {
        queryUserDbmsId(userId).also { logger.debug { "Looked up userDbmsId for $userId: $it" } }
      }

    fun fetchEmailFromCache(userId: String) =
      emailCache.computeIfAbsent(userId) {
        queryUserEmail(userId).also { logger.debug { "Looked up email for $userId: $it" } }
      }

    private fun queryUserDbmsId(userId: String, defaultIfMissing: Long = -1) =
      transaction {
        UsersTable
          .slice(UsersTable.id)
          .select { UsersTable.userId eq userId }
          .map { it[UsersTable.id].value }
          .firstOrNull() ?: defaultIfMissing
      }

    private fun queryUserEmail(userId: String, defaultIfMissing: Email = UNKNOWN_EMAIL) =
      transaction {
        UsersTable
          .slice(UsersTable.email)
          .select { UsersTable.userId eq userId }
          .map { Email(it[0] as String) }
          .firstOrNull() ?: defaultIfMissing
      }

    fun createUnknownUser(userId: String) =
      transaction {
        UsersTable
          .insertAndGetId { row ->
            row[UsersTable.userId] = userId
            row[fullName] = UNKNOWN_FULLNAME.value
            row[email] = "${UNKNOWN_EMAIL.value}-${randomId(4)}"
            row[enrolledClassCode] = DISABLED_CLASS_CODE.classCode
            row[defaultLanguage] = defaultLanguageType.languageName.value
            row[salt] = UNKNOWN
            row[digest] = UNKNOWN
          }.value.also { logger.info { "Created unknown user $it" } }
      }

    fun createUser(
      name: FullName,
      email: Email,
      password: Password,
      browserSession: BrowserSession?
    ): User =
      User(randomId(25), browserSession, false)
        .also { user ->
          transaction {
            val salt = newStringSalt()
            val digest = password.sha256(salt)
            val userDbmsId =
              UsersTable
                .insertAndGetId { row ->
                  row[userId] = user.userId
                  row[fullName] = name.value.maxLength(128)
                  row[UsersTable.email] = email.value.maxLength(128)
                  row[enrolledClassCode] = DISABLED_CLASS_CODE.classCode
                  row[defaultLanguage] = defaultLanguageType.languageName.value
                  row[UsersTable.salt] = salt
                  row[UsersTable.digest] = digest
                }.value

            val browserId =
              browserSession?.sessionDbmsId() ?: error("Missing browser session")

            UserSessionsTable
              .insert { row ->
                row[sessionRef] = browserId
                row[userRef] = userDbmsId
                row[activeClassCode] = DISABLED_CLASS_CODE.classCode
                row[previousTeacherClassCode] = DISABLED_CLASS_CODE.classCode
              }
          }
          logger.info { "Created user $email ${user.userId}" }
        }

    private fun isRegisteredEmail(email: Email) = queryUserByEmail(email).isNotNull()

    fun isNotRegisteredEmail(email: Email) = !isRegisteredEmail(email)

    fun queryUserByEmail(email: Email): User? =
      transaction {
        UsersTable
          .slice(UsersTable.userId)
          .select { UsersTable.email eq email.value }
          .map { (it[0] as String).toUser() }
          .firstOrNull()
          .also { logger.info { "queryUserByEmail() returned: ${it?.email ?: " ${email.value} not found"}" } }
      }
  }
}

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