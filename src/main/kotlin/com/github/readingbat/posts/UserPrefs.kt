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
import com.github.pambrose.common.util.sha256
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.Constants.DBMS_DOWN
import com.github.readingbat.misc.Dashboards.enrollIntoClass
import com.github.readingbat.misc.FormFields.CLASS_CODE
import com.github.readingbat.misc.FormFields.CONFIRM_PASSWORD
import com.github.readingbat.misc.FormFields.CURR_PASSWORD
import com.github.readingbat.misc.FormFields.DELETE_ACCOUNT
import com.github.readingbat.misc.FormFields.JOIN_CLASS
import com.github.readingbat.misc.FormFields.NEW_PASSWORD
import com.github.readingbat.misc.FormFields.UPDATE_PASSWORD
import com.github.readingbat.misc.FormFields.USER_PREFS_ACTION
import com.github.readingbat.misc.RedisConstants.DIGEST_FIELD
import com.github.readingbat.misc.UserId
import com.github.readingbat.misc.UserId.Companion.lookupPrincipal
import com.github.readingbat.misc.UserPrincipal
import com.github.readingbat.pages.UserPrefsPage.requestLogInPage
import com.github.readingbat.pages.UserPrefsPage.userPrefsPage
import com.github.readingbat.posts.CreateAccount.checkPassword
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.ServerUtils.fetchPrincipal
import io.ktor.application.call
import io.ktor.request.receiveParameters
import io.ktor.sessions.clear
import io.ktor.sessions.sessions
import mu.KLogging
import redis.clients.jedis.exceptions.JedisException

internal object UserPrefs : KLogging() {

  suspend fun PipelineCall.userPrefs(content: ReadingBatContent): String {
    val parameters = call.receiveParameters()
    val principal = fetchPrincipal()

    return withRedisPool { redis ->
      if (redis == null) {
        userPrefsPage(content, DBMS_DOWN, true)
      }
      else {
        val userId = lookupPrincipal(principal, redis)

        if (userId == null || principal == null) {
          requestLogInPage(content)
        }
        else {
          when (parameters[USER_PREFS_ACTION] ?: "") {
            UPDATE_PASSWORD -> {
              val currPassword = parameters[CURR_PASSWORD] ?: ""
              val newPassword = parameters[NEW_PASSWORD] ?: ""
              val confirmPassword = parameters[CONFIRM_PASSWORD] ?: ""
              val passwordError = checkPassword(newPassword, confirmPassword)

              val msg =
                if (passwordError.isNotEmpty()) {
                  passwordError to true
                }
                else {
                  val (salt, digest) = UserId.lookupUserId(userId, redis)
                  if (salt.isNotEmpty() && digest.isNotEmpty() && digest == currPassword.sha256(salt)) {
                    val newDigest = newPassword.sha256(salt)
                    redis.hset(userId.userInfoKey, DIGEST_FIELD, newDigest)
                    "Password changed" to false
                  }
                  else {
                    "Incorrect current password" to true
                  }
                }
              userPrefsPage(content, msg.first, msg.second)
            }

            JOIN_CLASS -> {
              val classCode = parameters[CLASS_CODE] ?: ""
              try {
                userId.enrollIntoClass(classCode)
                userPrefsPage(content, "Enrolled in class $classCode", false)
              } catch (e: JedisException) {
                logger.info { e }
                userPrefsPage(content,
                              "Unable to enroll in class [${e.message ?: ""}]",
                              true,
                              defaultClassCode = classCode)
              }
            }

            DELETE_ACCOUNT -> {
              logger.info { "Deleting user ${principal.userId}" }
              userId.deleteUser(principal, redis)
              call.sessions.clear<UserPrincipal>()
              requestLogInPage(content, false, "User ${principal.userId} deleted")
            }

            else -> {
              ""
            }
          }
        }
      }
    }
  }
}