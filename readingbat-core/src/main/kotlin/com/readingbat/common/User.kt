/*
 * Copyright © 2026 Paul Ambrose (pambrose@mac.com)
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

package com.readingbat.common

import com.pambrose.common.email.Email
import com.pambrose.common.email.Email.Companion.EMPTY_EMAIL
import com.pambrose.common.email.Email.Companion.UNKNOWN_EMAIL
import com.pambrose.common.exposed.get
import com.pambrose.common.exposed.readonlyTx
import com.pambrose.common.exposed.upsert
import com.pambrose.common.util.maxLength
import com.pambrose.common.util.md5Of
import com.pambrose.common.util.randomId
import com.readingbat.common.ClassCode.Companion.DISABLED_CLASS_CODE
import com.readingbat.common.ClassCodeRepository.addEnrollee
import com.readingbat.common.ClassCodeRepository.fetchClassTeacherId
import com.readingbat.common.ClassCodeRepository.fetchEnrollees
import com.readingbat.common.ClassCodeRepository.isNotValid
import com.readingbat.common.ClassCodeRepository.isValid
import com.readingbat.common.ClassCodeRepository.removeEnrollee
import com.readingbat.common.Endpoints.THUMBS_DOWN
import com.readingbat.common.Endpoints.THUMBS_UP
import com.readingbat.common.KeyConstants.AUTH_KEY
import com.readingbat.common.KeyConstants.CHALLENGE_ANSWERS_KEY
import com.readingbat.common.KeyConstants.CORRECT_ANSWERS_KEY
import com.readingbat.common.KeyConstants.keyOf
import com.readingbat.dsl.DataException
import com.readingbat.dsl.LanguageType.Companion.defaultLanguageType
import com.readingbat.dsl.LanguageType.Companion.toLanguageType
import com.readingbat.dsl.challenge.Challenge
import com.readingbat.dsl.isDbmsEnabled
import com.readingbat.posts.ChallengeHistory
import com.readingbat.posts.ChallengeResults
import com.readingbat.server.BrowserSessionsTable
import com.readingbat.server.ChallengeName
import com.readingbat.server.ClassesTable
import com.readingbat.server.EnrolleesTable
import com.readingbat.server.FullName
import com.readingbat.server.FullName.Companion.EMPTY_FULLNAME
import com.readingbat.server.FullName.Companion.UNKNOWN_FULLNAME
import com.readingbat.server.GroupName
import com.readingbat.server.Invocation
import com.readingbat.server.LanguageName
import com.readingbat.server.OAuthLinksTable
import com.readingbat.server.ReadingBatServer.adminUsers
import com.readingbat.server.UserAnswerHistoryTable
import com.readingbat.server.UserChallengeInfoTable
import com.readingbat.server.UserSessionsTable
import com.readingbat.server.UsersTable
import com.readingbat.server.userAnswerHistoryIndex
import com.readingbat.server.userSessionIndex
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.html.Entities.nbsp
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.Count
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.concurrent.ConcurrentHashMap
import kotlin.contracts.contract
import kotlin.time.measureTime

/**
 * Represents an authenticated user account in the ReadingBat system.
 *
 * Manages user identity, class enrollment, challenge answer history, and session tracking.
 * Users are persisted in the database via [UsersTable] and can be created through standard
 * registration or OAuth providers (GitHub, Google).
 *
 * Key responsibilities:
 * - Tracking enrolled and active class codes for student/teacher modes
 * - Recording challenge answer history and correctness
 * - Managing like/dislike feedback on challenges
 * - Publishing answer updates via WebSocket for real-time teacher dashboards
 */
class User {
  private constructor(
    idStr: String,
    browserSession: BrowserSession?,
    initFields: Boolean,
  ) {
    this.userId = idStr
    this.browserSession = browserSession

    if (initFields && isDbmsEnabled()) {
      measureTime {
        readonlyTx {
          with(UsersTable) {
            selectAll()
              .where { userId eq this@User.userId }
              .map { assignRowVals(it) }
              .firstOrNull() ?: error("UserId not found: ${this@User.userId}")
          }
        }
      }.also { logger.debug { "Selected user info in $it" } }
    }
  }

