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
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.AuthRoutes
import com.github.readingbat.misc.ClientSession
import io.ktor.application.call
import io.ktor.auth.UserIdPrincipal
import io.ktor.html.respondHtml
import io.ktor.http.ContentType
import io.ktor.response.respondText
import io.ktor.routing.Routing
import io.ktor.routing.get
import io.ktor.sessions.clear
import io.ktor.sessions.get
import io.ktor.sessions.sessions
import io.ktor.sessions.set
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.div
import mu.KotlinLogging
import java.io.ByteArrayOutputStream
import java.lang.management.ManagementFactory

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

  get(AuthRoutes.PROFILE) {
    val principal = call.sessions.get<UserIdPrincipal>()
    call.respondHtml {
      body {
        div { +"Hello, ${principal?.name}!" }
        div { a(href = AuthRoutes.LOGOUT) { +"log out" } }
      }
    }
  }

  get("/session-register") {
    val session = call.sessions.get<ClientSession>()
    if (session == null) {
      call.sessions.set(ClientSession(name = "Student name", id = randomId(15)))
      logger.info { call.sessions.get<ClientSession>() }
    }
    call.respondText { "registered ${call.sessions.get<ClientSession>()}" }
  }

  get("/session-check") {
    val session = call.sessions.get<ClientSession>()
    logger.info { session }
    call.respondText { "checked $session" }
  }

  get("/session-2clear") {
    logger.info { call.sessions.get<ClientSession>() }
    call.sessions.clear<ClientSession>()
    call.respondText { "cleared" }
  }

}

private object ThreadDumpInfo {
  internal val threadDump by lazy { ThreadDump(ManagementFactory.getThreadMXBean()) }
}
