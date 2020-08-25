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

package com.github.readingbat.pages

import com.github.pambrose.common.util.pluralize
import com.github.readingbat.common.CSSNames.INDENT_1EM
import com.github.readingbat.common.CSSNames.TD_PADDING
import com.github.readingbat.common.Constants.RETURN_PATH
import com.github.readingbat.common.Endpoints.USER_PREFS_ENDPOINT
import com.github.readingbat.common.User.Companion.toUser
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.pages.PageUtils.backLink
import com.github.readingbat.pages.PageUtils.bodyTitle
import com.github.readingbat.pages.PageUtils.headDefault
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.ServerUtils.queryParam
import com.github.readingbat.server.SessionActivity
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.stream.createHTML
import kotlinx.html.table
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.tr
import redis.clients.jedis.Jedis
import kotlin.time.hours
import kotlin.time.minutes

internal object SessionsPage {

  fun PipelineCall.sessionsPage(content: ReadingBatContent, redis: Jedis) =
    createHTML()
      .html {
        head { headDefault(content) }

        body {
          bodyTitle()

          h2 { +"ReadingBat Sessions" }

          h3 { +"Active Users" }
          div(classes = INDENT_1EM) {
            table {
              tr {
                td { +"Active users in the last minute: " }
                td { +SessionActivity.activeSessions(1.minutes).toString() }
              }
              tr {
                td { +"Active users in the last 15 minutes: " }
                td { +SessionActivity.activeSessions(15.minutes).toString() }
              }
              tr {
                td { +"Active users in the last hour: " }
                td { +SessionActivity.activeSessions(1.hours).toString() }
              }
            }
          }

          val sessions = SessionActivity.allSessions().sortedBy { it.age }

          h3 { +"${sessions.size} User Session".pluralize(sessions.size) }

          div(classes = TD_PADDING) {
            div(classes = INDENT_1EM) {
              table {
                tr {
                  th { +"Session Id" }
                  th { +"User" }
                  th { +"Last activity" }
                  th { +"Remote Host" }
                  th { +"User Agent" }
                }
                sessions.forEach {
                  tr {
                    val user = it.principal?.userId?.toUser(it.browserSession)
                    val userDesc = user?.let { "${it.name(redis)} (${it.email(redis)})" } ?: "Not logged in"
                    td { +it.browserSession.id }
                    td { +userDesc }
                    td { +(it.age.toString()) }
                    td { +(it.remoteHost) }
                    td { +(it.userAgent) }
                  }
                }
              }
            }
          }

          backLink("$USER_PREFS_ENDPOINT?$RETURN_PATH=${queryParam(RETURN_PATH, "/")}")
        }
      }
}
