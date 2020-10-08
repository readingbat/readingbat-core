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

package com.github.readingbat.common

import com.github.readingbat.server.BrowserSessions
import com.github.readingbat.server.Email
import com.github.readingbat.server.FullName
import com.github.readingbat.server.GeoInfos
import com.github.readingbat.server.ServerRequests
import com.github.readingbat.server.Users
import com.pambrose.common.exposed.dateTimeExpr
import com.pambrose.common.exposed.get
import mu.KLogging
import org.jetbrains.exposed.sql.Count
import org.jetbrains.exposed.sql.Max
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.countDistinct
import org.jetbrains.exposed.sql.jodatime.DateColumnType
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.measureTimedValue

internal object SessionActivites : KLogging() {

  private val session_id = BrowserSessions.session_id
  private val fullName = Users.fullName
  private val email = Users.email
  private val ip = GeoInfos.ip
  private val city = GeoInfos.city
  private val state = GeoInfos.stateProv
  private val country = GeoInfos.countryName
  private val isp = GeoInfos.organization
  private val flagUrl = GeoInfos.countryFlag
  private val userAgent = ServerRequests.userAgent
  private val created = ServerRequests.created

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

  fun querySessions(dayCount: Int) =
    transaction {
      measureTimedValue {
        val count = Count(Users.id)
        val maxDate = Max(created, DateColumnType(true))
        val elems = arrayOf(session_id, fullName, email, ip, city, state, country, isp, flagUrl, userAgent)

        (ServerRequests innerJoin BrowserSessions innerJoin Users innerJoin GeoInfos)
          .slice(*elems, count, maxDate)
          .select { created greater dateTimeExpr("now() - interval '${min(dayCount, 14)} day'") }
          .groupBy(*elems)
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
                      row[maxDate] ?: DateTime.now(DateTimeZone.UTC))
          }
      }.let { (query, duration) ->
        logger.debug { "User sessions query took $duration" }
        query
      }
    }

  fun activeSessions(duration: Duration) =
    transaction {
      //addLogger(KotlinLoggingSqlLogger)
      measureTimedValue {
        val sessionRef = ServerRequests.sessionRef
        val created = ServerRequests.created
        ServerRequests
          .slice(sessionRef.countDistinct())
          .select { created greater dateTimeExpr("now() - interval '${duration.toLongMilliseconds()} milliseconds'") }
          .map { it[0] as Long }
          .first()
      }.let { (query, duration) ->
        logger.debug { "Active sessions query took $duration" }
        query
      }
    }
}