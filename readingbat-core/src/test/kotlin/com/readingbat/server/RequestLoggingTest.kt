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

package com.readingbat.server

import com.pambrose.common.email.Email
import com.pambrose.common.exposed.readonlyTx
import com.readingbat.common.BrowserSession
import com.readingbat.common.Constants.UNKNOWN_USER_ID
import com.readingbat.common.OAuthProvider
import com.readingbat.common.User
import com.readingbat.common.User.Companion.createUnknownUser
import com.readingbat.common.User.Companion.userExists
import com.readingbat.common.UserPrincipal
import com.readingbat.server.GeoInfo.Companion.queryGeoInfo
import com.readingbat.server.Intercepts.requestTimingMap
import com.readingbat.withTestApp
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.origin
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.util.getOrFail
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll

private const val SESSION_PATH = "/probe/session"
private const val LOGIN_PATH = "/probe/login"
private const val LOG_PATH = "/probe/log"

/**
 * Tests that request logging tracks logged-in users only. Crawlers and bots browse the public
 * language and group pages and are handed a browser-session cookie like any other visitor, so a
 * request without a login must write neither a [ServerRequestsTable] row nor a
 * [BrowserSessionsTable] row, and must not be timed for the duration update either.
 *
 * [intercepts] installs [requestLogging] only when `IPGEOLOCATION_KEY` is set, which it is not
 * under test, so these tests install it themselves.
 */
class RequestLoggingTest : StringSpec() {
  init {
    "an anonymous request is not recorded" {
      withTestApp {
        // Production creates this placeholder account at startup; anonymous requests were logged under it.
        if (!userExists(UNKNOWN_USER_ID)) createUnknownUser(UNKNOWN_USER_ID)

        val visit = visit(userId = null)

        visit.userRefs.shouldBeEmpty()
        visit.browserSessions shouldBe 0L
      }
    }

    "an anonymous request is not timed" {
      withTestApp {
        visit(userId = null).timed shouldBe false
      }
    }

    "a logged-in request is recorded under the user" {
      withTestApp {
        val user = createUser()

        val visit = visit(userId = user.userId)

        visit.userRefs shouldBe [user.userDbmsId]
        visit.browserSessions shouldBe 1L
      }
    }

    "a logged-in request is timed" {
      withTestApp {
        visit(userId = createUser().userId).timed shouldBe true
      }
    }
  }
}

/** What request logging made of one visit to the probe page. */
private class Visit(val userRefs: List<Long>, val browserSessions: Long, val timed: Boolean)

// Sets up a browser session, logged in as userId when it is not null, then visits the probe page
// with it. The probe page carries its own X-Request-Id: the generated call ids restart with every
// test application, so they would collide with rows an earlier test wrote.
private suspend fun ApplicationTestBuilder.visit(userId: String?): Visit {
  application { requestLogging() }
  probeRoutes()
  val client = createClient { install(HttpCookies) }
  val sessionId = "session-${System.nanoTime()}"
  val logPath = "$LOG_PATH/$sessionId"

  client.get(if (userId == null) "$SESSION_PATH/$sessionId" else "$LOGIN_PATH/$sessionId/$userId")
  val timed = client.get(logPath) { header(HttpHeaders.XRequestId, "request-$sessionId") }.bodyAsText()

  return Visit(requestUserRefs(logPath), browserSessionCount(sessionId), timed.toBoolean())
}

private fun createUser(): User {
  val tag = System.nanoTime()
  return User.createOAuthUser(FullName("Logged In"), Email("logged-in-$tag@test.com"), OAuthProvider.GITHUB, "gh-$tag")
}

private fun ApplicationTestBuilder.probeRoutes() =
  routing {
    get("$SESSION_PATH/{sessionId}") {
      startSession()
      call.respondText("ok")
    }
    get("$LOGIN_PATH/{sessionId}/{userId}") {
      startSession()
      call.sessions.set(UserPrincipal(userId = call.parameters.getOrFail("userId")))
      call.respondText("ok")
    }
    // Runs between RoutingCallStarted and RoutingCallFinished, so it sees whether the call is being timed.
    get("$LOG_PATH/{tag}") {
      call.respondText((call.callId?.let { requestTimingMap.containsKey(it) } == true).toString())
    }
  }

private fun RoutingContext.startSession() {
  call.sessions.set(BrowserSession(id = call.parameters.getOrFail("sessionId")))
  seedGeoInfo(call.request.origin.remoteHost)
}

// Seed a valid geo row so the request-logging lookup never calls the ipgeolocation.io API.
private fun seedGeoInfo(ip: String) {
  if (queryGeoInfo(ip) == null)
    GeoInfo(true, -1, ip, """{"ip":"$ip","city":"Testville","country_name":"Testland"}""").insert()
}

private fun requestUserRefs(logPath: String) =
  readonlyTx {
    ServerRequestsTable
      .select(ServerRequestsTable.userRef)
      .where { ServerRequestsTable.path eq logPath }
      .map { it[ServerRequestsTable.userRef] }
  }

private fun browserSessionCount(id: String) =
  readonlyTx {
    BrowserSessionsTable
      .selectAll()
      .where { BrowserSessionsTable.sessionId eq id }
      .count()
  }
