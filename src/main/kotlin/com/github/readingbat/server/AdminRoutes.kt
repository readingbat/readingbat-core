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

package com.github.readingbat.server

import com.codahale.metrics.jvm.ThreadDump
import com.github.pambrose.common.response.redirectTo
import com.github.pambrose.common.util.isNotNull
import com.github.pambrose.common.util.isNull
import com.github.pambrose.common.util.randomId
import com.github.readingbat.common.*
import com.github.readingbat.common.AuthRoutes.COOKIES
import com.github.readingbat.common.Constants.NO_TRACK_HEADER
import com.github.readingbat.common.Endpoints.PING
import com.github.readingbat.common.Endpoints.THREAD_DUMP
import com.github.readingbat.common.SessionActivites.markActivity
import com.github.readingbat.server.ReadingBatServer.usePostgres
import com.github.readingbat.server.ServerUtils.get
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.html.*
import io.ktor.http.ContentType.Text.Plain
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.sessions.*
import io.ktor.util.pipeline.*
import kotlinx.html.body
import kotlinx.html.div
import mu.KLogging
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.ByteArrayOutputStream
import java.lang.management.ManagementFactory
import java.time.Instant.ofEpochMilli
import java.time.LocalDateTime.ofInstant
import java.time.ZoneId

internal object AdminRoutes : KLogging() {

  fun Routing.adminRoutes(metrics: Metrics) {

    get(PING, metrics) {
      call.respondText("pong", Plain)
    }

    get(THREAD_DUMP, metrics) {
      try {
        val baos = ByteArrayOutputStream()
        baos.use { ThreadDumpInfo.threadDump.dump(true, true, it) }
        val output = String(baos.toByteArray(), Charsets.UTF_8)
        call.respondText(output, Plain)
      } catch (e: NoClassDefFoundError) {
        call.respondText("Sorry, your runtime environment does not allow dump threads.", Plain)
      }
    }

    get(COOKIES) {
      val principal = call.userPrincipal
      val session = call.browserSession
      logger.info { "UserPrincipal: $principal BrowserSession: $session" }

      call.respondHtml {
        body {
          if (principal.isNull() && session.isNull())
            div { +"No cookies are present." }
          else {
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

    fun PipelineContext<Unit, ApplicationCall>.clearPrincipal() {
      call.userPrincipal
        .also {
          if (it.isNotNull()) {
            logger.info { "Clearing principal $it" }
            call.sessions.clear<UserPrincipal>()
          }
          else {
            logger.info { "Principal not set" }
          }
        }
    }

    fun PipelineContext<Unit, ApplicationCall>.clearSessionId() {
      call.browserSession
        .also {
          if (it.isNotNull()) {
            logger.info { "Clearing browser session id $it" }
            call.sessions.clear<BrowserSession>()
            if (usePostgres) {
              transaction {
                BrowserSessions.deleteWhere { BrowserSessions.session_id eq it.id }
              }
            }
          }
          else {
            logger.info { "Browser session id not set" }
          }
        }
    }

    get("/clear-cookies") {
      clearPrincipal()
      clearSessionId()
      redirectTo { "/" }
    }

    get("/clear-principal") {
      clearPrincipal()
      redirectTo { "/" }
    }

    get("/clear-sessionid") {
      clearSessionId()
      redirectTo { "/" }
    }
  }

  fun PipelineCall.assignBrowserSession() {
    if (call.request.headers.contains(NO_TRACK_HEADER))
      return

    if (call.browserSession.isNull()) {
      val browserSession = BrowserSession(id = randomId(15))
      call.sessions.set(browserSession)
      browserSession.markActivity(call)
      logger.info { "Created browser session: ${browserSession.id} - ${call.request.origin.remoteHost}" }
    }
  }

  private object ThreadDumpInfo {
    val threadDump by lazy { ThreadDump(ManagementFactory.getThreadMXBean()) }
  }
}