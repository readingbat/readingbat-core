/*
 * Copyright © 2026 Paul Ambrose (pambrose@mac.com)
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

package com.readingbat.posts

import com.readingbat.common.FormFields.ADMIN_ACTION_PARAM
import com.readingbat.common.FormFields.DELETE_ALL_DATA
import com.readingbat.common.Message
import com.readingbat.common.User
import com.readingbat.common.UserPrincipal
import com.readingbat.common.isNotAdminUser
import com.readingbat.common.isNotValidUser
import com.readingbat.dsl.ContentCaches.contentDslCache
import com.readingbat.dsl.ContentCaches.dirCache
import com.readingbat.dsl.ContentCaches.sourceCache
import com.readingbat.dsl.ReadingBatContent
import com.readingbat.pages.AdminPage.adminDataPage
import io.ktor.server.request.receiveParameters
import io.ktor.server.routing.RoutingContext
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.sessions

/**
 * Handles POST submissions from the admin data page, dispatching actions such as
 * clearing all cached content DSL, source, and directory caches.
 * Requires admin privileges.
 */
internal object AdminPost {
  private val mustBeLoggedIn = Message("Must be logged in for this function", true)
  private val mustBeSysAdmin = Message("Must be system admin for this function", true)
  private val invalidOption = Message("Invalid option", true)

  suspend fun RoutingContext.adminActions(
    content: ReadingBatContent,
    user: User?,
  ): String =
    when {
      user.isNotValidUser() -> {
        adminDataPage(content, user, mustBeLoggedIn)
      }

      user.isNotAdminUser() -> {
        adminDataPage(content, user, mustBeSysAdmin)
      }

      else -> {
        when (call.receiveParameters()[ADMIN_ACTION_PARAM] ?: "") {
          DELETE_ALL_DATA -> {
            val contentCnt = contentDslCache.size
            val sourceCnt = sourceCache.size
            val dirCnt = dirCache.size
            contentDslCache.clear()
            sourceCache.clear()
            dirCache.clear()
            call.sessions.clear<UserPrincipal>()
            adminDataPage(content, user, Message("$contentCnt/$sourceCnt/$dirCnt items deleted", false))
          }

          else -> {
            adminDataPage(content, user, msg = invalidOption)
          }
        }
      }
    }
}
