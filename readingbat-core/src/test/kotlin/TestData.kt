/*
 * Copyright © 2021 Paul Ambrose (pambrose@mac.com)
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
import com.github.readingbat.dsl.ReturnType.BooleanArrayType
import com.github.readingbat.dsl.ReturnType.FloatArrayType
import com.github.readingbat.dsl.ReturnType.FloatType
import com.github.readingbat.dsl.ReturnType.IntArrayType
import com.github.readingbat.dsl.ReturnType.StringArrayType
import com.github.readingbat.dsl.readingBatContent

object TestData {

  const val GROUP_NAME = "Test Cases"

  fun readTestContent() =
    readingBatContent {
      python {
        //repo = GitHubRepo(OwnerType.Organization, "readingbat", "readingbat-core")
        //branchName = "1.12.0"
        repo = FileSystemSource("../")
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

          challenge("StringArrayKtTest1") { returnType = StringArrayType }
        }
      }
    }.apply { validate() }
}