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
import com.readingbat.common.Property
import com.readingbat.kotest.TestSupport.initTestProperties
import com.readingbat.pages.ContentLoadingPage
import com.readingbat.server.ReadingBatServer.isContentReady
import com.readingbat.server.ReadingBatServer.markContentLoaded
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication

/**
 * Exercises the readiness interceptor registered by [intercepts]: while the DSL content has not
 * yet been loaded, user-facing paths return [HttpStatusCode.ServiceUnavailable] with the
 * [ContentLoadingPage] body and a `Retry-After` header; allowlisted paths (ping, static) pass
 * through; once [markContentLoaded] is called, the gate flips and previously-blocked paths
 * reach their handlers.
 *
 * Note: this test mutates [ReadingBatServer.contentReadCount] (a JVM-global `AtomicInt`) and
 * leaves it incremented. No other tests currently consume `isContentReady`, so this is safe;
 * any future test that does will need to account for the JVM-shared state.
 */
class ContentReadinessInterceptorTest : StringSpec() {
  init {
    "readiness interceptor blocks user paths until content is marked loaded" {
      initTestProperties()
      // Disable DBMS so the existing auth intercept (which depends on Ktor Sessions / DB lookups)
      // does not fire — this test stays focused on the readiness gate. Earlier tests in the same
      // JVM may have flipped this property on, so set it explicitly here.
      Property.DBMS_ENABLED.setProperty("false")
      isContentReady shouldBe false

      testApplication {
        application {
          intercepts()
          routing {
            get("/anything") { call.respondText("would-not-be-served") }
            get(PING_ENDPOINT) { call.respondText("pong") }
            get("/static/foo.css") { call.respondText("body { }", ContentType.Text.CSS) }
          }
        }

        client.get("/anything").also {
          it.status shouldBe HttpStatusCode.ServiceUnavailable
          it.headers[HttpHeaders.RetryAfter] shouldBe ContentLoadingPage.RETRY_AFTER_SECS.toString()
          it.bodyAsText() shouldContain "Site is loading"
        }
        client.get(PING_ENDPOINT).status shouldBe HttpStatusCode.OK
        client.get("/static/foo.css").status shouldBe HttpStatusCode.OK

        markContentLoaded()
        isContentReady shouldBe true

        client.get("/anything").also {
          it.status shouldBe HttpStatusCode.OK
          it.bodyAsText() shouldBe "would-not-be-served"
        }
      }
    }
  }
}
