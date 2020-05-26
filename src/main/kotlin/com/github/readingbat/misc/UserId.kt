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
import com.github.readingbat.misc.KeyPrefixes.ANSWER_HISTORY
import com.github.readingbat.misc.KeyPrefixes.AUTH
import com.github.readingbat.misc.KeyPrefixes.CHALLENGE_ANSWERS
import com.github.readingbat.misc.KeyPrefixes.CORRECT_ANSWERS
import com.github.readingbat.misc.KeyPrefixes.PASSWD
import com.github.readingbat.misc.KeyPrefixes.RESET
import com.github.readingbat.misc.KeyPrefixes.SALT
import com.github.readingbat.misc.KeyPrefixes.USERID_RESET
import com.github.readingbat.misc.KeyPrefixes.USER_ID
import com.github.readingbat.posts.ChallengeHistory
import com.github.readingbat.posts.ChallengeResults
import com.google.gson.Gson
import mu.KLogging
import redis.clients.jedis.Jedis

internal class UserId(val id: String = randomId(25)) {
  fun saltKey() = "$SALT|$id"

  fun passwordKey() = "$PASSWD|$id"

  fun correctAnswersKey(languageName: String, groupName: String, challengeName: String) =
    listOf(CORRECT_ANSWERS, AUTH, id, languageName, groupName, challengeName).joinToString(sep)

  fun challengeKey(languageName: String, groupName: String, challengeName: String) =
    listOf(CHALLENGE_ANSWERS, AUTH, id, languageName, groupName, challengeName).joinToString(sep)

  fun argumentKey(languageName: String, groupName: String, challengeName: String, argument: String) =
    listOf(ANSWER_HISTORY, AUTH, id, languageName, groupName, challengeName, argument).joinToString(sep)

  fun userIdPasswordResetKey(username: String) = listOf(USERID_RESET, id).joinToString(sep)


  fun deleteUser(principal: UserPrincipal, redis: Jedis) {
    val userIdKey = userIdKey(principal.userId)
    val saltKey = saltKey()
    val passwordKey = passwordKey()
    val correctAnswers = redis.keys(correctAnswersKey("*", "*", "*"))
    val challenges = redis.keys(challengeKey("*", "*", "*"))
    val arguments = redis.keys(argumentKey("*", "*", "*", "*"))

    logger.info { "Deleting user: ${principal.userId}" }
    logger.info { "userIdKey: $userIdKey" }
    logger.info { "saltKey: $saltKey" }
    logger.info { "passwordKey: $passwordKey" }
    logger.info { "correctAnswers: $correctAnswers" }
    logger.info { "challenges: $challenges" }
    logger.info { "arguments: $arguments" }

    redis.multi().also { tx ->
      tx.del(userIdKey)
      tx.del(saltKey)
      tx.del(passwordKey)
      correctAnswers.forEach { tx.del(it) }
      challenges.forEach { tx.del(it) }
      arguments.forEach { tx.del(it) }
      tx.exec()
    }
  }

  companion object : KLogging() {

    private const val sep = "|"

    val gson = Gson()

    fun userIdKey(username: String) = "$USER_ID|$username"

    fun passwordResetKey(resetId: String) = listOf(RESET, resetId).joinToString(sep)

    fun createUser(username: String, password: String, redis: Jedis) {
      // The userName (email) is stored in only one KV pair, enabling changes to the userName
      // Three things are stored:
      // username -> userId
      // userId -> salt
      // userId -> sha256-encoded password

      val userIdKey = userIdKey(username)
      val userId = UserId()
      val salt = newStringSalt()
      logger.info { "Created user $username ${userId.id}" }

      redis.multi().also { tx ->
        tx.set(userIdKey, userId.id)
        tx.set(userId.saltKey(), salt)
        tx.set(userId.passwordKey(), password.sha256(salt))
        tx.exec()
      }
    }

    fun saveAnswers(principal: UserPrincipal?,
                    browserSession: BrowserSession?,
                    languageName: String,
                    groupName: String,
                    challengeName: String,
                    compareMap: Map<String, String>,
                    funcInfo: FunctionInfo,
                    userResps: List<Map.Entry<String, List<String>>>,
                    results: List<ChallengeResults>) =
      withRedisPool { redis ->
        val userId = lookupUserId(principal, redis)

        // Save if all answers were correct
        val correctAnswersKey =
          userId?.correctAnswersKey(languageName, groupName, challengeName)
            ?: browserSession?.correctAnswersKey(languageName, groupName, challengeName)
            ?: ""

        if (correctAnswersKey.isNotEmpty()) {
          val allCorrect = results.all { it.correct }
          redis?.set(correctAnswersKey, allCorrect.toString())
        }

        val challengeKey =
          userId?.challengeKey(languageName, groupName, challengeName)
            ?: browserSession?.challengeKey(languageName, groupName, challengeName)
            ?: ""

        if (redis != null && challengeKey.isNotEmpty()) {
          val answerMap = mutableMapOf<String, String>()
          userResps.indices.forEach { i ->
            val userResp =
              compareMap[CSSNames.userResp + i]?.trim() ?: throw InvalidConfigurationException("Missing user response")
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
              userId?.argumentKey(languageName, groupName, challengeName, result.arguments)
                ?: browserSession?.argumentKey(languageName, groupName, challengeName, result.arguments)
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

    fun isValidUsername(username: String) = lookupUserId(username) != null

    fun lookupUserId(username: String): UserId? = withRedisPool { redis -> lookupUserId(username, redis) }

    fun isValidUsername(principal: UserPrincipal?) = withRedisPool { redis -> isValidUsername(principal, redis) }

    fun isValidUsername(principal: UserPrincipal?, redis: Jedis?) = lookupUserId(principal, redis) != null

    fun lookupUserId(principal: UserPrincipal?, redis: Jedis?) =
      principal?.let { lookupUserId(it.userId, redis) }

    fun lookupUserId(username: String, redis: Jedis?): UserId? {
      val userIdKey = userIdKey(username)
      val id = redis?.get(userIdKey) ?: ""
      return if (id.isNotEmpty()) UserId(id) else null
    }

    fun lookupSaltAndDigest(userId: UserId, redis: Jedis?): Pair<String, String> {
      val saltKey = userId.saltKey()
      val passwordKey = userId.passwordKey()
      val salt = redis?.get(saltKey) ?: ""
      val digest = redis?.get(passwordKey) ?: ""
      return salt to digest
    }
  }

  internal data class ChallengeAnswers(val id: String, val answers: MutableMap<String, String> = mutableMapOf())
}
