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

import com.github.readingbat.ReadingBatServer
import com.github.readingbat.dsl.GitHubContent
import com.github.readingbat.dsl.include
import com.github.readingbat.dsl.readingBatContent

object Main {
  @JvmStatic
  fun main(args: Array<String>) {
    ReadingBatServer.start(content)
  }

  val organization = "readingbat"
  val branch = "dev"

  val content =
    readingBatContent {

      +include(GitHubContent(organization, "readingbat-java-content", branch = branch)).java

      +include(GitHubContent(organization, "readingbat-python-content", branch = branch, srcPath = "src")).python

      +include(GitHubContent(organization, "readingbat-java-content", branch = branch)).kotlin

      // java {
      //+include(GitHubContent("readingbat-java-content")).java.findGroup("dd")
      // }
    }

}
