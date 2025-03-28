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

package com.github.readingbat.pages

import com.github.pambrose.common.time.format
import com.github.pambrose.common.util.pluralize
import com.github.readingbat.common.CssNames.INDENT_1EM
import com.github.readingbat.common.CssNames.TD_PADDING
import com.github.readingbat.common.Endpoints.ADMIN_PREFS_ENDPOINT
import com.github.readingbat.common.FormFields.DAYS_DEFAULT
import com.github.readingbat.common.FormFields.DAYS_PARAM
import com.github.readingbat.common.FormFields.RETURN_PARAM
import com.github.readingbat.common.SessionActivites.activeSessions
import com.github.readingbat.common.SessionActivites.querySessions
import com.github.readingbat.pages.PageUtils.backLink
import com.github.readingbat.pages.PageUtils.bodyTitle
import com.github.readingbat.pages.PageUtils.headDefault
import com.github.readingbat.pages.PageUtils.loadStatusPageDisplay
import com.github.readingbat.pages.PageUtils.rawHtml
import com.github.readingbat.server.FullName.Companion.UNKNOWN_FULLNAME
import com.github.readingbat.server.ServerUtils.queryParam
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.routing.RoutingContext
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.img
import kotlinx.html.stream.createHTML
import kotlinx.html.table
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.tr
import org.joda.time.DateTime
import org.joda.time.DateTimeZone.UTC
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

internal object SessionsPage {
  private val logger = KotlinLogging.logger {}

  fun RoutingContext.sessionsPage() =
    createHTML()
      .html {
        head { headDefault() }

        body {
          bodyTitle()

          h2 { +"ReadingBat Sessions" }

          h3 { +"Active Users" }
          div(classes = INDENT_1EM) {
            table {
              tr {
                td { +"Active users in the last minute: " }
                td { +activeSessions(1.minutes).toString() }
              }
              tr {
                td { +"Active users in the last 15 minutes: " }
                td { +activeSessions(15.minutes).toString() }
              }
              tr {
                td { +"Active users in the last hour: " }
                td { +activeSessions(1.hours).toString() }
              }
              tr {
                td { +"Active users in the last 24 hours: " }
                td { +activeSessions(24.hours).toString() }
              }
              tr {
                td { +"Active users in the last week: " }
                td { +activeSessions(7.days).toString() }
              }
            }
          }

          val dayCount = queryParam(DAYS_PARAM, DAYS_DEFAULT).toInt()
          val rows = querySessions(dayCount)
          val sessions = "Session".pluralize(rows.size)
          val days = "Day".pluralize(dayCount)

          h3 { +"${rows.size} User $sessions in $dayCount $days" }

          div(classes = TD_PADDING) {
            div(classes = INDENT_1EM) {
              table {
                tr {
                  th { +"Session Id" }
                  th { +"User" }
                  th { +"Last Activity" }
                  th { +"Requests" }
                  th { +"Remote Host" }
                  th { +"City" }
                  th { +"State" }
                  th { +"Country" }
                  th { +"ISP" }
                  th { +"" }
                  th { +"User Agent" }
                }
                val now = DateTime.now(UTC)
                rows
                  .forEach { row ->
                    tr {
                      td { +row.session_id }
                      td {
                        if (row.fullName != UNKNOWN_FULLNAME) {
                          +row.fullName.toString()
                          rawHtml("</br>")
                          +"(${row.email})"
                        } else {
                          +"Not logged in"
                        }
                      }
                      td { +(now.millis - row.maxDate.millis).milliseconds.format() }
                      td { +row.count.toString() }
                      td { +row.ip }
                      td { +row.city }
                      td { +row.state }
                      td { +row.country }
                      td { +row.isp }
                      td { if ("://" in row.flagUrl) img { src = row.flagUrl } else +"" }
                      td { +row.userAgent }
                    }
                  }
              }
            }
          }

          backLink("$ADMIN_PREFS_ENDPOINT?$RETURN_PARAM=${queryParam(RETURN_PARAM, "/")}")
          loadStatusPageDisplay()
        }
      }
}