  private constructor(
    userId: String,
    browserSession: BrowserSession?,
    row: ResultRow,
  ) {
    this.userId = userId
    this.browserSession = browserSession
    assignRowVals(row)
  }

  /** Unique identifier for this user, generated at creation time. */
  val userId: String

  /** The browser session associated with the current request, if any. */
  val browserSession: BrowserSession?

  /** Database primary key for this user. */
  var userDbmsId: Long = -1

  /** User's email address. */
  var email: Email = EMPTY_EMAIL

  /** User's display name. */
  var fullName: FullName = EMPTY_FULLNAME

  /** The class code this user is enrolled in as a student, or [DISABLED_CLASS_CODE] if not enrolled. */
  var enrolledClassCode: ClassCode = DISABLED_CLASS_CODE

  /** The user's preferred programming language for content display. */
  var defaultLanguage = defaultLanguageType

  /** URL of the user's avatar image, typically from an OAuth provider. */
  var avatarUrl: String? = null

  /** Whether this user has been loaded from and verified against the database. */
  var existsInDbms: Boolean = false
    private set

  private fun queryOrCreateSessionDbmsId() =
    browserSession?.queryOrCreateSessionDbmsId() ?: error("Null browser session")

  private fun assignRowVals(row: ResultRow) {
    userDbmsId = row[UsersTable.id].value
    email = Email(row[UsersTable.email])
    fullName =
      FullName(row[UsersTable.fullName])
    enrolledClassCode =
      ClassCode(row[UsersTable.enrolledClassCode])
    defaultLanguage = row[UsersTable.defaultLanguage].toLanguageType() ?: defaultLanguageType
    avatarUrl = row[UsersTable.avatarUrl]
    existsInDbms = true
  }

  fun browserSessions() =
    readonlyTx {
      (BrowserSessionsTable innerJoin UserSessionsTable)
        .select(BrowserSessionsTable.sessionId)
        .where { UserSessionsTable.userRef eq userDbmsId }
        .map { it[0] as String }
    }

  // Look across all possible browser sessions
  private fun interestedInActiveClassCode(classCode: ClassCode) =
    readonlyTx {
      with(UserSessionsTable) {
        select(Count(id))
          .where { (userRef eq userDbmsId) and (activeClassCode eq classCode.classCode) }
          .map { it[0] as Long }
          .first() > 0
      }
    }

  fun correctAnswers() =
    readonlyTx {
      with(UserChallengeInfoTable) {
        select(allCorrect)
          .where { (userRef eq userDbmsId) and allCorrect }
          .map { (it[0] as Boolean).toString() }
      }
    }

  fun likeDislikeEmoji(likeDislike: Int) =
    when (likeDislike) {
      1 -> THUMBS_UP
      2 -> THUMBS_DOWN
      else -> nbsp.text
    }

  fun likeDislikeEmoji(challenge: Challenge) = likeDislikeEmoji(likeDislike(challenge))

  fun likeDislike(challenge: Challenge) =
    readonlyTx {
      with(UserChallengeInfoTable) {
        select(likeDislike)
          .where { (userRef eq userDbmsId) and (md5 eq challenge.md5()) }
          .map { it[likeDislike].toInt() }
          .firstOrNull() ?: 0
      }
    }

  fun likeDislikes() =
    readonlyTx {
      with(UserChallengeInfoTable) {
        select(likeDislike)
          .where { (userRef eq userDbmsId) and ((likeDislike eq 1) or (likeDislike eq 2)) }
          .map { it.toString() }
      }
    }

  fun classCount() =
    readonlyTx {
      with(ClassesTable) {
        select(Count(classCode))
          .where { userRef eq userDbmsId }
          .map { it[0] as Long }
          .first().also { logger.info { "classCount() returned $it" } }
          .toInt()
      }
    }

