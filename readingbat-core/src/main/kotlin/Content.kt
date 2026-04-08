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

import com.pambrose.common.util.FileSystemSource
import com.pambrose.common.util.GitHubRepo
import com.pambrose.common.util.OwnerType
import com.pambrose.common.util.OwnerType.Organization
import com.readingbat.dsl.GitHubContent
import com.readingbat.dsl.ReturnType.BooleanArrayType
import com.readingbat.dsl.ReturnType.FloatArrayType
import com.readingbat.dsl.ReturnType.FloatType
import com.readingbat.dsl.ReturnType.IntArrayType
import com.readingbat.dsl.ReturnType.StringArrayType
import com.readingbat.dsl.ReturnType.StringListType
import com.readingbat.dsl.eval
import com.readingbat.dsl.readingBatContent

val dslContent =
  readingBatContent {
    // repo = GitHubRepo(organization, "readingbat-java-content")

    repo = FileSystemSource("./")

    /*
        java {
          //repo = GitHubRepo(organization, "readingbat-java-content")
          //repo = FileSystemSource("./")
          branchName = branch

          group("Warmup 1") {
            packageName = "warmup1"
            description = "This is a description of Warmup 1"
            includeFiles = "*.java"

            challenge("JoinEnds") {
              description = """This is a description of JoinEnds"""
              codingBatEquiv = "p141494"
            }
          }
        }

        python {
          //repo = GitHubRepo("readingbat", "readingbat-python-content")
          branchName = "dev"

          group("Numeric Expressions") {
            packageName = "numeric_expressions"
            description = "Basic numeric expressions"
            includeFilesWithType = "*.py" returns ReturnType.BooleanType

            challenge("lt_expr") {
              description = """Determine if one value is less than another with the "<" operator."""
              returnType = ReturnType.BooleanType
            }

          }
        }
    */
    /*
    include(GitHubContent(Organization, "readingbat", "readingbat-java-content").eval(this).java)
    include(GitHubContent(Organization, "readingbat", "readingbat-python-content", srcPath = "src").eval(this).python)
    include(GitHubContent(Organization, "readingbat", "readingbat-java-content").eval(this).kotlin)
*/
    /*
        kotlin {
          group("Infinite Loop") {
            packageName = "com.readingbat.testcontent"

            challenge("InfiniteLoop") {
              returnType = ReturnType.BooleanType
            }

          }
        }
    */
    // include(GitHubContent(Organization, "readingbat", "readingbat-java-content", fileName = "Content.kt").eval(this, variableName = "content").java)

//    include(GitHubContent(Organization, "readingbat", "readingbat-java-content").eval(this).java)
//
//    include(GitHubContent(Organization, "readingbat", "readingbat-java-content").eval(this).kotlin, "Athenian: ")

    include(
      GitHubContent(
        Organization,
        "readingbat",
        "readingbat-python-content",
//        srcPath = "src",
      ).eval(this).python,
    )

    val athenian =
      GitHubContent(OwnerType.User, "maleich", "ReadingBat-content").eval(this)
    include(athenian.python, "Athenian: ")

    python {
      // repo = GitHubRepo(Organization, "readingbat", "readingbat-core")
      // branchName = "1.12.0"
      repo = FileSystemSource("./")
      srcPath = "python"

      group("Test Cases") {
        packageName = "testcontent"
        description = "Tests"

        challenge("divide1") { returnType = FloatType } // This should be a float

        challenge("boolean_array_test") { returnType = BooleanArrayType }
        challenge("int_array_test") { returnType = IntArrayType }
        challenge("float_test") { returnType = FloatType }
        challenge("float_array_test") { returnType = FloatArrayType }
        challenge("string_array_test") { returnType = StringArrayType }
        challenge("for_loop1") { returnType = StringArrayType }
      }
    }

    java {
      repo = FileSystemSource("./")
      srcPath = "readingbat-core/src/test/java"

      group("Java Tests 2") {
        packageName = "com.readingbat.testcontent"
        description = "Tests 2"

        challenge("CharAt1")
        challenge("StringArrayTest2")
        challenge("StringListTest1")
        challenge("StringListTest2")
      }
    }

//    java {
//      repo = GitHubRepo(Organization, "readingbat", "readingbat-core")
//      srcPath = "readingbat-core/src/test/java"
//      // branchName = "2.1.2"
//
//      group("Java Tests") {
//        packageName = "com.readingbat.testcontent"
//        description = "Tests"
//
//        challenge("CharAt1")
//        challenge("StringArrayTest2")
//        challenge("StringListTest1")
//        challenge("StringListTest2")
//      }
//    }

    kotlin {
      repo = GitHubRepo(Organization, "readingbat", "readingbat-core")
      srcPath = "readingbat-core/src/test/kotlin"
      // branchName = "2.1.2"

      group("Kotlin Tests") {
        packageName = "com.readingbat.testcontent"
        description = "Tests"

        challenge("StringArrayKtTest1") { returnType = StringArrayType }
        challenge("StringArrayKtTest2") { returnType = StringArrayType }
        challenge("StringListKtTest1") { returnType = StringListType }
        challenge("StringListKtTest2") { returnType = StringListType }
      }
    }

    /*
    java {
      group("test1") {
        packageName = "kgroup1"

      }
      group("test2") {
        packageName = "kgroup2"
        description = "A description"
      }
      group("test3") {
        packageName = "kgroup2"
        description = """A description
          |and more.
        """.trimMargin()
      }
    }
     */
  }
