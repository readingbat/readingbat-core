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
import com.github.readingbat.dsl.ReadingBatContent.Companion.contentMap
import mu.KotlinLogging
import kotlin.time.measureTimedValue

@DslMarker
annotation class ReadingBatDslMarker

class GitHubContent(repo: String, fileName: String = "Content.kt") :
  GitHubSource(organization = "readingbat",
               repo = repo,
               srcPath = "src/main/kotlin",
               fileName = fileName)

fun readingBatContent(block: ReadingBatContent.() -> Unit) = ReadingBatContent()
  .apply(block).apply { validate() }

private val logger = KotlinLogging.logger {}

fun include(source: ContentSource, variableName: String = "content"): ReadingBatContent {
  return contentMap
    .computeIfAbsent(source.path) {
      val (code, dur) = measureTimedValue { source.content }
      logger.info { """Read content from "${source.path}" in $dur""" }
      evalDsl(code, source.path, variableName)
    }
}

private fun evalDsl(code: String, sourceName: String, variableName: String): ReadingBatContent {
  val method = ::readingBatContent
  val packageName = method.javaClass.packageName
  val methodName = method.name
  val importDecl = "import $packageName.$methodName"
  println(importDecl)
  val processed =
    (if (code.contains(importDecl)) "" else importDecl + "\n") +
        """
          $code
          $variableName
        """
  try {
    return KtsScript().run { eval(processed) as ReadingBatContent }.apply { validate() }
  } catch (e: Throwable) {
    logger.info { "Error in $sourceName:\n$processed" }
    throw e
  }
}
