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
import com.github.pambrose.common.util.sha256
import com.github.readingbat.dsl.FunctionInfo
import com.github.readingbat.dsl.InvalidConfigurationException
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.Constants.RESP
import com.github.readingbat.misc.KeyConstants.ACTIVE_CLASS_CODE_FIELD
import com.github.readingbat.misc.KeyConstants.ANSWER_HISTORY_KEY
import com.github.readingbat.misc.KeyConstants.AUTH_KEY
import com.github.readingbat.misc.KeyConstants.CHALLENGE_ANSWERS_KEY
import com.github.readingbat.misc.KeyConstants.CLASS_CODE_KEY
import com.github.readingbat.misc.KeyConstants.CLASS_DESC_KEY
import com.github.readingbat.misc.KeyConstants.CORRECT_ANSWERS_KEY
import com.github.readingbat.misc.KeyConstants.DIGEST_FIELD
import com.github.readingbat.misc.KeyConstants.EMAIL_FIELD
import com.github.readingbat.misc.KeyConstants.ENROLLED_CLASS_CODE_FIELD
import com.github.readingbat.misc.KeyConstants.KEY_SEP
import com.github.readingbat.misc.KeyConstants.NAME_FIELD
import com.github.readingbat.misc.KeyConstants.RESET_KEY
import com.github.readingbat.misc.KeyConstants.SALT_FIELD
import com.github.readingbat.misc.KeyConstants.USERID_RESET_KEY
import com.github.readingbat.misc.KeyConstants.USER_CLASSES_KEY
import com.github.readingbat.misc.KeyConstants.USER_EMAIL_KEY
import com.github.readingbat.misc.KeyConstants.USER_INFO_KEY
import com.github.readingbat.posts.ChallengeHistory
import com.github.readingbat.posts.ChallengeNames
import com.github.readingbat.posts.ChallengeResults
import com.github.readingbat.posts.DashboardInfo
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.ServerUtils.fetchPrincipal
import com.google.gson.Gson
import io.ktor.application.call
import io.ktor.sessions.get
import io.ktor.sessions.sessions
import mu.KLogging
import redis.clients.jedis.Jedis
import redis.clients.jedis.Transaction

internal class UserId(val id: String = randomId(25)) {

  val userInfoKey = listOf(USER_INFO_KEY, id).joinToString(KEY_SEP)
  val userClassesKey = listOf(USER_CLASSES_KEY, id).joinToString(KEY_SEP)

  fun email(redis: Jedis) = redis.hget(userInfoKey, EMAIL_FIELD) ?: ""

  private fun correctAnswersKey(names: ChallengeNames) =
    correctAnswersKey(names.languageName, names.groupName, names.challengeName)

  private fun correctAnswersKey(languageName: String, groupName: String, challengeName: String) =
    listOf(CORRECT_ANSWERS_KEY, AUTH_KEY, id, languageName, groupName, challengeName).joinToString(KEY_SEP)

  private fun challengeAnswersKey(names: ChallengeNames) =
    challengeAnswersKey(names.languageName, names.groupName, names.challengeName)

  private fun challengeAnswersKey(languageName: String, groupName: String, challengeName: String) =
    listOf(CHALLENGE_ANSWERS_KEY, AUTH_KEY, id, languageName, groupName, challengeName).joinToString(KEY_SEP)

  private fun answerHistoryKey(names: ChallengeNames, invocation: String) =
    answerHistoryKey(names.languageName, names.groupName, names.challengeName, invocation)

  fun answerHistoryKey(languageName: String, groupName: String, challengeName: String, invocation: String) =
    listOf(ANSWER_HISTORY_KEY, AUTH_KEY, id, languageName, groupName, challengeName, invocation).joinToString(KEY_SEP)

  // This key maps to a reset_id
  fun userIdPasswordResetKey() = listOf(USERID_RESET_KEY, id).joinToString(KEY_SEP)

  fun fetchEnrolledClassCode(redis: Jedis) = redis.hget(userInfoKey, ENROLLED_CLASS_CODE_FIELD) ?: ""

  fun assignEnrolledClassCode(classCode: String, tx: Transaction) =
    tx.hset(userInfoKey, ENROLLED_CLASS_CODE_FIELD, classCode)

  fun fetchActiveClassCode(redis: Jedis?) = redis?.hget(userInfoKey, ACTIVE_CLASS_CODE_FIELD) ?: ""

