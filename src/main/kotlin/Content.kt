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

import com.github.pambrose.common.util.FileSystemSource
import com.github.pambrose.common.util.OwnerType.Organization
import com.github.readingbat.dsl.GitHubContent
import com.github.readingbat.dsl.eval
import com.github.readingbat.dsl.readingBatContent

val content =
  readingBatContent {
    //repo = GitHubRepo(organization, "readingbat-java-content")

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

    //include(GitHubContent(Organization, "readingbat", "readingbat-java-content").eval(this).kotlin, "Athenian: ")

    include(GitHubContent(Organization, "readingbat", "readingbat-python-content", srcPath = "src").eval(this).python)

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
  }
