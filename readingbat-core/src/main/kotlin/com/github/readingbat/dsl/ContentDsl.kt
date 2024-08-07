/*
 * Copyright © 2023 Paul Ambrose (pambrose@mac.com)
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

import com.github.pambrose.common.redis.RedisUtils.withNonNullRedisPool
import com.github.pambrose.common.redis.RedisUtils.withRedisPool
import com.github.pambrose.common.util.ContentSource
import com.github.pambrose.common.util.GitHubFile
import com.github.pambrose.common.util.GitHubRepo
import com.github.pambrose.common.util.OwnerType
import com.github.pambrose.common.util.isNotNull
import com.github.pambrose.common.util.md5Of
import com.github.readingbat.common.Constants.UNASSIGNED
import com.github.readingbat.common.EnvVar
import com.github.readingbat.common.KeyConstants.CONTENT_DSL_KEY
import com.github.readingbat.common.KeyConstants.keyOf
import com.github.readingbat.common.Property.AGENT_ENABLED
import com.github.readingbat.common.Property.AGENT_LAUNCH_ID
import com.github.readingbat.common.Property.CONTENT_CACHING_ENABLED
import com.github.readingbat.common.Property.DBMS_ENABLED
import com.github.readingbat.common.Property.IS_PRODUCTION
import com.github.readingbat.common.Property.IS_TESTING
import com.github.readingbat.common.Property.MULTI_SERVER_ENABLED
import com.github.readingbat.common.Property.REDIS_ENABLED
import com.github.readingbat.common.Property.SAVE_REQUESTS_ENABLED
import com.github.readingbat.dsl.ContentDsl.logger
import com.github.readingbat.server.ReadingBatServer
import com.github.readingbat.server.ReadingBatServer.redisPool
import com.github.readingbat.server.ScriptPools.kotlinScriptPool
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import kotlin.reflect.KFunction
import kotlin.time.measureTimedValue

@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
annotation class ReadingBatDslMarker

class GitHubContent(
  ownerType: OwnerType,
  ownerName: String,
  repo: String,
  branch: String = "master",
  srcPath: String = "src/main/kotlin",
  fileName: String = "Content.kt",
) :
  GitHubFile(
    GitHubRepo(ownerType, ownerName, repo),
    branchName = branch,
    srcPath = srcPath,
    fileName = fileName,
  )

private const val GH_PREFIX = "https://raw.githubusercontent.com"

fun readingBatContent(block: ReadingBatContent.() -> Unit) =
  ReadingBatContent().apply(block).apply { validate() }

// This is accessible from the Content.kt descriptions
fun isProduction() = IS_PRODUCTION.getProperty(false)

fun isTesting() = IS_TESTING.getProperty(false)

internal fun isDbmsEnabled() = DBMS_ENABLED.getProperty(false)

internal fun isRedisEnabled() = REDIS_ENABLED.getProperty(false)

internal fun isSaveRequestsEnabled() =
  SAVE_REQUESTS_ENABLED.getProperty(
    default = true,
    errorOnNonInit = false,
  ) &&
    isDbmsEnabled() &&
    EnvVar.IPGEOLOCATION_KEY.isDefined()

internal fun isContentCachingEnabled() =
  CONTENT_CACHING_ENABLED.getProperty(
    default = false,
    errorOnNonInit = false,
  ) &&
    isDbmsEnabled() &&
    isRedisEnabled()

internal fun isMultiServerEnabled() = MULTI_SERVER_ENABLED.getProperty(false)

internal fun isAgentEnabled() = AGENT_ENABLED.getProperty(false)

internal fun agentLaunchId() = AGENT_LAUNCH_ID.getProperty(UNASSIGNED, false)

fun ContentSource.eval(enclosingContent: ReadingBatContent, variableName: String = "content"): ReadingBatContent =
  enclosingContent.evalContent(this, variableName)

private fun contentDslKey(source: String) = keyOf(CONTENT_DSL_KEY, md5Of(source))

private fun fetchContentDslFromRedis(source: String) =
  if (isContentCachingEnabled()) redisPool?.withRedisPool { it?.get(contentDslKey(source)) } else null

private fun saveContentDslToRedis(source: String, dsl: String) {
  if (isContentCachingEnabled()) {
    redisPool?.withNonNullRedisPool(true) { redis ->
      redis.set(contentDslKey(source), dsl)
      logger.info { "Saved ${source.removePrefix(GH_PREFIX)} to redis" }
    }
  }
}

internal fun readContentDsl(contentSource: ContentSource) =
  measureTimedValue {
    if (!contentSource.remote) {
      contentSource.content
    } else {
      var dslCode = fetchContentDslFromRedis(contentSource.source)
      if (dslCode.isNotNull()) {
        logger.info { "Fetched ${contentSource.source.removePrefix(GH_PREFIX)} from redis cache" }
      } else {
        dslCode = contentSource.content
        saveContentDslToRedis(contentSource.source, dslCode)
      }
      dslCode
    }
  }.let {
    logger.info { "Read content for ${contentSource.source.removePrefix(GH_PREFIX)} in ${it.duration}" }
    it.value
  }

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

@Suppress("unused")
object ContentDsl {
  internal val logger = KotlinLogging.logger {}
}
