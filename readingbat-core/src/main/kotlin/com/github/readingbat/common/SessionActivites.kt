/*
 * Copyright © 2021 Paul Ambrose (pambrose@mac.com)
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

import com.github.readingbat.dsl.isDbmsEnabled
import com.github.readingbat.server.BrowserSessionsTable
import com.github.readingbat.server.Email
import com.github.readingbat.server.FullName
import com.github.readingbat.server.GeoInfosTable
import com.github.readingbat.server.ServerRequestsTable
import com.github.readingbat.server.UsersTable
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

  private val session_id = BrowserSessionsTable.sessionId
  private val fullName = UsersTable.fullName
  private val email = UsersTable.email
  private val ip = GeoInfosTable.ip
  private val city = GeoInfosTable.city
  private val state = GeoInfosTable.stateProv
  private val country = GeoInfosTable.countryName
  private val isp = GeoInfosTable.organization
  private val flagUrl = GeoInfosTable.countryFlag
  private val userAgent = ServerRequestsTable.userAgent
  private val created = ServerRequestsTable.created

  class QueryInfo(
    val session_id: String,
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
    val maxDate: DateTime
  )

  fun querySessions(dayCount: Int) =
    transaction {
      measureTimedValue {
        val count = Count(UsersTable.id)
        val maxDate = Max(created, DateColumnType(true))
        val elems = arrayOf(fullName, email, ip, city, state, country, isp, flagUrl, userAgent)

        (ServerRequestsTable innerJoin BrowserSessionsTable innerJoin UsersTable innerJoin GeoInfosTable)
          .slice(session_id, *elems, count, maxDate)
          .select { created greater dateTimeExpr("now() - interval '${min(dayCount, 14)} day'") }
          .groupBy(*(arrayOf(session_id) + elems))
          .orderBy(maxDate, SortOrder.DESC)
          .map { row ->
            QueryInfo(
              row[session_id],
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
              row[maxDate] ?: DateTime.now(DateTimeZone.UTC)
            )
          }
      }.let { (query, duration) ->
        logger.debug { "User sessions query took $duration" }
        query
      }
    }

  fun activeSessions(duration: Duration): Long =
    if (!isDbmsEnabled())
      0
    else
      transaction {
        //addLogger(KotlinLoggingSqlLogger)
        measureTimedValue {
          val sessionRef = ServerRequestsTable.sessionRef
          val created = ServerRequestsTable.created
          ServerRequestsTable
            .slice(sessionRef.countDistinct())
            .select { created greater dateTimeExpr("now() - interval '${duration.inWholeMilliseconds} milliseconds'") }
            .map { it[0] as Long }
            .first()
        }.let { (query, duration) ->
          logger.debug { "Active sessions query took $duration" }
          query
        }
      }
}