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
import com.github.readingbat.misc.KeyPrefixes.ANSWER_HISTORY
import com.github.readingbat.misc.KeyPrefixes.AUTH
import com.github.readingbat.misc.KeyPrefixes.CHALLENGE_ANSWERS
import com.github.readingbat.misc.KeyPrefixes.CORRECT_ANSWERS
import com.github.readingbat.misc.KeyPrefixes.PASSWD
import com.github.readingbat.misc.KeyPrefixes.SALT
import com.github.readingbat.misc.KeyPrefixes.USER_ID
import mu.KLogging
import redis.clients.jedis.Jedis

internal class UserId(val id: String = randomId(25)) {
  fun saltKey() = "$SALT|$id"

  fun passwordKey() = "$PASSWD|$id"

  fun correctAnswersKey(languageName: String, groupName: String, challengeName: String) =
    listOf(CORRECT_ANSWERS, AUTH, id, languageName, groupName, challengeName).joinToString("|")

  fun challengeKey(languageName: String, groupName: String, challengeName: String) =
    listOf(CHALLENGE_ANSWERS, AUTH, id, languageName, groupName, challengeName).joinToString("|")

  fun argumentKey(languageName: String, groupName: String, challengeName: String, argument: String) =
    listOf(ANSWER_HISTORY, AUTH, id, languageName, groupName, challengeName, argument).joinToString("|")

  fun delete(principal: UserPrincipal, redis: Jedis) {
    val userIdKey = userIdKey(principal.userId)
    val saltKey = saltKey()
    val passwordKey = passwordKey()
    val correctAnswers = redis.keys(correctAnswersKey("*", "*", "*"))
    val challenges = redis.keys(challengeKey("*", "*", "*"))
    val arguments = redis.keys(argumentKey("*", "*", "*", "*"))

    println(userIdKey)
    println(saltKey)
    println(passwordKey)
    println(correctAnswers)
    println(challenges)
    println(arguments)
    /*
    redis.multi().also { tx ->
      tx.del(userIdKey)
      tx.del(saltKey)
      tx.del(passwordKey)
      correctAnswers.forEach { tx.del(it) }
      challenges.forEach { tx.del(it) }
      arguments.forEach { tx.del(it) }
      tx.exec()
    }

     */
  }

  companion object : KLogging() {

    fun userIdKey(username: String) = "$USER_ID|$username"

    fun createUser(username: String, password: String, redis: Jedis) {
      // The userName (email) is stored in only one KV pair, enabling changes to the userName
      // Three things are stored:
      // username -> userId
      // userId -> salt
      // userId -> sha256-encoded password

      val userIdKey = userIdKey(username)
      val userId = UserId()
      logger.info { "Created user $username ${userId.id} " }

      redis.multi().also { tx ->
        tx.set(userIdKey, userId.id)
        tx.set(userId.saltKey(), newStringSalt())
        tx.set(userId.passwordKey(), password.sha256(newStringSalt()))
        tx.exec()
      }

    }

    fun lookupUserId(username: String, redis: Jedis?): UserId? {
      val userIdKey = userIdKey(username)
      val id = redis?.get(userIdKey) ?: ""
      return if (id.isNotEmpty()) UserId(id) else null
    }

    fun isValidUserId(principal: UserPrincipal?, redis: Jedis?) = lookupUserId(principal, redis) != null

    fun lookupUserId(principal: UserPrincipal?, redis: Jedis?) =
      principal?.let {
        val userIdKey = userIdKey(it.userId)
        val id = redis?.get(userIdKey) ?: ""
        if (id.isNotEmpty()) UserId(id) else null
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
