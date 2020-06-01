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
import com.github.readingbat.dsl.FunctionInfo
import com.github.readingbat.dsl.InvalidConfigurationException
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.ClassCode.Companion.EMPTY_CLASS_CODE
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
import com.github.readingbat.misc.KeyConstants.RESET_KEY
import com.github.readingbat.misc.KeyConstants.SALT_FIELD
import com.github.readingbat.misc.KeyConstants.USER_CLASSES_KEY
import com.github.readingbat.misc.KeyConstants.USER_INFO_KEY
import com.github.readingbat.misc.KeyConstants.USER_RESET_KEY
import com.github.readingbat.pages.UserPrefsPage
import com.github.readingbat.pages.UserPrefsPage.fetchClassTeacherId
import com.github.readingbat.posts.ChallengeHistory
import com.github.readingbat.posts.ChallengeNames
import com.github.readingbat.posts.ChallengeResults
import com.github.readingbat.posts.DashboardInfo
import com.github.readingbat.server.*
import com.github.readingbat.server.Email.Companion.EMPTY_EMAIL
import com.google.gson.Gson
import mu.KLogging
import redis.clients.jedis.Jedis
import redis.clients.jedis.Transaction

internal class User private constructor(val id: String) {

  val userInfoKey = listOf(USER_INFO_KEY, id).joinToString(KEY_SEP)
  val userClassesKey = listOf(USER_CLASSES_KEY, id).joinToString(KEY_SEP)

  // This key maps to a reset_id
  val userPasswordResetKey = listOf(USER_RESET_KEY, id).joinToString(KEY_SEP)

  fun email(redis: Jedis) = redis.hget(userInfoKey, EMAIL_FIELD)?.let { Email(it) } ?: EMPTY_EMAIL

  private fun correctAnswersKey(names: ChallengeNames) =
    correctAnswersKey(names.languageName, names.groupName, names.challengeName)

  private fun correctAnswersKey(languageName: LanguageName, groupName: GroupName, challengeName: ChallengeName) =
    listOf(CORRECT_ANSWERS_KEY, AUTH_KEY, id, languageName.value, groupName.value, challengeName.value)
      .joinToString(KEY_SEP)

  private fun challengeAnswersKey(names: ChallengeNames) =
    challengeAnswersKey(names.languageName, names.groupName, names.challengeName)

  private fun challengeAnswersKey(languageName: LanguageName, groupName: GroupName, challengeName: ChallengeName) =
    listOf(CHALLENGE_ANSWERS_KEY, AUTH_KEY, id, languageName.value, groupName.value, challengeName.value)
      .joinToString(KEY_SEP)

  private fun answerHistoryKey(names: ChallengeNames, invocation: Invocation) =
    answerHistoryKey(names.languageName, names.groupName, names.challengeName, invocation)

  fun answerHistoryKey(languageName: LanguageName,
                       groupName: GroupName,
                       challengeName: ChallengeName,
                       invocation: Invocation) =
    listOf(ANSWER_HISTORY_KEY,
           AUTH_KEY,
           id,
           languageName.value,
           groupName.value,
           challengeName.value,
           invocation.value).joinToString(KEY_SEP)

  fun fetchEnrolledClassCode(redis: Jedis) =
    redis.hget(userInfoKey, ENROLLED_CLASS_CODE_FIELD).let { ClassCode(it) } ?: EMPTY_CLASS_CODE

  fun assignEnrolledClassCode(classCode: ClassCode, tx: Transaction) =
    tx.hset(userInfoKey, ENROLLED_CLASS_CODE_FIELD, classCode.value)

  fun fetchActiveClassCode(redis: Jedis?) =
    redis?.hget(userInfoKey, ACTIVE_CLASS_CODE_FIELD)?.let { ClassCode(it) } ?: EMPTY_CLASS_CODE

  fun assignActiveClassCode(classCode: ClassCode, redis: Jedis) {
    redis.hset(userInfoKey, ACTIVE_CLASS_CODE_FIELD, if (classCode.isClassesDisabled) "" else classCode.value)
  }

  fun resetActiveClassCode(tx: Transaction) {
    tx.hset(userInfoKey, ACTIVE_CLASS_CODE_FIELD, "")
  }

