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

import com.github.pambrose.common.script.KotlinScript
import com.github.pambrose.common.util.ContentSource
import com.github.pambrose.common.util.GitHubSource
import com.github.readingbat.ReadingBatServer
import com.github.readingbat.dsl.ReadingBatContent.Companion.contentMap
import mu.KotlinLogging
import kotlin.reflect.KFunction
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
      val withImports = addImports(code, variableName)
      evalDsl(withImports, source.path)
    }
}

internal fun addImports(code: String, variableName: String): String {
  val classImports =
    listOf(ReadingBatServer::class, GitHubContent::class)
      //.onEach { println("Checking for ${it.javaObjectType.name}") }
      .filter { code.contains("${it.javaObjectType.simpleName}(") }   // See if the class is referenced
      .map { "import ${it.javaObjectType.name}" }                     // Convert to import stmt
      .filter { !code.contains(it) }                                  // Do not include is import already present
      .joinToString("\n")                                             // Turn into String

  val funcImports =
    listOf(::readingBatContent, ::include)
      .filter { code.contains("${it.name}(") }  // See if the function is referenced
      .map { "import ${it.fqMethodName}" }      // Convert to import stmt
      .filter { !code.contains(it) }            // Do not include is import already present
      .joinToString("\n")                       // Turn into String

  val imports = listOf(classImports, funcImports).filter { !it.isBlank() }.joinToString("\n")
  return """
      $imports${if (imports.isBlank()) "" else "\n\n"}$code
      $variableName
    """.trimMargin().split("\n").joinToString("\n") { it.trimStart() }
}

private val <T> KFunction<T>.fqMethodName get() = "${javaClass.packageName}.$name"

private fun evalDsl(code: String, sourceName: String) =
  try {
    KotlinScript()
      .run {
        eval(code) as ReadingBatContent
      }.apply {
        validate()
      }
  } catch (e: Throwable) {
    logger.info { "Error in $sourceName:\n$code" }
    throw e
  }