  fun enrollInClass(classCode: String, redis: Jedis) {
    if (classCode.isBlank()) {
      throw DataException("Empty class code")
    }
    else {
      val classCodeEnrollmentKey = classCodeEnrollmentKey(classCode)
      when {
        redis.smembers(classCodeEnrollmentKey).isEmpty() -> throw DataException("Invalid class code $classCode")
        redis.sismember(classCodeEnrollmentKey, id) -> throw DataException("Already enrolled in class $classCode")
        else -> {
          val previousClassCode = fetchEnrolledClassCode(redis)
          redis.multi().also { tx ->
            // Remove if already enrolled in another class
            if (previousClassCode.isNotEmpty()) {
              tx.srem(classCodeEnrollmentKey(previousClassCode), id)
            }
            assignEnrolledClassCode(classCode, tx)
            tx.sadd(classCodeEnrollmentKey, id)
            tx.exec()
          }
        }
      }
    }
  }

  fun withdrawFromClass(classCode: String, redis: Jedis) {
    if (classCode.isBlank()) {
      throw DataException("Not enrolled in a class")
    }
    else {
      val classCodeEnrollmentKey = classCodeEnrollmentKey(classCode)
      // This should always be true
      val enrolled = redis.exists(classCodeEnrollmentKey) && redis.sismember(classCodeEnrollmentKey, id)
      redis.multi().also { tx ->
        assignEnrolledClassCode("", tx)
        if (enrolled)
          tx.srem(classCodeEnrollmentKey, id)
        tx.exec()
      }
    }
  }

  fun deleteUser(principal: UserPrincipal, redis: Jedis) {
    val userEmailKey = userEmailKey(principal.email(redis))
    val correctAnswers = redis.keys(correctAnswersKey("*", "*", "*"))
    val challenges = redis.keys(challengeAnswersKey("*", "*", "*"))
    val invocations = redis.keys(answerHistoryKey("*", "*", "*", "*"))

    val userIdPasswordResetKey = userIdPasswordResetKey()
    val previousResetId = redis.get(userIdPasswordResetKey) ?: ""

    logger.info { "Deleting User: ${principal.userId} ${principal.email(redis)}" }
    logger.info { "User Email: $userEmailKey" }
    logger.info { "User Info: $userInfoKey" }
    logger.info { "User Classes: $userClassesKey" }
    logger.info { "Correct Answers: $correctAnswers" }
    logger.info { "Challenges: $challenges" }
    logger.info { "Invocations: $invocations" }

    redis.multi().also { tx ->
      if (previousResetId.isNotEmpty()) {
        tx.del(userIdPasswordResetKey)
        tx.del(passwordResetKey(previousResetId))
      }

      tx.del(userEmailKey)
      tx.del(userInfoKey)
      tx.del(userClassesKey)

      correctAnswers.forEach { tx.del(it) }
      challenges.forEach { tx.del(it) }
      invocations.forEach { tx.del(it) }

      tx.exec()
    }
  }

