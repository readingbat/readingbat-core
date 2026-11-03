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

package com.readingbat.pages

import com.pambrose.common.util.pluralize
import com.readingbat.common.Constants.UNKNOWN
import com.readingbat.common.Endpoints.ADMIN_PREFS_ENDPOINT
import com.readingbat.common.FormFields.DAYS_DEFAULT
import com.readingbat.common.FormFields.DAYS_PARAM
import com.readingbat.common.FormFields.RETURN_PARAM
import com.readingbat.common.SessionActivites.activeSessions
import com.readingbat.common.SessionActivites.querySessions
import com.readingbat.common.TwClasses
import com.readingbat.pages.PageUtils.backLink
import com.readingbat.pages.PageUtils.bodyTitle
import com.readingbat.pages.PageUtils.headDefault
import com.readingbat.pages.PageUtils.loadStatusPageDisplay
import com.readingbat.pages.PageUtils.rawHtml
import com.readingbat.server.FullName.Companion.UNKNOWN_FULLNAME
import com.readingbat.server.ServerUtils.queryParam
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
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.time.toJavaInstant

/**
 * Generates the active sessions page at `/sessions`.
 *
 * Displays active user counts across various time windows and a detailed table of
 * recent user sessions including login date, geolocation, and request counts.
 */
internal object SessionsPage {
  private val logger = KotlinLogging.logger {}
  private val loginDateFormat = DateTimeFormatter.ofPattern("MM/dd/yy HH:mm")
  private val zoneNameFormat = DateTimeFormatter.ofPattern("zzz", Locale.US)

  /** Formats [instant] as `MM/dd/yy HH:mm` in [zone]. */
  internal fun loginDateText(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    loginDateFormat.format(instant.toJavaInstant().atZone(zone))

  /** Joins [city], [state], and [country] with commas, leaving out blank and unknown parts. */
  internal fun locationText(city: String, state: String, country: String): String =
    [city, state, country].filter { it.isNotBlank() && it != UNKNOWN }.joinToString(", ")

  fun RoutingContext.sessionsPage() =
    createHTML()
      .html {
        head { headDefault() }

        body {
          bodyTitle()

          h2 { +"ReadingBat Sessions" }

          val activeUserCounts =
            [
              1.minutes to "minute",
              15.minutes to "15 minutes",
              1.hours to "hour",
              24.hours to "24 hours",
              7.days to "week",
              30.days to "month",
              365.days to "year",
            ].map { (duration, label) -> label to activeSessions(duration) }

          h3 { +"Active Users" }
          div(classes = TwClasses.INDENT_1EM) {
            table {
              activeUserCounts.forEach { (label, count) ->
                tr {
                  td { +"Active users in the last $label: " }
                  td { +count.toString() }
                }
              }
            }
          }

          val dayCount = queryParam(DAYS_PARAM, DAYS_DEFAULT).toInt()
          val rows = querySessions(dayCount)
          val sessions = "Session".pluralize(rows.size)
          val days = "Day".pluralize(dayCount)

          h3 { +"${rows.size} User $sessions in $dayCount $days" }

          div(classes = "tdPadding") {
            div(classes = TwClasses.INDENT_1EM) {
              table {
                tr {
                  th { +"Session Id" }
                  th { +"User" }
                  th { +"Date (${ZonedDateTime.now().format(zoneNameFormat)})" }
                  th { +"Requests" }
                  th { +"Location" }
                  th { +"" }
                }
                rows
                  .forEach { row ->
                    tr {
                      td { +row.sessionId }
                      td {
                        if (row.fullName != UNKNOWN_FULLNAME) {
                          +row.fullName.toString()
                          rawHtml("</br>")
                          +"(${row.email})"
                        } else {
                          +"Not logged in"
                        }
                      }
                      td { +loginDateText(row.loginDate) }
                      td { +row.count.toString() }
                      td { +locationText(row.city, row.state, row.country) }
                      td { if ("://" in row.flagUrl) img { src = row.flagUrl } else +"" }
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
