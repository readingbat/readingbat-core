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
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.dsl.ReturnType
import com.github.readingbat.dsl.readingBatContent
import com.github.readingbat.server.Installs.installs
import com.github.readingbat.server.Locations.locations
import com.github.readingbat.server.ReadingBatServer
import com.github.readingbat.server.routes.AdminRoutes.adminRoutes
import com.github.readingbat.server.routes.sysAdminRoutes
import com.github.readingbat.server.routes.userRoutes
import com.github.readingbat.server.ws.WsCommon.wsRoutes
import io.ktor.application.*
import io.ktor.http.content.*
import io.ktor.routing.*

object TestData {

  const val GROUP_NAME = "Test Cases"

  fun readTestContent() =
    readingBatContent {
      python {
        //repo = GitHubRepo(OwnerType.Organization, "readingbat", "readingbat-core")
        //branchName = "1.9.0"
        repo = FileSystemSource("../")
        srcPath = "python"

        group(GROUP_NAME) {
          packageName = "test_content"

          challenge("boolean_array_test") { returnType = ReturnType.BooleanArrayType }
          challenge("int_array_test") { returnType = ReturnType.IntArrayType }
          challenge("float_test") { returnType = ReturnType.FloatType }
          challenge("float_array_test") { returnType = ReturnType.FloatArrayType }
          challenge("string_array_test") { returnType = ReturnType.StringArrayType }
        }
      }

      java {
        //repo = GitHubRepo(OwnerType.Organization, "readingbat", "readingbat-core")
        repo = FileSystemSource("./")
        srcPath = "src/test/java"
        group(GROUP_NAME) {
          packageName = "com.github.readingbat.test_content"

          challenge("StringArrayTest1")
        }
      }

      kotlin {
        //repo = GitHubRepo(OwnerType.Organization, "readingbat", "readingbat-core")
        repo = FileSystemSource("./")
        srcPath = "src/test/kotlin"
        group(GROUP_NAME) {
          packageName = "com.github.readingbat.test_content"

          challenge("StringArrayKtTest1") { returnType = ReturnType.StringArrayType }
        }
      }
    }.apply { validate() }

  fun Application.module(testing: Boolean = false, testContent: ReadingBatContent) {
    installs(false)

    routing {
      adminRoutes(ReadingBatServer.metrics)
      locations(ReadingBatServer.metrics) { testContent }
      userRoutes(ReadingBatServer.metrics) { testContent }
      sysAdminRoutes(ReadingBatServer.metrics) { s: String -> }
      wsRoutes(ReadingBatServer.metrics) { testContent }
      static(Endpoints.STATIC_ROOT) { resources("static") }
    }
  }
}