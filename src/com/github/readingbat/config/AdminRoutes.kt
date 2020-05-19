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

package com.github.readingbat.config

import com.codahale.metrics.jvm.ThreadDump
import com.github.pambrose.common.util.randomId
import com.github.readingbat.PipelineCall
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.AuthRoutes.COOKIES
import com.github.readingbat.misc.BrowserSession
import com.github.readingbat.misc.UserPrincipal
import com.github.readingbat.redirectTo
import io.ktor.application.call
import io.ktor.html.respondHtml
import io.ktor.http.ContentType
import io.ktor.response.respondText
import io.ktor.routing.Routing
import io.ktor.routing.get
import io.ktor.sessions.clear
import io.ktor.sessions.get
import io.ktor.sessions.sessions
import io.ktor.sessions.set
import kotlinx.html.body
import kotlinx.html.div
import mu.KotlinLogging
import java.io.ByteArrayOutputStream
import java.lang.management.ManagementFactory
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId


private val logger = KotlinLogging.logger {}

internal fun Routing.adminRoutes(content: ReadingBatContent) {

  get("/ping") { call.respondText("pong", ContentType.Text.Plain) }

  get("/threaddump") {
    try {
      val baos = ByteArrayOutputStream()
      baos.use { ThreadDumpInfo.threadDump.dump(true, true, it) }
      val output = String(baos.toByteArray(), Charsets.UTF_8)
      call.respondText(output, ContentType.Text.Plain)
    } catch (e: NoClassDefFoundError) {
      call.respondText("Sorry, your runtime environment does not allow dump threads.", ContentType.Text.Plain)
    }
  }

  get(COOKIES) {
    val principal = call.sessions.get<UserPrincipal>()
    val session = call.sessions.get<BrowserSession>()
    logger.info { "UserPrincipal: $principal BrowserSession: $session" }

    call.respondHtml {
      body {
        if (principal == null && session == null)
          div { +"No cookies are present." }
        else {
          if (principal != null) {
            val date = LocalDateTime.ofInstant(Instant.ofEpochMilli(principal.created), ZoneId.systemDefault())
            div { +"UserPrincipal: ${principal.userId} created on: $date" }
          }

          if (session != null) {
            val date = LocalDateTime.ofInstant(Instant.ofEpochMilli(session.created), ZoneId.systemDefault())
            div { +"BrowserSession id: [${session.id}] created on: $date" }
          }
        }
      }
    }
  }

  get("/clear-cookies") {
    val principal = call.sessions.get<UserPrincipal>()
    if (principal != null) {
      logger.info { "Clearing $principal" }
      call.sessions.clear<UserPrincipal>()
    }

    val session = call.sessions.get<BrowserSession>()
    if (session != null) {
      logger.info { "Clearing $session" }
      // @TODO Should delete session data from redis
      call.sessions.clear<BrowserSession>()
    }
    redirectTo { "/" }
  }
}

internal fun PipelineCall.registerBrowserSession() {
  val session = call.sessions.get<BrowserSession>()
  if (session == null) {
    call.sessions.set(BrowserSession(id = randomId(15)))
    logger.info { "Created: ${call.sessions.get<BrowserSession>()}" }
  }
}

private object ThreadDumpInfo {
  internal val threadDump by lazy { ThreadDump(ManagementFactory.getThreadMXBean()) }
}