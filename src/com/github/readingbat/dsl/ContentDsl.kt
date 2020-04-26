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

package com.github.readingbat.dsl

import com.github.pambrose.common.script.KtsScript
import com.github.pambrose.common.util.ContentSource
import com.github.pambrose.common.util.GitHubSource
import com.github.pambrose.common.util.toDoubleQuoted
import com.github.readingbat.dsl.ReadingBatContent.Companion.remoteMap
import mu.KLogging
import kotlin.time.measureTimedValue

@DslMarker
annotation class ReadingBatDslMarker

object ContentDsl : KLogging() {
  fun readingBatContent(block: ReadingBatContent.() -> Unit) = ReadingBatContent()
    .apply(block).apply { validate() }

  fun content(source: ContentSource, variableName: String = "content"): ReadingBatContent {
    return remoteMap
      .computeIfAbsent(source.path) {
        val (code, dur) = measureTimedValue { source.content }
        logger.info { "Read content from ${source.path.toDoubleQuoted()} in $dur" }
        evalDsl(code, variableName)
      }
  }

  private fun evalDsl(code: String, variableName: String): ReadingBatContent {
    val importDecl = "import ${ContentDsl.javaClass.name}.readingBatContent"
    val code =
      (if (code.contains(importDecl)) "" else importDecl + "\n") +
          """
              $code
              $variableName
            """
    return KtsScript().run { eval(code) as ReadingBatContent }.apply { validate() }
  }
}

class GitHubContent(repo: String, fileName: String = "Content.kt") :
  GitHubSource(organization = "readingbat",
               repo = repo,
               srcPath = "src/main/kotlin",
               fileName = fileName)