  companion object : KLogging() {

    val gson = Gson()

    fun userIdByPrincipal(principal: UserPrincipal?): UserId? = principal?.let { UserId(principal.userId) }

    fun userEmailKey(email: String) = listOf(USER_EMAIL_KEY, email).joinToString(KEY_SEP)

    fun classDescKey(classCode: String) = listOf(CLASS_DESC_KEY, classCode).joinToString(KEY_SEP)

    // Value is a list of all enrolled students
    fun classCodeEnrollmentKey(classCode: String) = listOf(CLASS_CODE_KEY, classCode).joinToString(KEY_SEP)

    fun correctAnswersKey(userId: UserId?, browserSession: BrowserSession?, names: ChallengeNames) =
      userId?.correctAnswersKey(names) ?: browserSession?.correctAnswersKey(names) ?: ""

    fun correctAnswersKey(userId: UserId?,
                          browserSession: BrowserSession?,
                          languageName: String,
                          groupName: String,
                          challengeName: String) =
      userId?.correctAnswersKey(languageName, groupName, challengeName)
        ?: browserSession?.correctAnswersKey(languageName, groupName, challengeName)
        ?: ""

    fun challengeAnswersKey(userId: UserId?, browserSession: BrowserSession?, names: ChallengeNames) =
      userId?.challengeAnswersKey(names) ?: browserSession?.challengeAnswerKey(names) ?: ""

    fun challengeAnswersKey(userId: UserId?,
                            browserSession: BrowserSession?,
                            languageName: String,
                            groupName: String,
                            challengeName: String) =
      userId?.challengeAnswersKey(languageName, groupName, challengeName)
        ?: browserSession?.challengeAnswerKey(languageName, groupName, challengeName)
        ?: ""

    fun answerHistoryKey(userId: UserId?, browserSession: BrowserSession?, names: ChallengeNames, invocation: String) =
      userId?.answerHistoryKey(names, invocation) ?: browserSession?.answerHistoryKey(names, invocation) ?: ""

    // Maps resetId to username
    fun passwordResetKey(resetId: String) = listOf(RESET_KEY, resetId).joinToString(KEY_SEP)

    fun createUser(name: String, email: String, password: String, redis: Jedis): UserId {
      // The userName (email) is stored in a single KV pair, enabling changes to the userName
      // Three things are stored:
      // email -> userId
      // userId -> salt and sha256-encoded digest

      val userEmailKey = userEmailKey(email)
      val userId = UserId()
      val salt = newStringSalt()
      logger.info { "Created user $email ${userId.id}" }

      redis.multi().also { tx ->
        tx.set(userEmailKey, userId.id)
        tx.hset(userId.userInfoKey, mapOf(NAME_FIELD to name,
                                          EMAIL_FIELD to email,
                                          SALT_FIELD to salt,
                                          DIGEST_FIELD to password.sha256(salt),
                                          ENROLLED_CLASS_CODE_FIELD to "",
                                          ACTIVE_CLASS_CODE_FIELD to ""))
        tx.exec()
      }

      return userId
    }

    fun PipelineCall.saveAnswers(content: ReadingBatContent,
                                 redis: Jedis,
                                 names: ChallengeNames,
                                 compareMap: Map<String, String>,
                                 funcInfo: FunctionInfo,
                                 userResps: List<Map.Entry<String, List<String>>>,
                                 results: List<ChallengeResults>) {
      val principal = fetchPrincipal()
      val browserSession by lazy { call.sessions.get<BrowserSession>() }
      val userId = userIdByPrincipal(principal)
      val challengeAnswerKey = challengeAnswersKey(userId, browserSession, names)
      val correctAnswersKey = correctAnswersKey(userId, browserSession, names)
      val enrolledClassCode = userId?.fetchEnrolledClassCode(redis) ?: ""
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
          redis.hset(challengeAnswerKey, invocation, userResp)
        }
      }
      // Save the history of each answer on a per-invocation basis
      results
        .filter { it.answered }
        .forEach { result ->
          val answerHistoryKey = answerHistoryKey(userId, browserSession, names, result.invocation)
          if (answerHistoryKey.isNotEmpty()) {
            val history =
              gson.fromJson(redis[answerHistoryKey], ChallengeHistory::class.java)
                ?: ChallengeHistory(result.invocation)
            history.apply { if (result.correct) markCorrect() else markIncorrect(result.userResponse) }
            val json = gson.toJson(history)
            redis.set(answerHistoryKey, json)

            // Publish to challenge dashboard
            if (enrolledClassCode.isNotEmpty() && userId != null) {
              val browserInfo =
                DashboardInfo(content.maxHistoryLength, userId.id, complete, numCorrect, history)
              logger.info { "Publishing data $json" }
              redis.publish(enrolledClassCode, gson.toJson(browserInfo))
            }
          }
        }
    }

    fun isValidPrincipal(principal: UserPrincipal, redis: Jedis): Boolean {
      val userId = userIdByPrincipal(principal) ?: return false
      return redis.hlen(userId.userInfoKey) > 0
    }

    fun isValidEmail(email: String, redis: Jedis) = lookupUserIdByEmail(email, redis) != null

    fun lookupUserIdByEmail(email: String, redis: Jedis): UserId? {
      val userEmailKey = userEmailKey(email)
      val id = redis.get(userEmailKey) ?: ""
      return if (id.isNotEmpty()) UserId(id) else null
    }

    fun lookupDigestInfoByUserId(userId: UserId, redis: Jedis): Pair<String, String> {
      val salt = redis.hget(userId.userInfoKey, SALT_FIELD) ?: ""
      val digest = redis.hget(userId.userInfoKey, DIGEST_FIELD) ?: ""
      return salt to digest
    }
  }

  internal data class ChallengeAnswers(val id: String, val answers: MutableMap<String, String> = mutableMapOf())
}
