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

package com.readingbat.dsl

import com.pambrose.common.util.ContentSource
import com.pambrose.common.util.GitHubFile
import com.pambrose.common.util.GitHubRepo
import com.pambrose.common.util.OwnerType
import com.pambrose.common.util.md5Of
import com.readingbat.common.Constants.UNASSIGNED
import com.readingbat.common.EnvVar
import com.readingbat.common.KeyConstants.CONTENT_DSL_KEY
import com.readingbat.common.KeyConstants.keyOf
import com.readingbat.common.Property.AGENT_ENABLED
import com.readingbat.common.Property.AGENT_LAUNCH_ID
import com.readingbat.common.Property.CONTENT_CACHING_ENABLED
import com.readingbat.common.Property.DBMS_ENABLED
import com.readingbat.common.Property.IS_PRODUCTION
import com.readingbat.common.Property.IS_TESTING
import com.readingbat.common.Property.MULTI_SERVER_ENABLED
import com.readingbat.common.Property.SAVE_REQUESTS_ENABLED
import com.readingbat.dsl.ContentCaches.contentDslCache
import com.readingbat.dsl.ContentDsl.logger
import com.readingbat.server.ReadingBatServer
import com.readingbat.server.ScriptPools.kotlinScriptPool
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KFunction
import kotlin.time.measureTimedValue

/** DSL marker annotation that restricts implicit receiver scope within ReadingBat DSL blocks. */
@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
annotation class ReadingBatDslMarker

/**
 * A reference to a Content DSL file hosted on GitHub.
 *
 * Used to load remote content definitions (typically `Content.kt` files) from a GitHub repository.
 * The file is fetched via raw GitHub URLs and evaluated as a Kotlin script to produce a
 * [ReadingBatContent] instance.
 *
 * @param ownerType whether the GitHub owner is a user or organization
 * @param ownerName the GitHub user or organization name
 * @param repoName the GitHub repository name
 * @param branchName the git branch to fetch from (defaults to "master")
 * @param srcPath the path within the repo to the source directory
 * @param fileName the name of the DSL file to load
 */
class GitHubContent(
  ownerType: OwnerType,
  ownerName: String,
  repoName: String,
  branchName: String = "master",
  srcPath: String = "src/main/kotlin",
  fileName: String = "Content.kt",
) :
  GitHubFile(
    repo = GitHubRepo(ownerType, ownerName, repoName),
    branchName = branchName,
    srcPath = srcPath,
    fileName = fileName,
  )

private const val GH_PREFIX = "https://raw.githubusercontent.com"

/** Thread-safe caches for DSL code, source files, and directory listings to avoid redundant remote fetches. */
object ContentCaches {
  val contentDslCache = ConcurrentHashMap<String, String>()
  val sourceCache = ConcurrentHashMap<String, String>()
  val dirCache = ConcurrentHashMap<String, MutableList<String>>()
}

/**
 * Entry point for the ReadingBat content DSL. Creates and configures a [ReadingBatContent] instance.
 *
 * This is the top-level function that content authors call in their `Content.kt` files to define
 * the full set of programming challenges.
 *
 * ```kotlin
 * val content = readingBatContent {
 *   repo = GitHubContent(Organization, "readingbat", "readingbat-java-content")
 *   java { ... }
 *   python { ... }
 * }
 * ```
 */
fun readingBatContent(block: ReadingBatContent.() -> Unit) =
  ReadingBatContent().apply(block).apply { validate() }

/** Returns true if the server is running in production mode. Accessible from Content.kt DSL files. */
fun isProduction() = IS_PRODUCTION.getProperty(false)

/** Returns true if the server is running in test mode. Accessible from Content.kt DSL files. */
fun isTesting() = IS_TESTING.getProperty(false)

internal fun isDbmsEnabled() = DBMS_ENABLED.getProperty(false)

internal fun isSaveRequestsEnabled() =
  SAVE_REQUESTS_ENABLED.getProperty(default = true, errorOnNonInit = false) &&
    isDbmsEnabled() &&
    EnvVar.IPGEOLOCATION_KEY.isDefined()

internal fun isContentCachingEnabled() =
  CONTENT_CACHING_ENABLED.getProperty(default = false, errorOnNonInit = false) && isDbmsEnabled()

internal fun isMultiServerEnabled() = MULTI_SERVER_ENABLED.getProperty(false)

internal fun isAgentEnabled() = AGENT_ENABLED.getProperty(false)

