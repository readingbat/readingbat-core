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
import com.github.pambrose.common.util.OwnerType.Organization
import com.github.readingbat.common.Endpoints.STATIC_ROOT
import com.github.readingbat.dsl.Challenge
import com.github.readingbat.dsl.ChallengeGroup
import com.github.readingbat.dsl.LanguageType.Java
import com.github.readingbat.dsl.LanguageType.Kotlin
import com.github.readingbat.dsl.LanguageType.Python
import com.github.readingbat.dsl.ReturnType.BooleanArrayType
import com.github.readingbat.dsl.ReturnType.FloatArrayType
import com.github.readingbat.dsl.ReturnType.FloatType
import com.github.readingbat.dsl.ReturnType.IntArrayType
import com.github.readingbat.dsl.ReturnType.StringArrayType
import com.github.readingbat.dsl.readingBatContent
import com.github.readingbat.server.Installs.installs
import com.github.readingbat.server.Locations.locations
import com.github.readingbat.server.ReadingBatServer.content
import com.github.readingbat.server.routes.AdminRoutes.adminRoutes
import com.github.readingbat.server.routes.sysAdminRoutes
import com.github.readingbat.server.routes.userRoutes
import com.github.readingbat.server.ws.WsCommon.wsRoutes
import io.kotest.matchers.shouldBe
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.content.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class ServerTest {
  val GROUP_NAME = "Test Cases"

  @Test
  fun testRoot() {
    val testContent =
      readingBatContent {

        python {
          repo = GitHubRepo(Organization, "readingbat", "readingbat-core")
          srcPath = "python"
          branchName = "1.7.0"

          group(GROUP_NAME) {
            packageName = "test_content"

            challenge("boolean_array_test") { returnType = BooleanArrayType }
            challenge("int_array_test") { returnType = IntArrayType }
            challenge("float_test") { returnType = FloatType }
            challenge("float_array_test") { returnType = FloatArrayType }
            challenge("string_array_test") { returnType = StringArrayType }
          }
        }

        java {
          repo = GitHubRepo(Organization, "readingbat", "readingbat-core")
          group(GROUP_NAME) {
          }
        }

        kotlin {
          repo = GitHubRepo(Organization, "readingbat", "readingbat-core")
          group(GROUP_NAME) {
          }
        }
      }

    fun Application.module(testing: Boolean = false) {
      installs(false)

      routing {
        adminRoutes()
        locations { content.get() }
        userRoutes { content.get() }
        sysAdminRoutes { s: String -> }
        wsRoutes { content.get() }
        static(STATIC_ROOT) { resources("static") }
      }
    }

    withTestApplication({ testContent.validate(); module(true) }) {

      handleRequest(HttpMethod.Get, "/").apply { response.status() shouldBe OK }
      handleRequest(HttpMethod.Get, Java.contentRoot).apply { response.status() shouldBe OK }
      handleRequest(HttpMethod.Get, Python.contentRoot).apply { response.status() shouldBe OK }
      handleRequest(HttpMethod.Get, Kotlin.contentRoot).apply { response.status() shouldBe OK }

      val group = testContent.python.get(GROUP_NAME)
      val challenge = group.challengeByName("boolean_array_test") ?: error("Missing challenge")

      val funcInfo = testContent.functionInfoMap[challenge.challengeId] ?: error("Missing functionInfo")
      runBlocking {
        val resp = funcInfo.gradeResponse(0, "")
        resp.correct shouldBe false
      }
    }
  }
}

fun <T : Challenge> ChallengeGroup<T>.challengeByName(name: String) =
  challenges.firstOrNull { it.challengeName.value == name }
