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

import com.readingbat.TestData
import com.readingbat.common.Endpoints.LOGOUT_ENDPOINT
import com.readingbat.common.Property
import com.readingbat.kotest.TestSupport.initTestProperties
import com.readingbat.kotest.TestSupport.testModule
import com.readingbat.server.ServerUtils.safeRedirectPath
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode.Companion.Found
import io.ktor.server.testing.testApplication

class SafeRedirectTest : StringSpec() {
  init {
    afterEach {
      Property.IS_PRODUCTION.setProperty("false")
    }

    "safeRedirectPath allows valid relative paths" {
      safeRedirectPath("/") shouldBe "/"
      safeRedirectPath("/content/java") shouldBe "/content/java"
      safeRedirectPath("/user-prefs?returnPath=/content") shouldBe "/user-prefs?returnPath=/content"
    }

    "safeRedirectPath rejects absolute URLs" {
      safeRedirectPath("https://evil.com") shouldBe "/"
      safeRedirectPath("http://evil.com") shouldBe "/"
      safeRedirectPath("ftp://evil.com") shouldBe "/"
    }

    "safeRedirectPath rejects protocol-relative URLs" {
      safeRedirectPath("//evil.com") shouldBe "/"
      safeRedirectPath("//evil.com/path") shouldBe "/"
    }

    "safeRedirectPath rejects empty and non-slash paths" {
      safeRedirectPath("") shouldBe "/"
      safeRedirectPath("evil.com") shouldBe "/"
      safeRedirectPath("javascript:alert(1)") shouldBe "/"
    }

    "Logout endpoint redirects to safe path" {
      initTestProperties()

      TestData.readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            val response =
              client.config { followRedirects = false }
                .get("$LOGOUT_ENDPOINT?returnPath=/content/java")
            response.status shouldBe Found
            response.headers["Location"] shouldBe "/content/java"
          }
        }
    }

    "Logout endpoint blocks absolute URL redirect" {
      initTestProperties()

      TestData.readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            val response =
              client.config { followRedirects = false }
                .get("$LOGOUT_ENDPOINT?returnPath=https://evil.com")
            response.status shouldBe Found
            response.headers["Location"] shouldBe "/"
          }
        }
    }

    "Logout endpoint blocks protocol-relative URL redirect" {
      initTestProperties()

      TestData.readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            val response =
              client.config { followRedirects = false }
                .get("$LOGOUT_ENDPOINT?returnPath=//evil.com")
            response.status shouldBe Found
            response.headers["Location"] shouldBe "/"
          }
        }
    }
  }
}