internal fun agentLaunchId() = AGENT_LAUNCH_ID.getProperty(UNASSIGNED, false)

/**
 * Loads and evaluates a remote [ContentSource] (e.g., a [GitHubContent] file) as a Kotlin script,
 * returning the resulting [ReadingBatContent]. The DSL file must define a variable with the given
 * [variableName] (defaults to "content") that holds the [ReadingBatContent] result.
 */
fun ContentSource.eval(enclosingContent: ReadingBatContent, variableName: String = "content"): ReadingBatContent =
  enclosingContent.evalContent(this, variableName)

private fun contentDslKey(source: String) = keyOf(CONTENT_DSL_KEY, md5Of(source))

private fun fetchContentDslFromCache(source: String) =
  if (isContentCachingEnabled()) contentDslCache[contentDslKey(source)] else null

private fun saveContentDslToCache(source: String, dsl: String) {
  if (isContentCachingEnabled()) {
    contentDslCache[contentDslKey(source)] = dsl
    logger.info { "Saved ${source.removePrefix(GH_PREFIX)} to content cache" }
  }
}

/** Reads DSL source code from a [ContentSource], using the cache for remote sources when available. */
internal fun readContentDsl(contentSource: ContentSource) =
  measureTimedValue {
    if (!contentSource.remote) {
      contentSource.content
    } else {
      var dslCode = fetchContentDslFromCache(contentSource.source)
      if (dslCode != null) {
        logger.info { "Fetched ${contentSource.source.removePrefix(GH_PREFIX)} from cache" }
      } else {
        dslCode = contentSource.content
        saveContentDslToCache(contentSource.source, dslCode)
      }
      dslCode
    }
  }.let {
    logger.info { "Read content for ${contentSource.source.removePrefix(GH_PREFIX)} in ${it.duration}" }
    it.value
  }

/**
 * Evaluates DSL [code] as a Kotlin script, adding necessary imports and returning the resulting
 * [ReadingBatContent]. The [variableName] identifies the content variable in the script.
 */
internal fun evalContentDsl(
  source: String,
  variableName: String = "content",
  code: String,
) =
  runBlocking {
    measureTimedValue {
      logger.info { "Starting eval for ${source.removePrefix(GH_PREFIX)}" }
      val withImports = addImports(code, variableName)
      evalDsl(withImports, source)
    }
  }.let {
    logger.info { "Evaluated ${source.removePrefix(GH_PREFIX)} in ${it.duration}" }
    it.value
  }

/**
 * Prepends any missing imports for DSL classes and functions to the [code], and appends the
 * [variableName] expression so the script returns the content object when evaluated.
 */
internal fun addImports(code: String, variableName: String): String {
  val classImports =
    listOf(ReadingBatServer::class, GitHubContent::class)
      // .onEach { println("Checking for ${it.javaObjectType.name}") }
      .filter { code.contains("${it.javaObjectType.simpleName}(") }   // See if the class is referenced
      .map { "import ${it.javaObjectType.name}" }                           // Convert to import stmt
      .filterNot { code.contains(it) }                                      // Do not include if import already present
      .joinToString("\n")                                          // Turn into String

  val funcImports =
    listOf(::readingBatContent)
      // .onEach { println("Checking for ${it.name}") }
      .filter { code.contains("${it.name}(") }  // See if the function is referenced
      .map { "import ${it.fqMethodName}" }            // Convert to import stmt
      .filterNot { code.contains(it) }                // Do not include is import already present
      .joinToString("\n")                    // Turn into String

  val imports = listOf(classImports, funcImports).filter { it.isNotBlank() }.joinToString("\n")
  return """
      $imports${if (imports.isBlank()) "" else "\n\n"}$code
      $variableName
    """.trimMargin().lines().joinToString("\n") {
    it.trimStart()
  }
}

private val <T> KFunction<T>.fqMethodName get() = "${javaClass.packageName}.$name"

private suspend fun evalDsl(code: String, sourceName: String) =
  runCatching {
    kotlinScriptPool.eval { eval(code) as ReadingBatContent }.apply { validate() }
  }.onFailure { _ ->
    logger.info { "Error in ${sourceName.removePrefix(GH_PREFIX)}:\n$code" }
  }.getOrThrow()

/** Holder for the DSL logger. */
@Suppress("unused")
object ContentDsl {
  internal val logger = KotlinLogging.logger {}
}
