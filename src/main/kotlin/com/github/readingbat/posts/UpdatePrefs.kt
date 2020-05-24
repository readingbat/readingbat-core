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
import com.github.readingbat.misc.Constants.RETURN_PATH
import com.github.readingbat.misc.FormFields
import com.github.readingbat.misc.FormFields.PREF_ACTION
import com.github.readingbat.misc.FormFields.UPDATE_PASSWORD
import com.github.readingbat.misc.UserId
import com.github.readingbat.misc.UserId.Companion.lookupUserId
import com.github.readingbat.pages.prefsPage
import com.github.readingbat.pages.requestLogInPage
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.fetchPrincipal
import io.ktor.application.call
import io.ktor.request.receiveParameters
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

internal suspend fun PipelineCall.changePrefs(content: ReadingBatContent): String {
  val parameters = call.receiveParameters()
  val principal = fetchPrincipal()

  return withRedisPool { redis ->
    val returnPath = parameters[RETURN_PATH] ?: "/"

    logger.debug { "Return path = $returnPath" }

    if (redis == null) {
      ""
    }
    else {
      val userId = lookupUserId(principal, redis)

      if (userId == null) {
        requestLogInPage(content)
      }
      else {
        val action = parameters[PREF_ACTION] ?: ""
        if (action == UPDATE_PASSWORD) {
          val currPassword = parameters[FormFields.CURR_PASSWORD] ?: ""
          val newPassword = parameters[FormFields.NEW_PASSWORD] ?: ""
          val passwordError = checkPassword(newPassword)

          val msg =
            if (passwordError.isNotEmpty()) {
              passwordError to true
            }
            else {
              val (salt, digest) = UserId.lookupSaltAndDigest(userId, redis)
              if (salt.isNotEmpty() && digest.isNotEmpty() && digest == currPassword.sha256(salt)) {
                val newDigest = newPassword.sha256(salt)
                redis.set(userId.passwordKey(), newDigest)
                "Password changed" to false
              }
              else {
                "Incorrect current password" to true
              }
            }
          prefsPage(content, msg.first, msg.second)
        }
        else {
          ""
        }
      }
    }
  }
}