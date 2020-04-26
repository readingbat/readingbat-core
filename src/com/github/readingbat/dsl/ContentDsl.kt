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
import com.github.pambrose.common.util.ensureSuffix
import com.github.pambrose.common.util.toDoubleQuoted
import com.github.readingbat.Constants.github
import com.github.readingbat.Constants.githubUserContent
import com.github.readingbat.dsl.ReadingBatContent.Companion.localMap
import com.github.readingbat.dsl.ReadingBatContent.Companion.remoteMap
import mu.KLogging
import java.net.URL
import kotlin.time.measureTimedValue

@DslMarker
annotation class ReadingBatDslMarker

object ContentDsl : KLogging() {
  fun readingBatContent(block: ReadingBatContent.() -> Unit) = ReadingBatContent()
    .apply(block).apply { validate() }

  fun remoteContent(scheme: String = "https://",
                    domainName: String = github,
                    organization: String = "readingbat",
                    repo: String,
                    branch: String = "master",
                    srcPath: String = "src/main/kotlin",
                    fileName: String = "Content.kt",
                    variableName: String = "content"): ReadingBatContent {
    val path = scheme + domainName.replace(github, githubUserContent).ensureSuffix("/") +
        listOf(organization, repo, branch, srcPath, fileName.ensureSuffix(".kt")).toPath(false)
    return remoteMap
      .computeIfAbsent(path) {
        val (code, dur) = measureTimedValue { URL(it).readText() }
        logger.info { "Read content from ${path.toDoubleQuoted()} in $dur" }
        evalDsl(code, variableName)
      }
  }

  fun localContent(filePath: String = "Content.kt", variableName: String = "content") =
    localMap.computeIfAbsent(filePath) { evalDsl(URL(it).readText(), variableName) }

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

  fun List<String>.toPath(addTrailingSeparator: Boolean = true, separator: CharSequence = "/") =
    mapIndexed { i, s -> if (i != 0 && s.startsWith(separator)) s.substring(1) else s }
      .mapIndexed { i, s -> if (i < size - 1 || addTrailingSeparator) s.ensureSuffix(separator) else s }
      .joinToString("")
}