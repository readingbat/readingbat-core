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

package com.github.readingbat.server.routes

import com.codahale.metrics.jvm.ThreadDump
import com.github.pambrose.common.response.redirectTo
import com.github.pambrose.common.util.isNotNull
import com.github.pambrose.common.util.isNull
import com.github.pambrose.common.util.randomId
import com.github.readingbat.common.AuthRoutes.COOKIES
import com.github.readingbat.common.BrowserSession
import com.github.readingbat.common.Constants.NO_TRACK_HEADER
import com.github.readingbat.common.Endpoints.PING_ENDPOINT
import com.github.readingbat.common.Endpoints.THREAD_DUMP
import com.github.readingbat.common.Metrics
import com.github.readingbat.common.UserPrincipal
import com.github.readingbat.common.browserSession
import com.github.readingbat.common.isAdminUser
import com.github.readingbat.common.userPrincipal
import com.github.readingbat.dsl.isDbmsEnabled
import com.github.readingbat.dsl.isProduction
import com.github.readingbat.dsl.isSaveRequestsEnabled
import com.github.readingbat.server.BrowserSessionsTable
import com.github.readingbat.server.GeoInfo.Companion.lookupGeoInfo
import com.github.readingbat.server.ServerUtils.fetchUser
import com.github.readingbat.server.ServerUtils.get
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.ContentType.Text.Plain
import io.ktor.http.HttpStatusCode
import io.ktor.server.html.respondHtml
import io.ktor.server.plugins.origin
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import kotlinx.html.body
import kotlinx.html.div
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.io.ByteArrayOutputStream
import java.lang.management.ManagementFactory
import java.time.Instant.ofEpochMilli
import java.time.LocalDateTime.ofInstant
import java.time.ZoneId

object AdminRoutes {
  private val logger = KotlinLogging.logger {}

  private suspend fun RoutingContext.requireAdminUser(): Boolean {
    if (isProduction()) {
      val user = fetchUser()
      if (user.isNull() || !user.isAdminUser()) {
        call.respondText("Forbidden", Plain, HttpStatusCode.Forbidden)
        return false
      }
    }
    return true
  }

  fun Routing.adminRoutes(metrics: Metrics) {
    get(PING_ENDPOINT, metrics) {
      call.respondText("pong", Plain)
    }

    get(THREAD_DUMP, metrics) {
      if (!requireAdminUser()) return@get

      try {
        ByteArrayOutputStream()
          .apply {
            use { ThreadDumpInfo.threadDump.dump(true, true, it) }
          }.let { baos ->
            String(baos.toByteArray(), Charsets.UTF_8)
          }
      } catch (e: NoClassDefFoundError) {
        "Sorry, your runtime environment does not allow dump threads."
      }.also {
        call.respondText(it, Plain)
      }
    }

    get(COOKIES) {
      if (!requireAdminUser()) return@get

      val principal = call.userPrincipal
      val session = call.browserSession
      logger.info { "UserPrincipal: $principal BrowserSession: $session" }

      call.respondHtml {
        body {
          if (principal.isNull() && session.isNull()) {
            div { +"No cookies are present." }
          } else {
            if (principal.isNotNull()) {
              val date = ofInstant(ofEpochMilli(principal.created), ZoneId.systemDefault())
              div { +"UserPrincipal: ${principal.userId} created on: $date" }
            }

            if (session.isNotNull()) {
              val date = ofInstant(ofEpochMilli(session.created), ZoneId.systemDefault())
              div { +"BrowserSession id: [${session.id}] created on: $date" }
            }
          }
        }
      }
    }

    fun RoutingContext.clearPrincipal() {
      call.userPrincipal
        .also {
          if (it.isNotNull()) {
            logger.info { "Clearing principal $it" }
            call.sessions.clear<UserPrincipal>()
          } else {
            logger.info { "Principal not set" }
          }
        }
    }

    fun RoutingContext.clearSessionId() {
      call.browserSession
        .also { bs ->
          if (bs.isNotNull()) {
            logger.info { "Clearing browser session id $bs" }
            call.sessions.clear<BrowserSession>()
            if (isDbmsEnabled())
              transaction {
                with(BrowserSessionsTable) {
                  deleteWhere { sessionId eq bs.id }
                }
              }
          } else {
            logger.info { "Browser session id not set" }
          }
        }
    }

    get("/clear-cookies") {
      if (!requireAdminUser()) return@get
      clearPrincipal()
      clearSessionId()
      redirectTo { "/" }
    }

    get("/clear-principal") {
      if (!requireAdminUser()) return@get
      clearPrincipal()
      redirectTo { "/" }
    }

    get("/clear-sessionid") {
      if (!requireAdminUser()) return@get
      clearSessionId()
      redirectTo { "/" }
    }
  }

  fun RoutingContext.assignBrowserSession() {
    if (call.request.headers.contains(NO_TRACK_HEADER))
      return

    if (call.browserSession.isNull()) {
      val browserSession = BrowserSession(id = randomId(15))
      call.sessions.set(browserSession)

      if (isSaveRequestsEnabled()) {
        val ipAddress = call.request.origin.remoteHost
        runCatching {
          lookupGeoInfo(ipAddress)
        }.onFailure { e ->
          logger.warn(e) {}
        }
      }

      logger.debug { "Created browser session: ${browserSession.id} - ${call.request.origin.remoteHost}" }
    }
  }

  private object ThreadDumpInfo {
    val threadDump by lazy { ThreadDump(ManagementFactory.getThreadMXBean()) }
  }
}
