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

import com.github.pambrose.common.util.newStringSalt
import com.github.pambrose.common.util.randomId
import com.github.readingbat.dsl.Challenge
import com.github.readingbat.dsl.FunctionInfo
import com.github.readingbat.dsl.InvalidConfigurationException
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.ClassCode.Companion.STUDENT_CLASS_CODE
import com.github.readingbat.misc.Constants.RESP
import com.github.readingbat.misc.KeyConstants.ACTIVE_CLASS_CODE_FIELD
import com.github.readingbat.misc.KeyConstants.ANSWER_HISTORY_KEY
import com.github.readingbat.misc.KeyConstants.AUTH_KEY
import com.github.readingbat.misc.KeyConstants.CHALLENGE_ANSWERS_KEY
import com.github.readingbat.misc.KeyConstants.CORRECT_ANSWERS_KEY
import com.github.readingbat.misc.KeyConstants.DIGEST_FIELD
import com.github.readingbat.misc.KeyConstants.EMAIL_FIELD
import com.github.readingbat.misc.KeyConstants.ENROLLED_CLASS_CODE_FIELD
import com.github.readingbat.misc.KeyConstants.KEY_SEP
import com.github.readingbat.misc.KeyConstants.NAME_FIELD
import com.github.readingbat.misc.KeyConstants.PREVIOUS_TEACHER_CLASS_CODE_FIELD
import com.github.readingbat.misc.KeyConstants.SALT_FIELD
import com.github.readingbat.misc.KeyConstants.USER_CLASSES_KEY
import com.github.readingbat.misc.KeyConstants.USER_INFO_KEY
import com.github.readingbat.misc.KeyConstants.USER_RESET_KEY
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
import com.google.gson.Gson
import mu.KLogging
import redis.clients.jedis.Jedis
import redis.clients.jedis.Transaction
import kotlin.contracts.contract

internal class User private constructor(val id: String) {

  val userInfoKey = listOf(USER_INFO_KEY, id).joinToString(KEY_SEP)
  val userClassesKey = listOf(USER_CLASSES_KEY, id).joinToString(KEY_SEP)

  // This key maps to a reset_id
  val userPasswordResetKey = listOf(USER_RESET_KEY, id).joinToString(KEY_SEP)

  fun email(redis: Jedis) = redis.hget(userInfoKey, EMAIL_FIELD)?.let { Email(it) } ?: EMPTY_EMAIL

  private fun correctAnswersKey(names: ChallengeNames) =
    correctAnswersKey(names.languageName, names.groupName, names.challengeName)

  fun correctAnswersKey(languageName: LanguageName, groupName: GroupName, challengeName: ChallengeName) =
    listOf(CORRECT_ANSWERS_KEY, AUTH_KEY, id, languageName, groupName, challengeName).joinToString(KEY_SEP)

  private fun challengeAnswersKey(names: ChallengeNames) =
    challengeAnswersKey(names.languageName, names.groupName, names.challengeName)

  private fun challengeAnswersKey(languageName: LanguageName, groupName: GroupName, challengeName: ChallengeName) =
    listOf(CHALLENGE_ANSWERS_KEY, AUTH_KEY, id, languageName, groupName, challengeName).joinToString(KEY_SEP)

  private fun answerHistoryKey(names: ChallengeNames, invocation: Invocation) =
    answerHistoryKey(names.languageName, names.groupName, names.challengeName, invocation)

  fun answerHistoryKey(languageName: LanguageName,
                       groupName: GroupName,
                       challengeName: ChallengeName,
                       invocation: Invocation) =
    listOf(ANSWER_HISTORY_KEY, AUTH_KEY, id, languageName, groupName, challengeName, invocation).joinToString(KEY_SEP)

  fun assignEnrolledClassCode(classCode: ClassCode, tx: Transaction) {
    tx.hset(userInfoKey, ENROLLED_CLASS_CODE_FIELD, classCode.value)
  }


  fun assignActiveClassCode(classCode: ClassCode, resetPreviousClassCode: Boolean, redis: Jedis) {
    redis.hset(userInfoKey, ACTIVE_CLASS_CODE_FIELD, if (classCode.isStudentMode) "" else classCode.value)
    if (resetPreviousClassCode)
      redis.hset(userInfoKey, PREVIOUS_TEACHER_CLASS_CODE_FIELD, if (classCode.isStudentMode) "" else classCode.value)
  }

  fun resetActiveClassCode(tx: Transaction) {
    tx.hset(userInfoKey, ACTIVE_CLASS_CODE_FIELD, "")
  }

