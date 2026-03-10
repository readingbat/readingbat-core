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
import com.github.readingbat.common.Property
import com.github.readingbat.kotest.TestSupport.initTestProperties
import com.github.readingbat.kotest.TestSupport.testModule
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.request.get
import io.ktor.server.testing.testApplication

class CookieSecurityTest : StringSpec() {
  init {
    afterEach {
      Property.IS_PRODUCTION.setProperty("false")
    }

    "Cookies have Secure flag in production mode" {
      initTestProperties()
      Property.IS_PRODUCTION.setProperty("true")

      readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent, production = true) }

            val response = client.get("/")
            val setCookieHeaders = response.headers.getAll("Set-Cookie") ?: emptyList()
            setCookieHeaders shouldHaveAtLeastSize 1
            setCookieHeaders.forEach { cookie ->
              cookie shouldContain "Secure"
            }
          }
        }
    }

    "Cookies do not have Secure flag in non-production mode" {
      initTestProperties()

      readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            val response = client.get("/")
            val setCookieHeaders = response.headers.getAll("Set-Cookie") ?: emptyList()
            setCookieHeaders shouldHaveAtLeastSize 1
            setCookieHeaders.forEach { cookie ->
              cookie shouldNotContain "Secure"
            }
          }
        }
    }
  }
}
