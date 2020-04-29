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

import com.github.readingbat.dsl.LanguageType.*

@ReadingBatDslMarker
class ReadingBatContent {
  val java = LanguageGroup(Java)
  val python = LanguageGroup(Python)
  val kotlin = LanguageGroup(Kotlin)

  private val languageList = listOf(java, python, kotlin)
  private val languageMap = languageList.map { it.languageType to it }.toMap()

  internal fun findLanguage(languageType: LanguageType) =
    languageMap[languageType] ?: throw InvalidConfigurationException("Invalid language $languageType")

  internal fun validate() = languageList.forEach { it.validate() }

  @ReadingBatDslMarker
  fun java(block: LanguageGroup.() -> Unit) {
    findLanguage(Java).apply(block)
  }

  @ReadingBatDslMarker
  fun python(block: LanguageGroup.() -> Unit) {
    findLanguage(Python).apply(block)
  }

  @ReadingBatDslMarker
  fun kotlin(block: LanguageGroup.() -> Unit) {
    findLanguage(Kotlin).apply(block)
  }

  @ReadingBatDslMarker
  operator fun LanguageGroup.unaryPlus() {
    val languageGroup = this@ReadingBatContent.findLanguage(languageType)
    challengeGroups.forEach { languageGroup.addGroup(it) }
  }

  override fun toString() = "Content(languageList=$languageList)"

  companion object {
    internal val contentMap = mutableMapOf<String, ReadingBatContent>()
  }
}

class InvalidConfigurationException(msg: String) : Exception(msg)