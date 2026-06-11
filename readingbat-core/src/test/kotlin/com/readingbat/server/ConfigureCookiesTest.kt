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

import com.readingbat.common.UserPrincipal
import com.readingbat.server.ConfigureCookies.DEV_SESSION_SECRET
import com.readingbat.server.ConfigureCookies.deriveKey
import com.readingbat.server.ConfigureCookies.resolveSessionSecret
import com.readingbat.server.ConfigureCookies.sessionTransformer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.server.testing.testApplication

/**
 * Tests for [ConfigureCookies] session-cookie hardening. The auth/session cookies were previously
 * stored as plaintext, so a [UserPrincipal] (carrying a userId) could be forged or read. These
 * tests pin down the secret-resolution fail-fast, the key derivation, and the end-to-end property
 * that a tampered/plaintext cookie is rejected while a legitimately set session round-trips.
 */
class ConfigureCookiesTest : StringSpec() {
  init {
    "resolveSessionSecret falls back to the dev default when unset in dev" {
      resolveSessionSecret(production = false, configured = "") shouldBe DEV_SESSION_SECRET
    }

    "resolveSessionSecret returns a custom secret" {
      resolveSessionSecret(production = false, configured = "custom") shouldBe "custom"
      resolveSessionSecret(production = true, configured = "a-real-production-secret") shouldBe
        "a-real-production-secret"
    }

    "resolveSessionSecret fails fast when unset in production" {
      shouldThrow<IllegalStateException> {
        resolveSessionSecret(production = true, configured = "")
      }
    }

    "deriveKey is deterministic and sized" {
      deriveKey("secret", "sign", 32).size shouldBe 32
      deriveKey("secret", "encrypt", 16).size shouldBe 16
      deriveKey("secret", "sign", 32) shouldBe deriveKey("secret", "sign", 32)
    }

    "deriveKey produces distinct keys per purpose and per secret" {
      deriveKey("secret", "sign", 32) shouldNotBe deriveKey("secret", "encrypt", 32)
      deriveKey("secret-a", "sign", 32) shouldNotBe deriveKey("secret-b", "sign", 32)
    }

    "a legitimately set session round-trips through the transformer" {
      testApplication {
        install(Sessions) {
          cookie<UserPrincipal>("test_auth") { transform(sessionTransformer("a-secret", "test_auth")) }
        }
        routing {
          get("/set") {
            call.sessions.set(UserPrincipal("user-1"))
            call.respondText("ok")
          }
          get("/whoami") { call.respondText(call.sessions.get<UserPrincipal>()?.userId ?: "none") }
        }
        val client = createClient { install(HttpCookies) }
        client.get("/set")
        client.get("/whoami").bodyAsText() shouldBe "user-1"
      }
    }

    "a forged plaintext cookie is rejected" {
      testApplication {
        install(Sessions) {
          cookie<UserPrincipal>("test_auth") { transform(sessionTransformer("a-secret", "test_auth")) }
        }
        routing {
          get("/whoami") { call.respondText(call.sessions.get<UserPrincipal>()?.userId ?: "none") }
        }
        // An attacker-crafted plaintext principal has no valid signature/ciphertext and is ignored.
        val response = client.get("/whoami") { header("Cookie", "test_auth=userId%3Dadmin%26created%3D0") }
        response.bodyAsText() shouldBe "none"
      }
    }
  }
}
