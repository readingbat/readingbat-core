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

import com.github.readingbat.dsl.LanguageType.Java
import com.github.readingbat.dsl.LanguageType.Python
import com.github.readingbat.dsl.readingBatContent
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ServerTest {
  @Test
  fun testRoot() {
    val content =
      readingBatContent {

        java {
          repoRoot = "Something"
        }

        python {
          repoRoot = "Something"
        }
      }

    withTestApplication({ content.validate(); module(testing = true, content = content) }) {

      handleRequest(HttpMethod.Get, "/").apply {
        assertEquals(HttpStatusCode.Found, response.status())
      }

      handleRequest(HttpMethod.Get, "/${Java.lowerName}").apply {
        assertEquals(HttpStatusCode.OK, response.status())
      }
      handleRequest(HttpMethod.Get, "/${Python.lowerName}").apply {
        assertEquals(HttpStatusCode.OK, response.status())
      }
    }
  }
}
