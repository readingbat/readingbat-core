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

import com.github.pambrose.common.dsl.KtorDsl.get
import com.github.pambrose.common.dsl.KtorDsl.withHttpClient
import com.github.pambrose.common.util.ContentRoot
import com.github.pambrose.common.util.ContentSource
import com.github.pambrose.common.util.FileSystemSource
import com.github.pambrose.common.util.pluralize
import com.github.readingbat.common.Constants.NO_TRACK
import com.github.readingbat.common.FunctionInfo
import com.github.readingbat.common.PropertyNames.CONTENT
import com.github.readingbat.dsl.LanguageType.Java
import com.github.readingbat.dsl.LanguageType.Kotlin
import com.github.readingbat.dsl.LanguageType.Python
import com.github.readingbat.server.ChallengeName
import com.github.readingbat.server.GroupName
import com.github.readingbat.server.Language
import com.github.readingbat.server.LanguageName
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking
import mu.KLogging
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.measureTimedValue


@ReadingBatDslMarker
class ReadingBatContent {
  // contentMap will prevent reading the same content multiple times
  private val contentMap = ConcurrentHashMap<String, ReadingBatContent>()
  private val languageList by lazy { listOf(java, python, kotlin) }
  private val languageMap by lazy { languageList.map { it.languageType to it }.toMap() }

  internal val sourcesMap = ConcurrentHashMap<Int, FunctionInfo>()
  internal val timeStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("M/d/y H:m:ss"))
  internal var sendGridPrefix = ""
  internal var googleAnalyticsId = ""
  internal var maxHistoryLength = 10
  internal var maxClassCount = 25
  internal var dslFileName = ""
  internal var dslVariableName = ""
  internal var ktorPort = 0
  internal var ktorWatch = ""
  internal var grafanaUrl = ""
  internal var prometheusUrl = ""

  // Accessible from Content.kt DSL
  val python by lazy { LanguageGroup<PythonChallenge>(this, Python) }
  val java by lazy { LanguageGroup<JavaChallenge>(this, Java) }
  val kotlin by lazy { LanguageGroup<KotlinChallenge>(this, Kotlin) }

  // User properties
  var cacheChallenges = isProduction()

  // These are defaults and can be overridden in language specific section
  //var repo: ContentRoot = defaultContentRoot # Makes repo a required value
  var repo: ContentRoot = FileSystemSource("./")
  var branchName = "master"

  internal fun clearContentMap() {
    logger.info { "Clearing content map" }
    contentMap.clear()
  }

  internal fun clearSourcesMap() {
    logger.info { "Clearing Challenge cache" }
    sourcesMap.clear()
  }

  internal fun hasLanguage(languageType: LanguageType) = languageMap.containsKey(languageType)

  internal operator fun contains(languageType: LanguageType) = this[languageType].isNotEmpty()

  internal fun findLanguage(languageType: LanguageType): LanguageGroup<out Challenge> =
    languageMap[languageType] ?: throw InvalidConfigurationException("Invalid language $languageType")

  internal fun findGroup(groupLoc: Language.Group): ChallengeGroup<out Challenge> =
    findLanguage(groupLoc.languageType).findGroup(groupLoc.groupName.value)

  internal fun findGroup(languageType: LanguageType, groupName: GroupName): ChallengeGroup<out Challenge> =
    findLanguage(languageType).findGroup(groupName.value)

  internal fun findChallenge(challengeLoc: Language.Group.Challenge): Challenge =
    findGroup(challengeLoc.group).findChallenge(challengeLoc.challengeName.value)

  internal fun findChallenge(languageName: LanguageName,
                             groupName: GroupName,
                             challengeName: ChallengeName): Challenge =
    findGroup(languageName.toLanguageType(), groupName).findChallenge(challengeName.value)

  internal operator fun get(languageType: LanguageType): LanguageGroup<out Challenge> = findLanguage(languageType)

  internal operator fun get(groupLoc: Language.Group): ChallengeGroup<out Challenge> = findGroup(groupLoc)

  internal operator fun get(languageType: LanguageType, groupName: GroupName): ChallengeGroup<out Challenge> =
    findGroup(languageType, groupName)

  internal operator fun get(challengeLoc: Language.Group.Challenge): Challenge = findChallenge(challengeLoc)

  internal operator fun get(languageName: LanguageName, groupName: GroupName, challengeName: ChallengeName): Challenge =
    findChallenge(languageName, groupName, challengeName)

  internal fun validate() = languageList.forEach { it.validate() }

  @ReadingBatDslMarker
  fun java(block: LanguageGroup<JavaChallenge>.() -> Unit) = java.run(block)

  @ReadingBatDslMarker
  fun python(block: LanguageGroup<PythonChallenge>.() -> Unit) = python.run(block)

  @ReadingBatDslMarker
  fun kotlin(block: LanguageGroup<KotlinChallenge>.() -> Unit) = kotlin.run(block)

  @ReadingBatDslMarker
  operator fun <T : Challenge> LanguageGroup<T>.unaryPlus() {
    val languageGroup = this@ReadingBatContent.findLanguage(languageType) as LanguageGroup<T>
    challengeGroups.forEach { languageGroup.addGroup(it) }
  }

  @ReadingBatDslMarker
  fun <T : Challenge> include(languageGroup: LanguageGroup<T>, namePrefix: String = "") {
    val group = findLanguage(languageGroup.languageType) as LanguageGroup<T>
    languageGroup.challengeGroups
      .forEach {
        if (namePrefix.isNotBlank())
          it.namePrefix = namePrefix
        group.addGroup(it)
      }
  }

  internal fun checkLanguage(languageType: LanguageType) {
    if (languageType !in this || this[languageType].isEmpty())
      throw InvalidConfigurationException("Invalid language: $languageType")
  }

  internal fun loadChallenges(prefix: String, languageType: LanguageType, useWebApi: Boolean = true) =
    measureTimedValue {
      val cnt = AtomicInteger(0)
      runBlocking {
        findLanguage(languageType).challengeGroups.forEach { challengeGroup ->
          challengeGroup.challenges.forEach { challenge ->
            if (useWebApi) {
              HttpClient(CIO)
                .use { httpClient ->
                  withHttpClient(httpClient) {
                    val url = "$prefix/$CONTENT/${challenge.path}"
                    logger.info { "Fetching: $url" }
                    get(url, setUp = { header(NO_TRACK, "") }) { response ->
                      val body = response.readText()
                      logger.info { "Response: ${response.status} ${body.length} chars" }
                      cnt.incrementAndGet()
                    }
                  }
                }
            }
            else {
              logger.info { "Loading: ${challenge.path}" }
              challenge.functionInfo(this@ReadingBatContent)
              cnt.incrementAndGet()
            }
          }
        }
      }
      cnt.get()
    }.let {
      "${it.value} $languageType ${"exercise".pluralize(it.value)} loaded in ${it.duration}".also { logger.info { it } }
    }

  internal fun evalContent(contentSource: ContentSource, variableName: String): ReadingBatContent =
    try {
      // Catch exceptions so that remote code does not bring down the server
      contentMap.computeIfAbsent(contentSource.source) { readContentDsl(contentSource, variableName) }
    } catch (e: Throwable) {
      logger.error(e) { "While evaluating: $this" }
      ReadingBatContent()
    }

  override fun toString() = "Content(languageList=$languageList)"

  companion object : KLogging()
}