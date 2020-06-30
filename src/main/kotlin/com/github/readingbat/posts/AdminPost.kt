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

import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.FormFields.ADMIN_ACTION
import com.github.readingbat.misc.FormFields.DELETE_ALL_DATA
import com.github.readingbat.misc.Message
import com.github.readingbat.misc.User
import com.github.readingbat.misc.UserPrincipal
import com.github.readingbat.misc.isValidUser
import com.github.readingbat.pages.AdminPage.adminDataPage
import com.github.readingbat.server.PipelineCall
import io.ktor.application.call
import io.ktor.request.receiveParameters
import io.ktor.sessions.clear
import io.ktor.sessions.sessions
import redis.clients.jedis.Jedis

internal object AdminPost {

  suspend fun PipelineCall.adminActions(content: ReadingBatContent, user: User?, redis: Jedis): String {
    return when {
      content.production && !user.isValidUser(redis) -> adminDataPage(content,
                                                                      user,
                                                                      redis = redis,
                                                                      msg = Message("Must be logged in for this function",
                                                                                    true))
      content.production && user?.email(redis)?.value != "pambrose@mac.com" -> {
        adminDataPage(content, user, redis = redis, msg = Message("Must be system admin for this function", true))
      }
      else -> {
        val parameters = call.receiveParameters()
        when (parameters[ADMIN_ACTION] ?: "") {
          DELETE_ALL_DATA -> {
            val cnt = redis.keys("*")?.onEach { redis.del(it) }?.count() ?: 0
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