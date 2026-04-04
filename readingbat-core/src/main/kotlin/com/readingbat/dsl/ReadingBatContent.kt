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

import com.pambrose.common.dsl.KtorDsl.get
import com.pambrose.common.dsl.KtorDsl.withHttpClient
import com.pambrose.common.util.ContentRoot
import com.pambrose.common.util.ContentSource
import com.pambrose.common.util.FileSystemSource
import com.pambrose.common.util.pathOf
import com.pambrose.common.util.pluralize
import com.readingbat.common.Constants.NO_TRACK_HEADER
import com.readingbat.common.FunctionInfo
import com.readingbat.common.PropertyNames.CONTENT
import com.readingbat.dsl.LanguageType.Java
import com.readingbat.dsl.LanguageType.Kotlin
import com.readingbat.dsl.LanguageType.Python
import com.readingbat.dsl.challenge.Challenge
import com.readingbat.dsl.challenge.JavaChallenge
import com.readingbat.dsl.challenge.KotlinChallenge
import com.readingbat.dsl.challenge.PythonChallenge
import com.readingbat.server.ChallengeName
import com.readingbat.server.GroupName
import com.readingbat.server.Language
import com.readingbat.server.LanguageName
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.plusAssign
import kotlin.time.measureTimedValue

/**
 * Top-level container for all ReadingBat content, serving as the root of the DSL hierarchy.
 *
 * A [ReadingBatContent] instance holds three [LanguageGroup]s -- [java], [python], and [kotlin] --
 * each containing the challenge groups and individual challenges for that language. Content authors
 * create an instance via the [readingBatContent] builder function in their `Content.kt` DSL file.
 *
 * The DSL hierarchy is: `ReadingBatContent` -> [LanguageGroup] -> [ChallengeGroup] -> [Challenge].
 *
 * Example usage:
 * ```kotlin
 * val content = readingBatContent {
 *   repo = GitHubContent(Organization, "readingbat", "readingbat-java-content")
 *   java {
 *     group("Warmup-1") {
 *       packageName = "warmup1"
 *       description = "Simple warmup problems"
 *       includeFiles = "*.java"
 *     }
 *   }
 * }
 * ```
 */