  fun addClassCode(classCodeVal: ClassCode, classDesc: String) =
    transaction {
      with(ClassesTable) {
        insert { row ->
          row[userRef] = userDbmsId
          row[classCode] = classCodeVal.classCode
          row[description] = classDesc.maxLength(256)
        }
      }
    }

  fun classCodes() =
    readonlyTx {
      with(ClassesTable) {
        select(classCode)
          .where { userRef eq userDbmsId }
          .map { ClassCode(it[0] as String) }
      }
    }

  fun isInDbms() =
    readonlyTx {
      with(UsersTable) {
        select(Count(id))
          .where { id eq userDbmsId }
          .map { it[0] as Long }
          .first() > 0
      }
    }

  private fun assignEnrolledClassCode(classCode: ClassCode) =
    with(UsersTable) {
      update({ id eq userDbmsId }) { row ->
        row[updated] = nowInstant()
        row[enrolledClassCode] = classCode.classCode
        this@User.enrolledClassCode = classCode
      }
    }

  fun challenges() =
    readonlyTx {
      with(UserChallengeInfoTable) {
        select(md5)
          .where { userRef eq userDbmsId }
          .map { it[0] as String }.also { logger.info { "challenges() return ${it.size}" } }
      }
    }

  fun invocations() =
    readonlyTx {
      with(UserAnswerHistoryTable) {
        select(md5)
          .where { userRef eq userDbmsId }
          .map { it[0] as String }.also { logger.info { "invocations() return ${it.size}" } }
      }
    }

  fun historyExists(md5Val: String, invocationVal: Invocation) =
    readonlyTx {
      with(UserAnswerHistoryTable) {
        select(Count(id))
          .where { (userRef eq userDbmsId) and (md5 eq md5Val) and (invocation eq invocationVal.value) }
          .map { it[0] as Long }
          .first() > 0
      }
    }

  fun answerHistory(md5Val: String, invocationVal: Invocation): ChallengeHistory =
    readonlyTx {
      answerHistoryQuery(md5Val, invocationVal)
    }

  fun answerHistoryInTransaction(md5Val: String, invocationVal: Invocation): ChallengeHistory =
    answerHistoryQuery(md5Val, invocationVal)

  private fun answerHistoryQuery(md5Val: String, invocationVal: Invocation): ChallengeHistory =
    with(UserAnswerHistoryTable) {
      select(invocation, correct, incorrectAttempts, historyJson)
        .where { (userRef eq userDbmsId) and (md5 eq md5Val) and (invocation eq invocationVal.value) }
        .map {
          val json = it[historyJson]
          val history = Json.decodeFromString<List<String>>(json).toMutableList()
          ChallengeHistory(
            Invocation(it[invocation]),
            it[correct],
            it[incorrectAttempts],
            history,
          )
        }
        .firstOrNull() ?: ChallengeHistory(invocationVal)
    }

  fun answerHistoryBulk(challengeMd5s: List<String>): Map<String, ChallengeHistory> =
    readonlyTx {
      with(UserAnswerHistoryTable) {
        select(md5, invocation, correct, incorrectAttempts, historyJson)
          .where { (userRef eq userDbmsId) and (md5 inList challengeMd5s) }
          .associate {
            val json = it[historyJson]
            val history = Json.decodeFromString<List<String>>(json).toMutableList()
            it[md5] to
              ChallengeHistory(
                Invocation(it[invocation]),
                it[correct],
                it[incorrectAttempts],
                history,
              )
          }
      }
    }

  fun assignActiveClassCode(classCode: ClassCode, resetPreviousClassCode: Boolean) =
    transaction {
      with(UserSessionsTable) {
        upsert(conflictIndex = userSessionIndex) { row ->
          row[sessionRef] = queryOrCreateSessionDbmsId()
          row[userRef] = userDbmsId
          row[updated] = nowInstant()
          row[activeClassCode] = classCode.classCode
          if (resetPreviousClassCode)
            row[previousTeacherClassCode] = classCode.classCode
        }
      }
    }

