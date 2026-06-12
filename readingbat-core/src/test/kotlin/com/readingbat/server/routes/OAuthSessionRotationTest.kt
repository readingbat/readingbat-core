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

package com.readingbat.server.routes

import com.readingbat.common.AuthName.AUTH_COOKIE
import com.readingbat.common.BrowserSession
import com.readingbat.common.UserPrincipal
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.server.testing.testApplication

private const val SESSION_COOKIE = "readingbat_session_id"
private const val SEED_ID = "seed-fixed-session-id"

private fun HttpResponse.cookiePair(name: String): String? =
  (headers.getAll("Set-Cookie") ?: emptyList())
    .firstOrNull { it.startsWith("$name=") }
    ?.substringBefore(";")

/**
 * Tests that logging in rotates the anonymous browser session, preventing session fixation: a
 * pre-login (possibly attacker-supplied) browser-session id must be replaced with a fresh one, and
 * the authentication principal must be issued.
 */
class OAuthSessionRotationTest : StringSpec() {
  init {
    "establishAuthenticatedSession rotates the browser session id and sets the principal" {
      testApplication {
        install(Sessions) {
          cookie<BrowserSession>(SESSION_COOKIE)
          cookie<UserPrincipal>(AUTH_COOKIE)
        }
        routing {
          get("/seed") {
            call.sessions.set(BrowserSession(id = SEED_ID))
            call.respondText("seeded")
          }
          get("/sim-login") {
            establishAuthenticatedSession("user-1")
            call.respondText("ok")
          }
        }

        // Establish a known (attacker-fixed) browser session, then log in carrying it.
        val seeded = client.get("/seed").cookiePair(SESSION_COOKIE)
        seeded.shouldNotBeNull()

        val response = client.get("/sim-login") { header("Cookie", seeded!!) }
        val setCookies = response.headers.getAll("Set-Cookie") ?: emptyList()

        // Login must issue a fresh browser session cookie that no longer carries the fixed id.
        val rotated = setCookies.firstOrNull { it.startsWith("$SESSION_COOKIE=") }
        rotated.shouldNotBeNull()
        rotated!! shouldNotContain SEED_ID
        // The authentication principal cookie must be set.
        setCookies.any { it.startsWith("$AUTH_COOKIE=") } shouldBe true
      }
    }
  }
}