@ReadingBatDslMarker
class ReadingBatContent {
  internal val timeStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("M/d/y H:m:ss"))
  private val languageList by lazy { listOf(java, python, kotlin) }
  private val languageMap by lazy { languageList.associateBy { it.languageType } }

  // contentMap will prevent reading the same content multiple times
  internal val contentMap = ConcurrentHashMap<String, ReadingBatContent>()
  internal val functionInfoMap = ConcurrentHashMap<Int, FunctionInfo>()

  internal var maxHistoryLength = 10
  internal var maxClassCount = 25

  /** Language group for Python challenges. Accessible in the DSL as a property or block receiver. */
  val python by lazy { LanguageGroup<PythonChallenge>(this, Python) }

  /** Language group for Java challenges. Accessible in the DSL as a property or block receiver. */
  val java by lazy { LanguageGroup<JavaChallenge>(this, Java) }

  /** Language group for Kotlin challenges. Accessible in the DSL as a property or block receiver. */
  val kotlin by lazy { LanguageGroup<KotlinChallenge>(this, Kotlin) }

  val languages by lazy { listOf(python, java, kotlin) }

  val cacheChallenges get() = isProduction() || isTesting()

  /**
   * The default content source for all languages. Can be a [FileSystemSource] for local files
   * or a [GitHubRepo][com.pambrose.common.util.GitHubRepo] for remote content.
   * Individual [LanguageGroup]s can override this with their own repo.
   */
  var repo: ContentRoot = FileSystemSource("./")

  /** Default git branch name used when loading content from GitHub. Overridable per language group. */
  var branchName = "master"

  internal fun clearContentMap() {
    logger.info { "Clearing content map" }
    contentMap.clear()
  }

  internal fun clearSourcesMap() {
    logger.info { "Clearing Challenge cache" }
    functionInfoMap.clear()
  }

  internal fun hasLanguage(languageType: LanguageType) = languageMap.containsKey(languageType)

  internal operator fun contains(languageType: LanguageType) = this[languageType].isNotEmpty()

  internal fun findLanguage(languageName: LanguageName): LanguageGroup<out Challenge> =
    findLanguage(languageName.toLanguageType())

  internal fun findLanguage(languageType: LanguageType): LanguageGroup<out Challenge> =
    languageMap[languageType] ?: error("Invalid language $languageType")

  internal fun findGroup(languageName: LanguageName, groupName: GroupName): ChallengeGroup<out Challenge> =
    findLanguage(languageName.toLanguageType()).findGroup(groupName.value)

  private fun findGroup(languageType: LanguageType, groupName: GroupName): ChallengeGroup<out Challenge> =
    findLanguage(languageType).findGroup(groupName.value)

  internal fun findGroup(groupLoc: Language.Group): ChallengeGroup<out Challenge> =
    findLanguage(groupLoc.languageType).findGroup(groupLoc.groupName.value)

  internal fun findChallenge(challengeLoc: Language.Group.Challenge): Challenge =
    findGroup(challengeLoc.group).findChallenge(challengeLoc.challengeName.value)

  internal fun findChallenge(
    languageName: LanguageName,
    groupName: GroupName,
    challengeName: ChallengeName,
  ): Challenge =
    findGroup(languageName, groupName).findChallenge(challengeName.value)

  internal operator fun get(languageType: LanguageType): LanguageGroup<out Challenge> = findLanguage(languageType)

  internal operator fun get(groupLoc: Language.Group): ChallengeGroup<out Challenge> = findGroup(groupLoc)

  internal operator fun get(languageType: LanguageType, groupName: GroupName): ChallengeGroup<out Challenge> =
    findGroup(languageType, groupName)

  internal operator fun get(challengeLoc: Language.Group.Challenge): Challenge = findChallenge(challengeLoc)

  internal operator fun get(languageName: LanguageName, groupName: GroupName, challengeName: ChallengeName): Challenge =
    findChallenge(languageName, groupName, challengeName)

  internal fun validate() = languageList.forEach { it.validate() }

  @Suppress("unused")
  internal fun functionInfoByMd5(md5: String) =
    functionInfoMap.asSequence().firstOrNull { it.component2().challengeMd5.value == md5 }?.value

  /** Opens a DSL block for defining Java challenge groups. */
  fun java(block: LanguageGroup<JavaChallenge>.() -> Unit) = java.run(block)

  /** Opens a DSL block for defining Python challenge groups. */
  fun python(block: LanguageGroup<PythonChallenge>.() -> Unit) = python.run(block)

  /** Opens a DSL block for defining Kotlin challenge groups. */
  fun kotlin(block: LanguageGroup<KotlinChallenge>.() -> Unit) = kotlin.run(block)

  /**
   * Merges all challenge groups from another [LanguageGroup] into this content's corresponding language.
   * Used with the unary `+` operator to include externally defined language groups.
   */
  @Suppress("UNCHECKED_CAST")
  operator fun <T : Challenge> LanguageGroup<T>.unaryPlus() {
    val languageGroup = this@ReadingBatContent.findLanguage(languageType) as LanguageGroup<T>
    challengeGroups.forEach { languageGroup.addGroup(it) }
  }

  /**
   * Includes all challenge groups from [languageGroup] into this content. An optional [namePrefix]
   * is prepended to each group name to avoid naming collisions when merging content from multiple sources.
   */
  fun <T : Challenge> include(languageGroup: LanguageGroup<T>, namePrefix: String = "") {
//    val langList = languageList
//    val langMap = languageMap

    @Suppress("UNCHECKED_CAST")
    val langGroup = findLanguage(languageGroup.languageType) as LanguageGroup<T>
    languageGroup.challengeGroups
      .forEach {
        if (namePrefix.isNotBlank())
          it.namePrefix = namePrefix
        langGroup.addGroup(it)
      }
  }

  internal fun checkLanguage(languageType: LanguageType) {
    if (languageType !in this || this[languageType].isEmpty())
      error("Invalid language: $languageType")
  }

  internal fun loadChallenges(
    languageType: LanguageType,
    log: (String) -> Unit,
    prefix: String = "",
    useWebApi: Boolean = false,
  ) =
    measureTimedValue {
      val cnt = AtomicInt(0)
      runBlocking {
        HttpClient(CIO) { expectSuccess = false }
          .use { httpClient ->
            findLanguage(languageType).challengeGroups
              .flatMap { it.challenges }
              .forEach { challenge ->
                if (useWebApi) {
                  withHttpClient(httpClient) {
                    val url = pathOf(prefix, CONTENT, challenge.path)
                    logger.info { "Fetching: $url" }
                    get(url, setUp = { header(NO_TRACK_HEADER, "") }) { response ->
                      val body = response.bodyAsText()
                      logger.info { "Response: ${response.status} ${body.length} chars" }
                      cnt += 1
                    }
                  }
                } else {
                  logger.info { "Loading: ${challenge.path}" }
                  log("Loading: ${challenge.path}")
                  challenge.functionInfo()
                  cnt += 1
                }
              }
          }
      }
      cnt.load()
    }.let {
      "${it.value} $languageType ${"exercise".pluralize(it.value)} loaded in ${it.duration}"
    }

  internal fun evalContent(contentSource: ContentSource, variableName: String): ReadingBatContent =
    // Catch exceptions so that remote code does not bring down the server
    runCatching {
      val src = contentSource.source
      contentMap.computeIfAbsent(src) {
        logger.info { "Computing contentMap element for $src" }
        val dslCode = readContentDsl(contentSource)
        evalContentDsl(src, variableName, dslCode)
      }
    }.getOrElse { e ->
      logger.error(e) { "While evaluating: $this" }
      ReadingBatContent()
    }

  override fun toString() = "Content(languageList=$languageList)"

  companion object {
    private val logger = KotlinLogging.logger {}
  }
}