  fun resetActiveClassCode() {
    logger.info { "Resetting $fullName ($email) active class code" }
    transaction {
      with(UserSessionsTable) {
        upsert(conflictIndex = userSessionIndex) { row ->
          row[sessionRef] = queryOrCreateSessionDbmsId()
          row[userRef] = userDbmsId
          row[updated] = nowInstant()
          row[activeClassCode] = DISABLED_CLASS_CODE.classCode
          row[previousTeacherClassCode] = DISABLED_CLASS_CODE.classCode
        }
      }
    }
  }

  fun isEnrolled(classCode: ClassCode) =
    readonlyTx {
      val count = Count(EnrolleesTable.id)
      (ClassesTable innerJoin EnrolleesTable)
        .select(count)
        .where { (EnrolleesTable.userRef eq userDbmsId) and (ClassesTable.classCode eq classCode.classCode) }
        .single()[count].also { logger.debug { "isEnrolled() returned $it for $classCode" } } > 0
    }

  fun correctAnswersKey(languageName: LanguageName, groupName: GroupName, challengeName: ChallengeName) =
    keyOf(CORRECT_ANSWERS_KEY, AUTH_KEY, userId, md5Of(languageName, groupName, challengeName))

  fun challengeAnswersKey(languageName: LanguageName, groupName: GroupName, challengeName: ChallengeName) =
    keyOf(CHALLENGE_ANSWERS_KEY, AUTH_KEY, userId, md5Of(languageName, groupName, challengeName))

  fun isNotEnrolled(classCode: ClassCode) = !isEnrolled(classCode)

  /** Enrolls this user in the given class, withdrawing from any previously enrolled class. */
  fun enrollInClass(classCode: ClassCode) {
    when {
      classCode.isNotEnabled -> {
        throw DataException("Not reportable class code")
      }

      classCode.isNotValid() -> {
        throw DataException("Invalid class code: $classCode")
      }

      isEnrolled(classCode) -> {
        throw DataException("Already enrolled in class $classCode")
      }

      else -> {
        transaction {
          val previousClassCode = enrolledClassCode
          if (previousClassCode.isEnabled)
            previousClassCode.removeEnrollee(this@User)
          assignEnrolledClassCode(classCode)
          classCode.addEnrollee(this@User)
        }
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
    transaction {
      enrollees
        .forEach { enrollee ->
          logger.info { "Assigning ${enrollee.email} to $DISABLED_CLASS_CODE" }
          with(UsersTable) {
            update({ id eq enrollee.userDbmsId }) { row ->
              row[updated] = nowInstant()
              row[enrolledClassCode] = DISABLED_CLASS_CODE.classCode
            }
          }
        }
    }
  }

  fun isUniqueClassDesc(classDesc: String) =
    readonlyTx {
      with(ClassesTable) {
        select(Count(id))
          .where { description eq classDesc }
          .map { it[0] as Long }
          .first() == 0L
      }
    }

  /** Deletes this user and all associated data, unenrolling all enrollees from classes this user owns. */
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
      with(UsersTable) {
        deleteWhere { id eq this@User.userDbmsId }
      }
    }
  }

  /** Resets the answer history for all invocations of the given challenge, publishing updates if applicable. */
  suspend fun resetHistory(funcInfo: FunctionInfo, challenge: Challenge, maxHistoryLength: Int) {
    logger.debug { "Resetting challenge: $challenge" }

    funcInfo.invocations
      .map { ChallengeResults(invocation = it) }
      .forEach { result ->
        // Reset the history of each answer on a per-invocation basis
        logger.debug { "Resetting invocation: ${result.invocation}" }
        val history = ChallengeHistory(result.invocation).apply { markUnanswered() }
        transaction {
          with(UserAnswerHistoryTable) {
            upsert(conflictIndex = userAnswerHistoryIndex) { row ->
              row[userRef] = userDbmsId
              row[md5] = challenge.md5(result.invocation)
              row[updated] = nowInstant()
              row[invocation] = history.invocation.value
              row[correct] = false
              row[incorrectAttempts] = 0
              row[historyJson] = Json.encodeToString(emptyList<String>())
            }
          }
        }

        if (shouldPublish())
          funcInfo.challengeMd5.value.also { md5 ->
            AnswerPublisher.publishAnswers(this@User, md5, maxHistoryLength, false, 0, history)
            AnswerPublisher.publishLikeDislike(this@User, md5, 0)
          }
      }
  }

