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

import com.github.pambrose.common.time.format
import com.github.pambrose.common.util.pluralize
import com.github.readingbat.common.CSSNames.INDENT_1EM
import com.github.readingbat.common.CSSNames.TD_PADDING
import com.github.readingbat.common.Endpoints.ADMIN_PREFS_ENDPOINT
import com.github.readingbat.common.FormFields.RETURN_PARAM
import com.github.readingbat.common.SessionActivites
import com.github.readingbat.common.User.Companion.toUser
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.pages.PageUtils.backLink
import com.github.readingbat.pages.PageUtils.bodyTitle
import com.github.readingbat.pages.PageUtils.headDefault
import com.github.readingbat.pages.PageUtils.loadStatusPageDisplay
import com.github.readingbat.server.BrowserSessions
import com.github.readingbat.server.Email
import com.github.readingbat.server.FullName
import com.github.readingbat.server.FullName.Companion.UNKNOWN_FULLNAME
import com.github.readingbat.server.GeoInfos
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.ServerRequests
import com.github.readingbat.server.ServerUtils.queryParam
import com.github.readingbat.server.Users
import com.github.readingbat.server.dateTimeExpr
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import mu.KLogging
import org.jetbrains.exposed.sql.Count
import org.jetbrains.exposed.sql.Max
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.jodatime.DateColumnType
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.joda.time.DateTimeZone.UTC
import kotlin.time.hours
import kotlin.time.measureTimedValue
import kotlin.time.milliseconds
import kotlin.time.minutes

internal object SessionsPage : KLogging() {

  fun PipelineCall.sessionsPage(content: ReadingBatContent) =
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
                td { +SessionActivites.activeSessions(1.minutes).toString() }
              }
              tr {
                td { +"Active users in the last 15 minutes: " }
                td { +SessionActivites.activeSessions(15.minutes).toString() }
              }
              tr {
                td { +"Active users in the last hour: " }
                td { +SessionActivites.activeSessions(1.hours).toString() }
              }
            }
          }

          val sessions = SessionActivites.allSessions().filter { it.requests > 1 }.sortedBy { it.age }

          h3 { +"${sessions.size} User Session".pluralize(sessions.size) }

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
                sessions
                  .forEach { session ->
                    tr {
                      val user = session.principal?.userId?.let { toUser(it, session.browserSession) }
                      val userDesc = user?.let { "${it.fullName} (${it.email})" } ?: "Not logged in"
                      td { +session.browserSession.id }
                      td { +userDesc }
                      td { +session.age.format(false) }
                      td { +session.requests.toString() }
                      td { +session.remoteHost.remoteHost }
                      td { +session.remoteHost.city }
                      td { +session.remoteHost.state }
                      td { +session.remoteHost.country }
                      td { +session.remoteHost.organization }
                      td { if ("://" in session.remoteHost.flagUrl) img { src = session.remoteHost.flagUrl } else +"" }
                      td { +session.userAgent }
                    }
                  }
              }
            }
          }

          class QueryInfo(val session_id: String,
                          val fullName: FullName,
                          val email: Email,
                          val ip: String,
                          val city: String,
                          val state: String,
                          val country: String,
                          val isp: String,
                          val flagUrl: String,
                          val userAgent: String,
                          val count: Long,
                          val maxDate: DateTime)

          val rows =
            transaction {
              //addLogger(KotlinLoggingSqlLogger)
              measureTimedValue {
                val session_id = BrowserSessions.session_id
                val fullName = Users.fullName
                val email = Users.email
                val ip = GeoInfos.ip
                val city = GeoInfos.city
                val state = GeoInfos.stateProv
                val country = GeoInfos.countryName
                val isp = GeoInfos.organization
                val flagUrl = GeoInfos.countryFlag
                val userAgent = ServerRequests.userAgent
                val count = Count(Users.id)
                val maxDate = Max(ServerRequests.created, DateColumnType(true))
                val created = ServerRequests.created

                (ServerRequests innerJoin BrowserSessions innerJoin Users innerJoin GeoInfos)
                  .slice(session_id, fullName, email, ip, city, state, country, isp, flagUrl, userAgent, count, maxDate)
                  .select { created greater dateTimeExpr("now() - interval '2 day'") }
                  .groupBy(session_id, fullName, email, ip, city, state, country, isp, flagUrl, userAgent)
                  .orderBy(maxDate, SortOrder.DESC)
                  .map { row ->
                    QueryInfo(row[session_id],
                              FullName(row[fullName]),
                              Email(row[email]),
                              row[ip],
                              row[city],
                              row[state],
                              row[country],
                              row[isp],
                              row[flagUrl],
                              row[userAgent],
                              row[count],
                              row[maxDate] ?: DateTime.now(UTC))
                  }
              }.let { (query, duration) ->
                logger.info { "User sessions query took ${duration}" }
                query
              }

            }

          h3 { +"${rows.size} User Session".pluralize(rows.size) }

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
                      td { +(if (row.fullName != UNKNOWN_FULLNAME) "${row.fullName} (${row.email})" else "Not logged in") }
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
