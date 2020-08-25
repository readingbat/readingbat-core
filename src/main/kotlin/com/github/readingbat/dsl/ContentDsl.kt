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

import com.github.pambrose.common.util.ContentSource
import com.github.pambrose.common.util.GitHubFile
import com.github.pambrose.common.util.GitHubRepo
import com.github.pambrose.common.util.OwnerType
import com.github.readingbat.common.PropertyNames.AGENT_ENABLED
import com.github.readingbat.common.PropertyNames.AGENT_LAUNCH_ID
import com.github.readingbat.common.PropertyNames.IS_PRODUCTION
import com.github.readingbat.common.ScriptPools.kotlinScriptPool
import com.github.readingbat.server.ReadingBatServer
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import kotlin.reflect.KFunction
import kotlin.time.measureTimedValue

@DslMarker
annotation class ReadingBatDslMarker

class GitHubContent(ownerType: OwnerType,
                    ownerName: String,
                    repo: String,
                    branch: String = "master",
                    srcPath: String = "src/main/kotlin",
                    fileName: String = "Content.kt") :
  GitHubFile(GitHubRepo(ownerType, ownerName, repo),
             branchName = branch,
             srcPath = srcPath,
             fileName = fileName)

fun readingBatContent(block: ReadingBatContent.() -> Unit) =
  ReadingBatContent().apply(block).apply { validate() }

private val logger = KotlinLogging.logger {}

// This is accessible from the Content.kt descriptions
fun isProduction() = System.getProperty(IS_PRODUCTION)?.toBoolean() ?: false

fun isAgentEnabled() = System.getProperty(AGENT_ENABLED)?.toBoolean() ?: false

fun agentLaunchId() = System.getProperty(AGENT_LAUNCH_ID) ?: "unassigned"

fun ContentSource.eval(enclosingContent: ReadingBatContent, variableName: String = "content"): ReadingBatContent =
  enclosingContent.evalContent(this, variableName)

internal fun readContentDsl(contentSource: ContentSource, variableName: String = "content"): ReadingBatContent {
  val (code, dur) = measureTimedValue { contentSource.content }
  logger.info { """Read content from "${contentSource.source}" in $dur""" }
  val withImports = addImports(code, variableName)
  return runBlocking { evalDsl(withImports, contentSource.source) }
}

internal fun addImports(code: String, variableName: String): String {
  val classImports =
    listOf(ReadingBatServer::class, GitHubContent::class)
      //.onEach { println("Checking for ${it.javaObjectType.name}") }
      .filter { code.contains("${it.javaObjectType.simpleName}(") }   // See if the class is referenced
      .map { "import ${it.javaObjectType.name}" }                     // Convert to import stmt
      .filterNot { code.contains(it) }                                // Do not include if import already present
      .joinToString("\n")                                             // Turn into String

  val funcImports =
    listOf(::readingBatContent)
      .filter { code.contains("${it.name}(") }  // See if the function is referenced
      .map { "import ${it.fqMethodName}" }      // Convert to import stmt
      .filterNot { code.contains(it) }          // Do not include is import already present
      .joinToString("\n")                       // Turn into String

  val imports = listOf(classImports, funcImports).filter { it.isNotBlank() }.joinToString("\n")
  return """
      $imports${if (imports.isBlank()) "" else "\n\n"}$code
      $variableName
    """.trimMargin().lines().joinToString("\n") { it.trimStart() }
}

private val <T> KFunction<T>.fqMethodName get() = "${javaClass.packageName}.$name"

private suspend fun evalDsl(code: String, sourceName: String) =
  try {
    kotlinScriptPool
      .eval {
        eval(code) as ReadingBatContent
      }.apply {
        validate()
      }
  } catch (e: Throwable) {
    logger.info { "Error in $sourceName:\n$code" }
    throw e
  }