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

import TestData.readTestContent
import com.github.readingbat.common.AuthRoutes.COOKIES
import com.github.readingbat.common.Endpoints.PING_ENDPOINT
import com.github.readingbat.common.Endpoints.THREAD_DUMP
import com.github.readingbat.common.Property
import com.github.readingbat.kotest.TestSupport.initTestProperties
import com.github.readingbat.kotest.TestSupport.testModule
import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.core.spec.style.StringSpec
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.testing.testApplication

class AdminSecurityTest : StringSpec() {
  init {
    afterEach {
      Property.IS_PRODUCTION.setProperty("false")
    }

    "Admin endpoints return 403 in production without auth" {
      initTestProperties()
      Property.IS_PRODUCTION.setProperty("true")

      readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            client.apply {
              get(THREAD_DUMP).also { it shouldHaveStatus Forbidden }
              get(COOKIES).also { it shouldHaveStatus Forbidden }
              get("/clear-cookies").also { it shouldHaveStatus Forbidden }
              get("/clear-principal").also { it shouldHaveStatus Forbidden }
              get("/clear-sessionid").also { it shouldHaveStatus Forbidden }
            }
          }
        }
    }

    "Ping endpoint remains accessible in production without auth" {
      initTestProperties()
      Property.IS_PRODUCTION.setProperty("true")

      readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            client.get(PING_ENDPOINT).also { it shouldHaveStatus OK }
          }
        }
    }

    "Admin endpoints require auth even in non-production mode" {
      initTestProperties()

      readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            client.apply {
              get(THREAD_DUMP).also { it shouldHaveStatus Forbidden }
              get(COOKIES).also { it shouldHaveStatus Forbidden }
            }
          }
        }
    }
  }
}
