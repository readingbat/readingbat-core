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

package com.readingbat.common

import com.pambrose.common.email.Email
import com.pambrose.common.exposed.get
import com.pambrose.common.exposed.readonlyTx
import com.readingbat.dsl.isDbmsEnabled
import com.readingbat.server.BrowserSessionsTable
import com.readingbat.server.FullName
import com.readingbat.server.GeoInfosTable
import com.readingbat.server.ServerRequestsTable
import com.readingbat.server.UsersTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.Count
import org.jetbrains.exposed.v1.core.Max
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.countDistinct
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.datetime.KotlinInstantColumnType
import org.jetbrains.exposed.v1.jdbc.select
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.time.measureTimedValue

/**
 * Queries for active session information, used for admin dashboards and Prometheus metrics.
 *
 * Provides session activity data by joining server requests, browser sessions, users, and
 * geolocation tables. Supports both detailed session listings (for admin pages) and
 * aggregate active-session counts (for metrics).
 */
internal object SessionActivites {
  private val logger = KotlinLogging.logger {}
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

  /** Data class holding the result of a session activity query, including user, geo, and request info. */
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
    val maxDate: Instant,
  )

  /** Queries active sessions within the last [dayCount] days (max 14), grouped by session and user. */
  fun querySessions(dayCount: Int) =
    readonlyTx {
      measureTimedValue {
        val count = Count(UsersTable.id)
        val maxDate = Max(created, KotlinInstantColumnType())
        val elems = arrayOf(fullName, email, ip, city, state, country, isp, flagUrl, userAgent)

        (ServerRequestsTable innerJoin BrowserSessionsTable innerJoin UsersTable innerJoin GeoInfosTable)
          .select(session_id, *elems, count, maxDate)
          .where { created greater instantExpr("now() - interval '${min(dayCount, 14)} day'") }
          .groupBy(*(arrayOf(session_id) + elems))
          .orderBy(maxDate, SortOrder.DESC)
          .map { row ->
            QueryInfo(
              session_id = row[session_id],
              fullName = FullName(row[fullName]),
              email = Email(row[email]),
              ip = row[ip],
              city = row[city],
              state = row[state],
              country = row[country],
              isp = row[isp],
              flagUrl = row[flagUrl],
              userAgent = row[userAgent],
              count = row[count],
              maxDate = row[maxDate] ?: nowInstant(),
            )
          }
      }.let { (query, duration) ->
        logger.debug { "User sessions query took $duration" }
        query
      }
    }

  /** Returns the count of distinct active sessions within the given [duration] window. */
  fun activeSessions(duration: Duration): Long =
    when {
      !isDbmsEnabled() -> {
        0
      }

      else -> {
        readonlyTx {
          // addLogger(KotlinLoggingSqlLogger)
          measureTimedValue {
            with(ServerRequestsTable) {
              val ms = duration.inWholeMilliseconds
              select(sessionRef.countDistinct())
                .where { created greater instantExpr("now() - interval '$ms milliseconds'") }
                .map { it[0] as Long }
                .first()
            }
          }.let { (query, duration) ->
            logger.debug { "Active sessions query took $duration" }
            query
          }
        }
      }
    }
}