  /** Returns true if answer updates should be published via WebSocket, based on whether a teacher is actively monitoring. */
  fun shouldPublish(classCode: ClassCode = enrolledClassCode) =
    when {
      !isDbmsEnabled() -> {
        false
      }

      classCode.isEnabled -> {
        // Check to see if the teacher that owns class has it set as their active class in one of the sessions
        val teacherId = classCode.fetchClassTeacherId()
        teacherId.isNotEmpty() &&
          teacherId.toUser().interestedInActiveClassCode(classCode)
            .also { logger.debug { "Publishing teacherId: $teacherId for $classCode" } }
      }

      else -> {
        false
      }
    }

  override fun toString() = "User(userId='$userId', name='$fullName')"

  companion object {
    // Class code a user is enrolled in. Will report answers to when in student mode
    // This is not browser-id specific
    // internal const val ENROLLED_CLASS_CODE_FIELD = "enrolled-class-code"

    // Class code you will observe updates on when in teacher mode
    // This is browser-id specific
    // private const val ACTIVE_CLASS_CODE_FIELD = "active-class-code"

    // Previous teacher class code that a user had
    // This is browser-id specific
    // private const val PREVIOUS_TEACHER_CLASS_CODE_FIELD = "previous-teacher-class-code"

    private val logger = KotlinLogging.logger {}

    val userIdCache = ConcurrentHashMap<String, Long>()
    val emailCache = ConcurrentHashMap<String, Email>()

    /** Converts a userId string to a fully-initialized [User] by loading from the database. */
    fun String.toUser(browserSession: BrowserSession? = null) = User(this, browserSession, true)

    fun String.toUser(row: ResultRow) = User(this, null, row)

    /** Returns the class code the user is actively monitoring as a teacher, or [DISABLED_CLASS_CODE] if none. */
    fun queryActiveTeachingClassCode(user: User?) =
      when {
        user == null || !isDbmsEnabled() -> {
          DISABLED_CLASS_CODE
        }

        else -> {
          transaction {
            with(UserSessionsTable) {
              select(activeClassCode)
                .where { (sessionRef eq user.queryOrCreateSessionDbmsId()) and (userRef eq user.userDbmsId) }
                .map { it[0] as String }
                .firstOrNull()?.let { ClassCode(it) } ?: DISABLED_CLASS_CODE
            }
          }
        }
      }

    fun queryPreviousTeacherClassCode(user: User?) =
      when {
        user == null || !isDbmsEnabled() -> {
          DISABLED_CLASS_CODE
        }

        else -> {
          transaction {
            with(UserSessionsTable) {
              select(previousTeacherClassCode)
                .where { (sessionRef eq user.queryOrCreateSessionDbmsId()) and (userRef eq user.userDbmsId) }
                .map { it[0] as String }
                .firstOrNull()?.let { ClassCode(it) } ?: DISABLED_CLASS_CODE
            }
          }
        }
      }

    fun userExists(idVal: String) =
      readonlyTx {
        with(UsersTable) {
          select(Count(id))
            .where { userId eq idVal }
            .map { it[0] as Long }
            .first() > 0
        }
      }

    fun fetchUserDbmsIdFromCache(userId: String) =
      userIdCache.computeIfAbsent(userId) {
        queryUserDbmsId(userId).also { logger.debug { "Looked up userDbmsId for $userId: $it" } }
      }

    fun fetchEmailFromCache(userId: String) =
      emailCache.computeIfAbsent(userId) {
        queryUserEmail(userId).also { logger.debug { "Looked up email for $userId: $it" } }
      }

    private fun queryUserDbmsId(idVal: String, defaultIfMissing: Long = -1) =
      readonlyTx {
        with(UsersTable) {
          select(id)
            .where { userId eq idVal }
            .map { it[id].value }
            .firstOrNull() ?: defaultIfMissing
        }
      }

    private fun queryUserEmail(userIdVal: String, defaultIfMissing: Email = UNKNOWN_EMAIL) =
      readonlyTx {
        with(UsersTable) {
          select(email)
            .where { userId eq userIdVal }
            .map { Email(it[0] as String) }
            .firstOrNull() ?: defaultIfMissing
        }
      }

    fun createUnknownUser(userIdVal: String) =
      transaction {
        with(UsersTable) {
          insertAndGetId { row ->
            row[userId] = userIdVal
            row[fullName] = UNKNOWN_FULLNAME.value
            row[email] = "${UNKNOWN_EMAIL.value}-${randomId(4)}"
            row[enrolledClassCode] = DISABLED_CLASS_CODE.classCode
            row[defaultLanguage] = defaultLanguageType.languageName.value
          }.value.also { logger.info { "Created unknown user $it" } }
        }
      }

    /** Creates a new user account via an OAuth provider (e.g., GitHub, Google). */
    fun createOAuthUser(
      name: FullName,
      emailVal: Email,
      provider: String,
      providerId: String,
      accessToken: String,
      avatarUrlVal: String? = null,
    ): User =
      User(randomId(25), null, false)
        .also { user ->
          transaction {
            user.userDbmsId =
              with(UsersTable) {
                insertAndGetId { row ->
                  row[userId] = user.userId
                  row[fullName] = name.value.maxLength(128)
                  row[email] = emailVal.value.maxLength(128)
                  row[enrolledClassCode] = DISABLED_CLASS_CODE.classCode
                  row[defaultLanguage] = defaultLanguageType.languageName.value
                  row[authProvider] = provider
                  row[avatarUrl] = avatarUrlVal
                }.value
              }

            with(OAuthLinksTable) {
              insert { row ->
                row[userRef] = user.userDbmsId
                row[OAuthLinksTable.provider] = provider
                row[OAuthLinksTable.providerId] = providerId
                row[providerEmail] = emailVal.value
                row[OAuthLinksTable.accessToken] = accessToken
              }
            }
          }
          user.existsInDbms = true
          logger.info { "Created OAuth user $emailVal ${user.userId} via $provider" }
        }

    private fun isRegisteredEmail(email: Email) = queryUserByEmail(email) != null

    fun isNotRegisteredEmail(email: Email) = !isRegisteredEmail(email)

    fun queryUserByEmail(emailVal: Email): User? =
      readonlyTx {
        with(UsersTable) {
          select(userId)
            .where { email eq emailVal.value }
            .map { (it[0] as String).toUser() }
            .firstOrNull()
            .also { logger.info { "queryUserByEmail() returned: ${it?.email ?: " ${emailVal.value} not found"}" } }
        }
      }
  }
}

/** Returns true if this user is a valid, authenticated admin user. */
internal fun User?.isAdminUser() = isValidUser() && email.value in adminUsers

internal fun User?.isNotAdminUser() = !isAdminUser()

/** Returns true if this user is null or not verified in the database. Uses Kotlin contracts for smart casting. */
internal fun User?.isNotValidUser(): Boolean {
  contract {
    returns(false) implies (this@isNotValidUser is User)
  }
  return !isValidUser()
}

/** Returns true if this user is non-null and verified in the database. Uses Kotlin contracts for smart casting. */
internal fun User?.isValidUser(): Boolean {
  contract {
    returns(true) implies (this@isValidUser is User)
  }
  return this?.existsInDbms ?: false
}