  fun enrollInClass(classCode: ClassCode, redis: Jedis) {
    if (classCode.isStudentMode) {
      throw DataException("Empty class code")
    }
    else {
      when {
        !classCode.isValid(redis) -> throw DataException("Invalid class code $classCode")
        classCode.isEnrolled(this, redis) -> throw DataException("Already enrolled in class $classCode")
        else -> {
          val previousClassCode = fetchEnrolledClassCode(redis)
          redis.multi().also { tx ->
            // Remove if already enrolled in another class
            if (previousClassCode.isTeacherMode)
              previousClassCode.removeEnrollee(this, tx)

            assignEnrolledClassCode(classCode, tx)

            classCode.addEnrollee(this, tx)

            tx.exec()
          }
        }
      }
    }
  }

  fun withdrawFromClass(classCode: ClassCode, redis: Jedis) {
    if (classCode.isStudentMode) {
      throw DataException("Not enrolled in a class")
    }
    else {
      // This should always be true
      val enrolled = classCode.isValid(redis) && classCode.isEnrolled(this, redis)
      redis.multi().also { tx ->
        assignEnrolledClassCode(STUDENT_CLASS_CODE, tx)
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
    enrollees.forEach { it.assignEnrolledClassCode(STUDENT_CLASS_CODE, tx) }

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

  fun lookupDigestInfoByUser(redis: Jedis): Pair<String, String> {
    val salt = redis.hget(userInfoKey, SALT_FIELD) ?: ""
    val digest = redis.hget(userInfoKey, DIGEST_FIELD) ?: ""
    return salt to digest
  }

  fun savePasswordResetKey(email: Email, previousResetId: ResetId, newResetId: ResetId, tx: Transaction) {
    if (previousResetId.isNotBlank()) {
      tx.del(userPasswordResetKey)
      tx.del(previousResetId.passwordResetKey)
    }

    tx.set(userPasswordResetKey, newResetId.value)
    tx.set(newResetId.passwordResetKey, email.value)
  }

  fun deleteUser(user: User, redis: Jedis) {
    val userEmailKey = user.email(redis).userEmailKey

    val correctAnswers = redis.keys(correctAnswersKey(ANY_LANGUAGE, ANY_GROUP, ANY_CHALLENGE))
    val challenges = redis.keys(challengeAnswersKey(ANY_LANGUAGE, ANY_GROUP, ANY_CHALLENGE))
    val invocations =
      redis.keys(answerHistoryKey(ANY_LANGUAGE, ANY_GROUP, ANY_CHALLENGE, ANY_INVOCATION))
    val classCodes = redis.smembers(userClassesKey).map { ClassCode(it) }
    val enrolleePairs = classCodes.map { it to it.fetchEnrollees(redis) }

    val previousResetId = redis.get(userPasswordResetKey)?.let { ResetId(it) } ?: EMPTY_RESET_ID

    logger.info { "Deleting User: ${user.id} ${user.email(redis)}" }
    logger.info { "User Email: $userEmailKey" }
    logger.info { "User Info: $userInfoKey" }
    logger.info { "User Classes: $userClassesKey" }
    logger.info { "Correct Answers: $correctAnswers" }
    logger.info { "Challenges: $challenges" }
    logger.info { "Invocations: $invocations" }
    logger.info { "Classes: $classCodes" }

    redis.multi().also { tx ->
      if (previousResetId.isNotBlank()) {
        tx.del(userPasswordResetKey)
        tx.del(previousResetId.passwordResetKey)
      }

      tx.del(userEmailKey)
      tx.del(userInfoKey)
      tx.del(userClassesKey)

      correctAnswers.forEach { tx.del(it) }
      challenges.forEach { tx.del(it) }
      invocations.forEach { tx.del(it) }

      // Delete class info
      enrolleePairs.forEach { (classCode, enrollees) -> deleteClassCode(classCode, enrollees, tx) }

      tx.exec()
    }
  }

  fun publishAnswers(maxHistoryLength: Int,
                     complete: Boolean,
                     numCorrect: Int,
                     history: ChallengeHistory,
                     redis: Jedis) {
    // Publish to challenge dashboard
    // First check if enrolled in a class
    val enrolledClassCode = fetchEnrolledClassCode(redis)
    if (enrolledClassCode.isTeacherMode) {
      // Check to see if owner of class has it set as their active class
      val teacherId = enrolledClassCode.fetchClassTeacherId(redis)
      if (teacherId.isNotEmpty() && teacherId.toUser().fetchActiveClassCode(redis) == enrolledClassCode) {
        val dashboardInfo = DashboardInfo(id, complete, numCorrect, maxHistoryLength, history)
        redis.publish(enrolledClassCode.value, gson.toJson(dashboardInfo))
      }
    }
  }

  fun resetHistory(maxHistoryLength: Int,
                   funcInfo: FunctionInfo,
                   languageName: LanguageName,
                   groupName: GroupName,
                   challengeName: ChallengeName,
                   redis: Jedis) {
    funcInfo.invocations
      .map { ChallengeResults(invocation = it) }
      .forEach { result ->
        // Reset the history of each answer on a per-invocation basis
        val answerHistoryKey = answerHistoryKey(languageName, groupName, challengeName, result.invocation)
        if (answerHistoryKey.isNotEmpty()) {
          val history = ChallengeHistory(result.invocation).apply { markUnanswered() }
          logger.info { "Resetting $answerHistoryKey" }
          redis.set(answerHistoryKey, gson.toJson(history))
          publishAnswers(maxHistoryLength, false, 0, history, redis)
        }
      }
  }

  companion object : KLogging() {

    val gson = Gson()

    fun String.toUser() = User(this)

    fun newUser() = User(randomId(25))

    fun User?.fetchActiveClassCode(redis: Jedis?) =
      if (this == null)
        STUDENT_CLASS_CODE
      else
        redis?.hget(userInfoKey, ACTIVE_CLASS_CODE_FIELD)?.let { ClassCode(it) } ?: STUDENT_CLASS_CODE

    fun User?.fetchPreviousTeacherClassCode(redis: Jedis?) =
      if (this == null)
        STUDENT_CLASS_CODE
      else
        redis?.hget(userInfoKey, PREVIOUS_TEACHER_CLASS_CODE_FIELD)?.let { ClassCode(it) } ?: STUDENT_CLASS_CODE

    fun User?.fetchEnrolledClassCode(redis: Jedis) =
      if (this == null)
        STUDENT_CLASS_CODE
      else
        redis.hget(userInfoKey, ENROLLED_CLASS_CODE_FIELD)?.let { ClassCode(it) } ?: STUDENT_CLASS_CODE

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

    fun User?.answerHistoryKey(browserSession: BrowserSession?, names: ChallengeNames, invocation: Invocation) =
      this?.answerHistoryKey(names, invocation) ?: browserSession?.answerHistoryKey(names, invocation) ?: ""

    fun createUser(name: FullName, email: Email, password: Password, redis: Jedis): User {
      // The userName (email) is stored in a single KV pair, enabling changes to the userName
      // Three things are stored:
      // email -> userId
      // userId -> salt and sha256-encoded digest

      val user = newUser()
      val salt = newStringSalt()
      logger.info { "Created user $email ${user.id}" }

      redis.multi().also { tx ->
        tx.set(email.userEmailKey, user.id)
        tx.hset(user.userInfoKey, mapOf(NAME_FIELD to name.value,
                                        EMAIL_FIELD to email.value,
                                        SALT_FIELD to salt,
                                        DIGEST_FIELD to password.sha256(salt),
                                        ENROLLED_CLASS_CODE_FIELD to STUDENT_CLASS_CODE.value,
                                        ACTIVE_CLASS_CODE_FIELD to STUDENT_CLASS_CODE.value))
        tx.exec()
      }

      return user
    }

    fun User?.saveChallengeAnswers(content: ReadingBatContent,
                                   browserSession: BrowserSession?,
                                   names: ChallengeNames,
                                   paramMap: Map<String, String>,
                                   funcInfo: FunctionInfo,
                                   userResponses: List<Map.Entry<String, List<String>>>,
                                   results: List<ChallengeResults>,
                                   redis: Jedis) {
      val challengeAnswerKey = challengeAnswersKey(browserSession, names)
      val correctAnswersKey = correctAnswersKey(browserSession, names)

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

      // Save the history of each answer on a per-invocation basis
      for (result in results) {
        val answerHistoryKey = answerHistoryKey(browserSession, names, result.invocation)
        if (answerHistoryKey.isNotEmpty()) {
          val history =
            gson.fromJson(redis[answerHistoryKey], ChallengeHistory::class.java) ?: ChallengeHistory(result.invocation)

          when {
            !result.answered -> history.markUnanswered()
            result.correct -> history.markCorrect()
            else -> history.markIncorrect(result.userResponse)
          }

          val json = gson.toJson(history)
          logger.debug { "Saving: $json to $answerHistoryKey" }
          redis.set(answerHistoryKey, json)

          this?.publishAnswers(content.maxHistoryLength, complete, numCorrect, history, redis)
        }
      }
    }

    fun isRegisteredEmail(email: Email, redis: Jedis) = lookupUserByEmail(email, redis) != null

    fun lookupUserByEmail(email: Email, redis: Jedis): User? {
      val id = redis.get(email.userEmailKey) ?: ""
      return if (id.isNotEmpty()) id.toUser() else null
    }
  }

  internal data class ChallengeAnswers(val id: String, val answers: MutableMap<String, String> = mutableMapOf())
}

internal fun User?.isValidUser(redis: Jedis): Boolean {
  contract {
    returns(true) implies (this@isValidUser is User)
  }
  return if (this == null) false else redis.hlen(userInfoKey) > 0
}