  fun enrollInClass(classCode: ClassCode, redis: Jedis) {
    if (classCode.isNotEmpty) {
      throw DataException("Empty class code")
    }
    else {
      when {
        classCode.fetchEnrollees(redis).isEmpty() -> throw DataException("Invalid class code $classCode")
        classCode.isEnrolled(this, redis) -> throw DataException("Already enrolled in class $classCode")
        else -> {
          val previousClassCode = fetchEnrolledClassCode(redis)
          redis.multi().also { tx ->
            // Remove if already enrolled in another class
            if (previousClassCode.isEmpty)
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
    if (classCode.isNotEmpty) {
      throw DataException("Not enrolled in a class")
    }
    else {
      // This should always be true
      val enrolled = classCode.isValid(redis) && classCode.isEnrolled(this, redis)
      redis.multi().also { tx ->
        assignEnrolledClassCode(EMPTY_CLASS_CODE, tx)
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
    enrollees.forEach { it.assignEnrolledClassCode(EMPTY_CLASS_CODE, tx) }

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
      .filter { classCode -> classDesc == UserPrefsPage.fetchClassDesc(classCode, redis) }
      .none()

  fun lookupDigestInfoByUser(redis: Jedis): Pair<String, String> {
    val salt = redis.hget(userInfoKey, SALT_FIELD) ?: ""
    val digest = redis.hget(userInfoKey, DIGEST_FIELD) ?: ""
    return salt to digest
  }

  fun savePasswordResetKey(email: Email,
                           previousResetId: String,
                           newResetId: String,
                           tx: Transaction) {
    if (previousResetId.isNotEmpty()) {
      tx.del(userPasswordResetKey)
      tx.del(passwordResetKey(previousResetId))
    }

    tx.set(userPasswordResetKey, newResetId)
    tx.set(passwordResetKey(newResetId), email.value)
  }

  fun deleteUser(principal: UserPrincipal, redis: Jedis) {
    val user = principal.toUser()
    val userEmailKey = user.email(redis).userEmailKey

    val correctAnswers = redis.keys(correctAnswersKey(LanguageName("*"), GroupName("*"), ChallengeName("*")))
    val challenges = redis.keys(challengeAnswersKey(LanguageName("*"), GroupName("*"), ChallengeName("*")))
    val invocations =
      redis.keys(answerHistoryKey(LanguageName("*"), GroupName("*"), ChallengeName("*"), Invocation("*")))
    val classCodes = redis.smembers(userClassesKey).map { ClassCode(it) }
    val enrolleePairs = classCodes.map { it to it.fetchEnrollees(redis) }

    val previousResetId = redis.get(userPasswordResetKey) ?: ""

    logger.info { "Deleting User: ${principal.userId} ${user.email(redis)}" }
    logger.info { "User Email: $userEmailKey" }
    logger.info { "User Info: $userInfoKey" }
    logger.info { "User Classes: $userClassesKey" }
    logger.info { "Correct Answers: $correctAnswers" }
    logger.info { "Challenges: $challenges" }
    logger.info { "Invocations: $invocations" }
    logger.info { "Classes: $classCodes" }

    redis.multi().also { tx ->
      if (previousResetId.isNotEmpty()) {
        tx.del(userPasswordResetKey)
        tx.del(passwordResetKey(previousResetId))
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

  companion object : KLogging() {

    val gson = Gson()

    fun String.toUser() = User(this)

    fun newUser() = User(randomId(25))

    fun correctAnswersKey(user: User?, browserSession: BrowserSession?, names: ChallengeNames) =
      user?.correctAnswersKey(names) ?: browserSession?.correctAnswersKey(names) ?: ""

    fun correctAnswersKey(user: User?,
                          browserSession: BrowserSession?,
                          languageName: LanguageName,
                          groupName: GroupName,
                          challengeName: ChallengeName) =
      user?.correctAnswersKey(languageName, groupName, challengeName)
        ?: browserSession?.correctAnswersKey(languageName, groupName, challengeName)
        ?: ""

    fun challengeAnswersKey(user: User?, browserSession: BrowserSession?, names: ChallengeNames) =
      user?.challengeAnswersKey(names) ?: browserSession?.challengeAnswerKey(names) ?: ""

    fun challengeAnswersKey(user: User?,
                            browserSession: BrowserSession?,
                            languageName: LanguageName,
                            groupName: GroupName,
                            challengeName: ChallengeName) =
      user?.challengeAnswersKey(languageName, groupName, challengeName)
        ?: browserSession?.challengeAnswerKey(languageName, groupName, challengeName)
        ?: ""

    fun answerHistoryKey(user: User?, browserSession: BrowserSession?, names: ChallengeNames, invocation: Invocation) =
      user?.answerHistoryKey(names, invocation) ?: browserSession?.answerHistoryKey(names, invocation) ?: ""

    // Maps resetId to username
    fun passwordResetKey(resetId: String) = listOf(RESET_KEY, resetId).joinToString(KEY_SEP)

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
                                        ENROLLED_CLASS_CODE_FIELD to EMPTY_CLASS_CODE.value,
                                        ACTIVE_CLASS_CODE_FIELD to EMPTY_CLASS_CODE.value))
        tx.exec()
      }

      return user
    }

    fun saveAnswers(content: ReadingBatContent,
                    principal: UserPrincipal?,
                    browserSession: BrowserSession?,
                    redis: Jedis,
                    names: ChallengeNames,
                    compareMap: Map<String, String>,
                    funcInfo: FunctionInfo,
                    userResps: List<Map.Entry<String, List<String>>>,
                    results: List<ChallengeResults>) {
      val user = principal?.toUser()
      val challengeAnswerKey = challengeAnswersKey(user, browserSession, names)
      val correctAnswersKey = correctAnswersKey(user, browserSession, names)
      val enrolledClassCode = user?.fetchEnrolledClassCode(redis) ?: EMPTY_CLASS_CODE
      val complete = results.all { it.correct }
      val numCorrect = results.count { it.correct }

      // Save if all answers were correct
      if (correctAnswersKey.isNotEmpty()) {
        redis.set(correctAnswersKey, complete.toString())
      }

      if (challengeAnswerKey.isNotEmpty()) {
        val answerMap =
          userResps.indices.mapNotNull { i ->
              val userResp = compareMap[RESP + i]?.trim()
                ?: throw InvalidConfigurationException("Missing user response")
              if (userResp.isNotEmpty()) funcInfo.invocations[i] to userResp else null
            }
            .toMap()

        // Save the last answers given
        answerMap.forEach { (invocation, userResp) ->
          redis.hset(challengeAnswerKey, invocation.value, userResp)
        }
      }
      // Save the history of each answer on a per-invocation basis
      results
        .filter { it.answered }
        .forEach { result ->
          val answerHistoryKey = answerHistoryKey(user, browserSession, names, result.invocation)
          if (answerHistoryKey.isNotEmpty()) {
            val history =
              gson.fromJson(redis[answerHistoryKey], ChallengeHistory::class.java)
                ?: ChallengeHistory(result.invocation)
            history.apply { if (result.correct) markCorrect() else markIncorrect(result.userResponse) }
            val json = gson.toJson(history)
            redis.set(answerHistoryKey, json)

            // Publish to challenge dashboard
            // First check if enrolled in a class
            if (enrolledClassCode.isEmpty && user != null) {
              // Check to see if owner of class has it set as their active class
              val teacherId = fetchClassTeacherId(enrolledClassCode, redis)
              if (teacherId.isNotEmpty() && teacherId.toUser().fetchActiveClassCode(redis) == enrolledClassCode) {
                val browserInfo = DashboardInfo(content.maxHistoryLength, user.id, complete, numCorrect, history)
                logger.info { "Publishing data $json" }
                redis.publish(enrolledClassCode.value, gson.toJson(browserInfo))
              }
            }
          }
        }
    }

    fun isValidPrincipal(principal: UserPrincipal, redis: Jedis) = redis.hlen(principal.toUser().userInfoKey) > 0

    fun isRegisteredEmail(email: Email, redis: Jedis) = lookupUserByEmail(email, redis) != null

    fun lookupUserByEmail(email: Email, redis: Jedis): User? {
      val id = redis.get(email.userEmailKey) ?: ""
      return if (id.isNotEmpty()) id.toUser() else null
    }
  }

  internal data class ChallengeAnswers(val id: String, val answers: MutableMap<String, String> = mutableMapOf())
}