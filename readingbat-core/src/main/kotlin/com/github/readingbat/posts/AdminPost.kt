/*
 * Copyright © 2023 Paul Ambrose (pambrose@mac.com)
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

import com.github.pambrose.common.redis.RedisUtils.scanKeys
import com.github.readingbat.common.FormFields.ADMIN_ACTION_PARAM
import com.github.readingbat.common.FormFields.DELETE_ALL_DATA
import com.github.readingbat.common.Message
import com.github.readingbat.common.User
import com.github.readingbat.common.UserPrincipal
import com.github.readingbat.common.isNotAdminUser
import com.github.readingbat.common.isNotValidUser
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.dsl.isProduction
import com.github.readingbat.pages.AdminPage.adminDataPage
import com.github.readingbat.server.PipelineCall
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.sessions.*
import redis.clients.jedis.Jedis

internal object AdminPost {
  private val mustBeLoggedIn = Message("Must be logged in for this function", true)
  private val mustBeSysAdmin = Message("Must be system admin for this function", true)
  private val invalidOption = Message("Invalid option", true)

  suspend fun PipelineCall.adminActions(
    content: ReadingBatContent,
    user: User?,
    redis: Jedis,
  ): String =
    when {
      isProduction() && user.isNotValidUser() -> adminDataPage(content, user, redis = redis, msg = mustBeLoggedIn)
      isProduction() && user.isNotAdminUser() -> adminDataPage(content, user, redis, mustBeSysAdmin)
      else -> {
        when (call.receiveParameters()[ADMIN_ACTION_PARAM] ?: "") {
          DELETE_ALL_DATA -> {
            val cnt = redis.scanKeys("*").onEach { redis.del(it) }.count()
            call.sessions.clear<UserPrincipal>()
            adminDataPage(content, user, redis, Message("$cnt items deleted", false))
          }

          else -> adminDataPage(content, user, redis = redis, msg = invalidOption)
        }
      }
    }
}
