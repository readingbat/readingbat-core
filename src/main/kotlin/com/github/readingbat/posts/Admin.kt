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

import com.github.pambrose.common.redis.RedisUtils.withSuspendingRedisPool
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.Constants.DBMS_DOWN
import com.github.readingbat.misc.FormFields.ADMIN_ACTION
import com.github.readingbat.misc.FormFields.DELETE_ALL_DATA
import com.github.readingbat.misc.UserPrincipal
import com.github.readingbat.pages.AdminPage.adminDataPage
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.fetchPrincipal
import io.ktor.application.call
import io.ktor.request.receiveParameters
import io.ktor.sessions.clear
import io.ktor.sessions.sessions

internal object Admin {

  suspend fun PipelineCall.adminActions(content: ReadingBatContent) =
    if (fetchPrincipal() == null)
      adminDataPage(content, "Must be logged in for this function")
    else
      withSuspendingRedisPool { redis ->
        if (redis == null) {
          adminDataPage(content, DBMS_DOWN)
        }
        else {
          val parameters = call.receiveParameters()
          when (parameters[ADMIN_ACTION] ?: "") {
            DELETE_ALL_DATA -> {
              val cnt = redis.keys("*")?.onEach { redis.del(it) }?.count() ?: 0
              call.sessions.clear<UserPrincipal>()
              adminDataPage(content, "$cnt items deleted", false)
            }
            else ->
              ""
          }
        }
      }
}


