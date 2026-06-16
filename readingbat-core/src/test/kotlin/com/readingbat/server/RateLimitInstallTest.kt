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

import com.readingbat.common.Endpoints.PING_ENDPOINT
import com.readingbat.server.Installs.configureGlobalRateLimit
import com.readingbat.server.Installs.isRateLimitExcluded
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.TooManyRequests
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.testing.testApplication

/**
 * Tests for the global rate limiter wiring. The limiter was previously registered as a named
 * provider that no route ever applied (`register {}` instead of `global {}`), so it never
 * enforced anything. These tests verify that the limiter actually throttles dynamic requests and
 * that high-volume static/WebSocket/ping traffic is excluded so normal browsing is unaffected.
 */
class RateLimitInstallTest : StringSpec() {
  init {
    "isRateLimitExcluded excludes static, websocket, and ping paths" {
      isRateLimitExcluded("/static/css/styles.css") shouldBe true
      isRateLimitExcluded("/ws/challenge/abc") shouldBe true
      isRateLimitExcluded(PING_ENDPOINT) shouldBe true
    }

    "isRateLimitExcluded does not exclude dynamic content paths" {
      isRateLimitExcluded("/content/java/Warmup-1/hello") shouldBe false
      isRateLimitExcluded("/") shouldBe false
    }

    "global rate limiter returns 429 once a client exceeds the limit on a dynamic path" {
      testApplication {
        install(RateLimit) { configureGlobalRateLimit(limit = 3, refillSeconds = 60) }
        routing {
          get("/content/test") { call.respondText("ok") }
        }
        repeat(3) { client.get("/content/test").status shouldBe OK }
        client.get("/content/test").status shouldBe TooManyRequests
      }
    }

    "global rate limiter never throttles excluded static paths" {
      testApplication {
        install(RateLimit) { configureGlobalRateLimit(limit = 3, refillSeconds = 60) }
        routing {
          get("/static/asset") { call.respondText("asset") }
        }
        repeat(10) { client.get("/static/asset").status shouldBe OK }
      }
    }
  }
}
