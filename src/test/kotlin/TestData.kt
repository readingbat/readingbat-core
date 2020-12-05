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
import com.github.readingbat.dsl.ReturnType
import com.github.readingbat.dsl.readingBatContent

object TestData {

  const val GROUP_NAME = "Test Cases"

  internal fun readTestContent() =
    readingBatContent {
      python {
        repo = FileSystemSource("./")
        //repo = GitHubRepo(OwnerType.Organization, "readingbat", "readingbat-core")
        //branchName = "1.7.0"
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

          challenge("StringArrayKtTest1") { returnType = ReturnType.StringArrayType }
        }
      }
    }.apply { validate() }

}