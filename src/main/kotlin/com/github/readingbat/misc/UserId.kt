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

import com.github.pambrose.common.redis.RedisUtils.withRedisPool
import com.github.pambrose.common.util.newStringSalt
import com.github.pambrose.common.util.randomId
import com.github.pambrose.common.util.sha256
import com.github.readingbat.dsl.FunctionInfo
import com.github.readingbat.dsl.InvalidConfigurationException
import com.github.readingbat.misc.Constants.RESP
import com.github.readingbat.misc.RedisConstants.ANSWER_HISTORY_KEY
import com.github.readingbat.misc.RedisConstants.AUTH_KEY
import com.github.readingbat.misc.RedisConstants.CHALLENGE_ANSWERS_KEY
import com.github.readingbat.misc.RedisConstants.CORRECT_ANSWERS_KEY
import com.github.readingbat.misc.RedisConstants.DIGEST_FIELD
import com.github.readingbat.misc.RedisConstants.DIGEST_KEY
import com.github.readingbat.misc.RedisConstants.RESET_KEY
import com.github.readingbat.misc.RedisConstants.SALT_FIELD
import com.github.readingbat.misc.RedisConstants.USERID_RESET_KEY
import com.github.readingbat.misc.RedisConstants.USER_ID_KEY
import com.github.readingbat.posts.ChallengeHistory
import com.github.readingbat.posts.ChallengeNames
import com.github.readingbat.posts.ChallengeResults
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.fetchPrincipal
import com.google.gson.Gson
import io.ktor.application.call
import io.ktor.sessions.get
import io.ktor.sessions.sessions
import mu.KLogging
import redis.clients.jedis.Jedis

internal class UserId(val id: String = randomId(25)) {

  fun digestKey() = "$DIGEST_KEY|$id"

  fun correctAnswersKey(names: ChallengeNames) =
    correctAnswersKey(names.languageName, names.groupName, names.challengeName)

  fun correctAnswersKey(languageName: String, groupName: String, challengeName: String) =
    listOf(CORRECT_ANSWERS_KEY, AUTH_KEY, id, languageName, groupName, challengeName).joinToString(sep)

  fun challengeKey(names: ChallengeNames) =
    challengeKey(names.languageName, names.groupName, names.challengeName)

  fun challengeKey(languageName: String, groupName: String, challengeName: String) =
    listOf(CHALLENGE_ANSWERS_KEY, AUTH_KEY, id, languageName, groupName, challengeName).joinToString(sep)

  fun argumentKey(names: ChallengeNames, argument: String) =
    argumentKey(names.languageName, names.groupName, names.challengeName, argument)

  fun argumentKey(languageName: String, groupName: String, challengeName: String, argument: String) =
    listOf(ANSWER_HISTORY_KEY, AUTH_KEY, id, languageName, groupName, challengeName, argument).joinToString(sep)

  // This key maps to a reset_id
  fun userIdPasswordResetKey() = listOf(USERID_RESET_KEY, id).joinToString(sep)

  fun deleteUser(principal: UserPrincipal, redis: Jedis) {
    val userIdKey = userIdKey(principal.userId)
    val digestKey = digestKey()
    val correctAnswers = redis.keys(correctAnswersKey("*", "*", "*"))
    val challenges = redis.keys(challengeKey("*", "*", "*"))
    val arguments = redis.keys(argumentKey("*", "*", "*", "*"))

    val userIdPasswordResetKey = userIdPasswordResetKey()
    val previousResetId = redis.get(userIdPasswordResetKey) ?: ""

    logger.info { "Deleting user: ${principal.userId}" }
    logger.info { "userIdKey: $userIdKey" }
    logger.info { "digestKey: $digestKey" }
    logger.info { "correctAnswers: $correctAnswers" }
    logger.info { "challenges: $challenges" }
    logger.info { "arguments: $arguments" }

    redis.multi().also { tx ->
      if (previousResetId.isNotEmpty()) {
        tx.del(userIdPasswordResetKey)
        tx.del(passwordResetKey(previousResetId))
      }

      tx.del(userIdKey)
      //tx.hdel(digestKey, SALT_FIELD, DIGEST_FIELD)
      tx.del(digestKey)

      correctAnswers.forEach { tx.del(it) }
      challenges.forEach { tx.del(it) }
      arguments.forEach { tx.del(it) }

      tx.exec()
    }
  }

