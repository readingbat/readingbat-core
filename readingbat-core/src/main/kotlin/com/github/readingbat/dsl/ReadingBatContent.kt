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

import com.github.pambrose.common.dsl.KtorDsl.get
import com.github.pambrose.common.dsl.KtorDsl.withHttpClient
import com.github.pambrose.common.util.ContentRoot
import com.github.pambrose.common.util.ContentSource
import com.github.pambrose.common.util.FileSystemSource
import com.github.pambrose.common.util.pathOf
import com.github.pambrose.common.util.pluralize
import com.github.readingbat.common.Constants.NO_TRACK_HEADER
import com.github.readingbat.common.FunctionInfo
import com.github.readingbat.common.PropertyNames.CONTENT
import com.github.readingbat.dsl.LanguageType.Java
import com.github.readingbat.dsl.LanguageType.Kotlin
import com.github.readingbat.dsl.LanguageType.Python
import com.github.readingbat.dsl.challenge.Challenge
import com.github.readingbat.dsl.challenge.JavaChallenge
import com.github.readingbat.dsl.challenge.KotlinChallenge
import com.github.readingbat.dsl.challenge.PythonChallenge
import com.github.readingbat.server.ChallengeName
import com.github.readingbat.server.GroupName
import com.github.readingbat.server.Language
import com.github.readingbat.server.LanguageName
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

  // Accessible from Content.kt DSL
  val python by lazy { LanguageGroup<PythonChallenge>(this, Python) }
  val java by lazy { LanguageGroup<JavaChallenge>(this, Java) }
  val kotlin by lazy { LanguageGroup<KotlinChallenge>(this, Kotlin) }

  val languages by lazy { listOf(python, java, kotlin) }

  val cacheChallenges get() = isProduction() || isTesting()

  // User properties
  // These are defaults and can be overridden in language specific section
  // var repo: ContentRoot = defaultContentRoot # Makes repo a required value
  var repo: ContentRoot = FileSystemSource("./")
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

  fun java(block: LanguageGroup<JavaChallenge>.() -> Unit) = java.run(block)

  fun python(block: LanguageGroup<PythonChallenge>.() -> Unit) = python.run(block)

  fun kotlin(block: LanguageGroup<KotlinChallenge>.() -> Unit) = kotlin.run(block)

  @Suppress("UNCHECKED_CAST")
  operator fun <T : Challenge> LanguageGroup<T>.unaryPlus() {
    val languageGroup = this@ReadingBatContent.findLanguage(languageType) as LanguageGroup<T>
    challengeGroups.forEach { languageGroup.addGroup(it) }
  }

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
        findLanguage(languageType).challengeGroups
          .forEach { challengeGroup ->
            challengeGroup.challenges
              .forEach { challenge ->
                if (useWebApi) {
                  HttpClient(CIO) { expectSuccess = false }
                    .use { httpClient ->
                      withHttpClient(httpClient) {
                        val url = pathOf(prefix, CONTENT, challenge.path)
                        logger.info { "Fetching: $url" }
                        get(url, setUp = { header(NO_TRACK_HEADER, "") }) { response ->
                          val body = response.bodyAsText()
                          logger.info { "Response: ${response.status} ${body.length} chars" }
                          cnt += 1
                        }
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
