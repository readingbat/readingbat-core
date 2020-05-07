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

package com.github.readingbat

import com.github.pambrose.common.util.GitHubRepo
import com.github.readingbat.Module.module
import com.github.readingbat.dsl.LanguageType.*
import com.github.readingbat.dsl.readingBatContent
import com.github.readingbat.misc.Constants.root
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode.Companion.Found
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ServerTest {
  @Test
  fun testRoot() {
    val testContent =
      readingBatContent {

        java {
          repo = GitHubRepo("readingbat", "readingbat-java-content")
          group("group1") {

          }
        }

        python {
          repo = GitHubRepo("readingbat", "readingbat-java-content")
          group("group1") {

          }
        }

        kotlin {
          repo = GitHubRepo("readingbat", "readingbat-java-content")
          group("group1") {

          }
        }
      }

    withTestApplication({
                          testContent.validate()
                          module(testing = true, content = testContent)
                        }) {

      handleRequest(HttpMethod.Get, "/").apply {
        assertEquals(Found, response.status())
      }

      handleRequest(HttpMethod.Get, "/$root/${Java.lowerName}").apply {
        assertEquals(OK, response.status())
      }

      handleRequest(HttpMethod.Get, "/$root/${Python.lowerName}").apply {
        assertEquals(OK, response.status())
      }

      handleRequest(HttpMethod.Get, "/$root/${Kotlin.lowerName}").apply {
        assertEquals(OK, response.status())
      }
    }
  }
}