  companion object : KLogging() {

    private const val sep = "|"

    val gson = Gson()

    fun userIdKey(username: String) = "$USER_ID_KEY|$username"

    // Maps resetId to username
    fun passwordResetKey(resetId: String) = listOf(RESET_KEY, resetId).joinToString(sep)

    fun createUser(username: String, password: String, redis: Jedis) {
      // The userName (email) is stored in a single KV pair, enabling changes to the userName
      // Three things are stored:
      // username -> userId
      // userId -> salt and sha256-encoded digest

      val userIdKey = userIdKey(username)
      val userId = UserId()
      val salt = newStringSalt()
      logger.info { "Created user $username ${userId.id}" }

      redis.multi().also { tx ->
        tx.set(userIdKey, userId.id)
        tx.hset(userId.digestKey(), mapOf(SALT_FIELD to salt, DIGEST_FIELD to password.sha256(salt)))
        tx.exec()
      }
    }

    fun PipelineCall.saveAnswers(names: ChallengeNames,
                                 compareMap: Map<String, String>,
                                 funcInfo: FunctionInfo,
                                 userResps: List<Map.Entry<String, List<String>>>,
                                 results: List<ChallengeResults>) =
      withRedisPool { redis ->
        val principal = fetchPrincipal()
        val browserSession by lazy { call.sessions.get<BrowserSession>() }
        val userId = lookupPrincipal(principal, redis)

        // Save if all answers were correct
        val correctAnswersKey = userId?.correctAnswersKey(names) ?: browserSession?.correctAnswersKey(names) ?: ""

        if (correctAnswersKey.isNotEmpty()) {
          val allCorrect = results.all { it.correct }
          redis?.set(correctAnswersKey, allCorrect.toString())
        }

        val challengeKey = userId?.challengeKey(names) ?: browserSession?.challengeKey(names) ?: ""

        if (redis != null && challengeKey.isNotEmpty()) {
          val answerMap = mutableMapOf<String, String>()
          userResps.indices.forEach { i ->
            val userResp =
              compareMap[RESP + i]?.trim() ?: throw InvalidConfigurationException("Missing user response")
            if (userResp.isNotEmpty()) {
              val argumentKey = funcInfo.arguments[i]
              answerMap[argumentKey] = userResp
            }
          }

          answerMap.forEach { (args, userResp) ->
            redis.hset(challengeKey, args, userResp)
            redis.publish("channel", userResp)
          }
        }

        // Save the history of each answer on a per-arguments basis
        results
          .filter { it.answered }
          .forEach { result ->
            val argumentKey =
              userId?.argumentKey(names, result.arguments)
                ?: browserSession?.argumentKey(names, result.arguments)
                ?: ""

            if (redis != null && argumentKey.isNotEmpty()) {
              val history =
                gson.fromJson(redis[argumentKey], ChallengeHistory::class.java) ?: ChallengeHistory(
                  result.arguments)
              logger.debug { "Before: $history" }
              history.apply { if (result.correct) markCorrect() else markIncorrect(result.userResponse) }
              logger.debug { "After: $history" }
              redis.set(argumentKey, gson.toJson(history))
            }
          }
      }

    fun isValidUsername(username: String) = lookupUsername(username) != null

    fun isValidPrincipal(principal: UserPrincipal?) = withRedisPool { redis -> isValidPrincipal(principal, redis) }

    fun isValidPrincipal(principal: UserPrincipal?, redis: Jedis?) = lookupPrincipal(principal, redis) != null

    fun lookupPrincipal(principal: UserPrincipal?, redis: Jedis?) =
      principal?.let { lookupUsername(it.userId, redis) }

    fun lookupUsername(username: String): UserId? = withRedisPool { redis -> lookupUsername(username, redis) }

    fun lookupUsername(username: String, redis: Jedis?): UserId? {
      val userIdKey = userIdKey(username)
      val id = redis?.get(userIdKey) ?: ""
      return if (id.isNotEmpty()) UserId(id) else null
    }

    fun lookupUserId(userId: UserId, redis: Jedis?): Pair<String, String> {
      val digestKey = userId.digestKey()
      val salt = redis?.hget(digestKey, SALT_FIELD) ?: ""
      val digest = redis?.hget(digestKey, DIGEST_FIELD) ?: ""
      return salt to digest
    }
  }

  internal data class ChallengeAnswers(val id: String, val answers: MutableMap<String, String> = mutableMapOf())
}
