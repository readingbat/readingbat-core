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

import com.github.pambrose.common.util.FileSystemSource
import com.github.readingbat.common.Endpoints
import com.github.readingbat.common.FunctionInfo
import com.github.readingbat.dsl.Challenge
import com.github.readingbat.dsl.ChallengeGroup
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.dsl.ReturnType.BooleanArrayType
import com.github.readingbat.dsl.ReturnType.FloatArrayType
import com.github.readingbat.dsl.ReturnType.FloatType
import com.github.readingbat.dsl.ReturnType.IntArrayType
import com.github.readingbat.dsl.ReturnType.StringArrayType
import com.github.readingbat.dsl.readingBatContent
import com.github.readingbat.server.Installs.installs
import com.github.readingbat.server.Locations.locations
import com.github.readingbat.server.routes.AdminRoutes.adminRoutes
import com.github.readingbat.server.routes.sysAdminRoutes
import com.github.readingbat.server.routes.userRoutes
import com.github.readingbat.server.ws.WsCommon.wsRoutes
import io.ktor.application.*
import io.ktor.http.content.*
import io.ktor.routing.*
import kotlinx.coroutines.runBlocking

object TestSupport {
  internal fun ReadingBatContent.pythonGroup(name: String) = python.get(name)
  internal fun ReadingBatContent.javaGroup(name: String) = java.get(name)
  internal fun ReadingBatContent.kotlinGroup(name: String) = kotlin.get(name)

  internal fun <T : Challenge> ChallengeGroup<T>.challengeByName(name: String) =
    challenges.firstOrNull { it.challengeName.value == name } ?: error("Missing challenge $name")

  internal fun <T : Challenge> ChallengeGroup<T>.functionInfo(name: String) =
    challengeByName(name).functionInfo()

  val GROUP_NAME = "Test Cases"

  internal fun readTestContent() =
    readingBatContent {
      python {
        repo = FileSystemSource("./")
        //repo = GitHubRepo(OwnerType.Organization, "readingbat", "readingbat-core")
        //branchName = "1.7.0"
        srcPath = "python"

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
        repo = FileSystemSource("./")
        //repo = GitHubRepo(OwnerType.Organization, "readingbat", "readingbat-core")
        srcPath = "src/test/java"
        group(GROUP_NAME) {
          packageName = "com.github.readingbat.test_content"

          challenge("StringArrayTest1")
        }
      }

      kotlin {
        repo = FileSystemSource("./")
        //repo = GitHubRepo(OwnerType.Organization, "readingbat", "readingbat-core")
        srcPath = "src/test/kotlin"
        group(GROUP_NAME) {
          packageName = "com.github.readingbat.test_content"

          challenge("StringArrayKtTest1") { returnType = StringArrayType }
        }
      }
    }

  internal fun FunctionInfo.checkUserResponse(index: Int, userResponse: String) =
    runBlocking {
      checkResponse(index, userResponse)
    }


  fun Application.module(testing: Boolean = false, testContent: ReadingBatContent) {
    installs(false)

    routing {
      adminRoutes()
      locations { testContent }
      userRoutes { testContent }
      sysAdminRoutes { s: String -> }
      wsRoutes { testContent }
      static(Endpoints.STATIC_ROOT) { resources("static") }
    }
  }
}