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
import com.github.readingbat.common.Endpoints.CONFIG_ENDPOINT
import com.github.readingbat.common.Endpoints.SESSIONS_ENDPOINT
import com.github.readingbat.common.Endpoints.SYSTEM_ADMIN_ENDPOINT
import com.github.readingbat.common.Property
import com.github.readingbat.kotest.TestSupport.initTestProperties
import com.github.readingbat.kotest.TestSupport.testModule
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication

class AuthenticateAdminUserTest : StringSpec(
  {
    afterEach {
      Property.IS_PRODUCTION.setProperty("false")
    }

    "Admin-guarded endpoints require auth even in non-production mode" {
      initTestProperties()

      readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            client.apply {
              get(CONFIG_ENDPOINT).bodyAsText() shouldContain "Must be logged in for this function"
              get(SESSIONS_ENDPOINT).bodyAsText() shouldContain "Must be logged in for this function"
              get(SYSTEM_ADMIN_ENDPOINT).bodyAsText() shouldContain "Must be logged in for this function"
            }
          }
        }
    }

    "Admin-guarded endpoints require auth in production mode" {
      initTestProperties()
      Property.IS_PRODUCTION.setProperty("true")

      readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            client.apply {
              get(CONFIG_ENDPOINT).bodyAsText() shouldContain "Must be logged in for this function"
              get(SESSIONS_ENDPOINT).bodyAsText() shouldContain "Must be logged in for this function"
              get(SYSTEM_ADMIN_ENDPOINT).bodyAsText() shouldContain "Must be logged in for this function"
            }
          }
        }
    }
  },
)
