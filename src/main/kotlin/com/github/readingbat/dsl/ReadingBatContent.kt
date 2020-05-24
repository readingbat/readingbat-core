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

import com.github.pambrose.common.util.ContentRoot
import com.github.readingbat.dsl.LanguageGroup.Companion.defaultContentRoot
import com.github.readingbat.dsl.LanguageType.*


@ReadingBatDslMarker
class ReadingBatContent {
  internal var googleAnalyticsId = ""

  val python by lazy { LanguageGroup<PythonChallenge>(this, Python) }
  val java by lazy { LanguageGroup<JavaChallenge>(this, Java) }
  val kotlin by lazy { LanguageGroup<KotlinChallenge>(this, Kotlin) }

  // User properties
  var repo: ContentRoot = defaultContentRoot
  var cacheChallenges = true

  private val languageList by lazy { listOf(java, python, kotlin) }
  private val languageMap by lazy { languageList.map { it.languageType to it }.toMap() }

  internal fun hasGroups(languageType: LanguageType) = findLanguage(languageType).hasGroups()

  internal fun hasLanguage(languageType: LanguageType) = languageMap.containsKey(languageType)

  internal fun findLanguage(languageType: LanguageType) =
    languageMap[languageType] ?: throw InvalidConfigurationException("Invalid language $languageType")

  internal fun findGroup(languageType: LanguageType, groupName: String) =
    findLanguage(languageType).findGroup(groupName)

  internal fun findChallenge(languageType: LanguageType, groupName: String, challengeName: String) =
    findGroup(languageType, groupName).findChallenge(challengeName)

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
  fun <T : Challenge> include(languageGroup: LanguageGroup<T>) {
    val group = findLanguage(languageGroup.languageType) as LanguageGroup<T>
    languageGroup.challengeGroups.forEach { group.addGroup(it) }
  }

  internal fun checkLanguage(languageType: LanguageType) {
    if (!hasLanguage(languageType) || !hasGroups(languageType))
      throw InvalidConfigurationException("Invalid language: $languageType")
  }

  override fun toString() = "Content(languageList=$languageList)"

  companion object {
    internal val contentMap = mutableMapOf<String, ReadingBatContent>()
  }
}
