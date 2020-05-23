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

package com.github.readingbat.posts

import com.github.pambrose.common.redis.RedisUtils.withRedisPool
import com.github.pambrose.common.response.redirectTo
import com.github.pambrose.common.response.respondWith
import com.github.pambrose.common.util.isNotValidEmail
import com.github.pambrose.common.util.newStringSalt
import com.github.pambrose.common.util.sha256
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.Constants.RETURN_PATH
import com.github.readingbat.misc.FormFields.PASSWORD
import com.github.readingbat.misc.FormFields.USERNAME
import com.github.readingbat.misc.UserId
import com.github.readingbat.misc.userIdKey
import com.github.readingbat.pages.createAccountPage
import com.github.readingbat.posts.Messages.CLEVER_PASSWORD
import com.github.readingbat.posts.Messages.EMPTY_EMAIL
import com.github.readingbat.posts.Messages.EMPTY_PASWORD
import com.github.readingbat.posts.Messages.INVALID_EMAIL
import com.github.readingbat.posts.Messages.PASSWORD_TOO_SHORT
import com.github.readingbat.server.PipelineCall
import com.google.common.util.concurrent.RateLimiter
import io.ktor.application.call
import io.ktor.request.receiveParameters
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

private object Messages {
  const val EMPTY_EMAIL = "Empty email value"
  const val INVALID_EMAIL = "Invalid email value"
  const val EMPTY_PASWORD = "Empty password value"
  const val PASSWORD_TOO_SHORT = "Password value too short (must have at least 6 characters)"
  const val CLEVER_PASSWORD = "Surely you can come up with a more clever password"
}

internal suspend fun PipelineCall.createAccount(content: ReadingBatContent) {
  val parameters = call.receiveParameters()
  val username = parameters[USERNAME] ?: ""
  val password = parameters[PASSWORD] ?: ""
  val returnPath = parameters[RETURN_PATH] ?: "/"
  logger.debug { "Return path = $returnPath" }

  when {
    username.isBlank() -> respondWith { createAccountPage(content, "", EMPTY_EMAIL, returnPath) }
    username.isNotValidEmail() -> respondWith { createAccountPage(content, username, INVALID_EMAIL, returnPath) }
    password.isBlank() -> respondWith { createAccountPage(content, username, EMPTY_PASWORD, returnPath) }
    password.length < 6 -> respondWith { createAccountPage(content, username, PASSWORD_TOO_SHORT, returnPath) }
    password == "password" -> respondWith { createAccountPage(content, username, CLEVER_PASSWORD, returnPath) }
    else -> {
      withRedisPool { redis ->
        runBlocking {
          if (redis == null) {
            redirectTo { returnPath }
          }
          else {
            // Check if username already exists
            val userIdKey = userIdKey(username)
            if (redis.exists(userIdKey)) {
              respondWith { createAccountPage(content, "", "Username already exists: $username", returnPath) }
            }
            else {
              // The userName (email) is stored in only one KV pair, enabling changes to the userName
              // Three things are stored:
              // username -> userId
              // userId -> salt
              // userId -> sha256-encoded password

              redis.multi().also { tx ->
                val userId = UserId()
                val salt = newStringSalt()
                val digest = password.sha256(salt)

                logger.info { "Created user $username ${userId.id} $salt $digest" }

                tx.set(userIdKey, userId.id)
                tx.set(userId.saltKey(), salt)
                tx.set(userId.passwordKey(), digest)
                tx.exec()
              }

              createAccountLimiter.acquire() // may wait

              redirectTo { returnPath }
            }
          }
        }
      }
    }
  }
}

private val createAccountLimiter = RateLimiter.create(2.0) // rate 2.0 is "2 permits per second"
