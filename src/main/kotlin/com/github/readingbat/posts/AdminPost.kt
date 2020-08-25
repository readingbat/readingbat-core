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

import com.github.readingbat.common.FormFields.ADMIN_ACTION
import com.github.readingbat.common.FormFields.DELETE_ALL_DATA
import com.github.readingbat.common.Message
import com.github.readingbat.common.RedisRoutines.scanKeys
import com.github.readingbat.common.UserPrincipal
import com.github.readingbat.dsl.isProduction
import com.github.readingbat.pages.AdminPage.adminDataPage
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.ReadingBatContent
import com.github.readingbat.server.User
import com.github.readingbat.server.isValidUser
import io.ktor.application.*
import io.ktor.request.*
import io.ktor.sessions.*
import redis.clients.jedis.Jedis

internal object AdminPost {

  suspend fun PipelineCall.adminActions(content: ReadingBatContent, user: User?, redis: Jedis): String {
    return when {
      isProduction() && !user.isValidUser(redis) ->
        adminDataPage(content,
                      user,
                      redis = redis,
                      msg = Message("Must be logged in for this function", true))
      isProduction() && user?.email(redis)?.value != "pambrose@mac.com" ->
        adminDataPage(content, user, redis = redis, msg = Message("Must be system admin for this function", true))
      else -> {
        val parameters = call.receiveParameters()
        when (parameters[ADMIN_ACTION] ?: "") {
          DELETE_ALL_DATA -> {
            val cnt = redis.scanKeys("*").onEach { redis.del(it) }.count()
            call.sessions.clear<UserPrincipal>()
            adminDataPage(content, user, redis, Message("$cnt items deleted", false))
          }
          else ->
            adminDataPage(content, user, redis = redis, msg = Message("Invalid option", true))
        }
      }
    }
  }
}