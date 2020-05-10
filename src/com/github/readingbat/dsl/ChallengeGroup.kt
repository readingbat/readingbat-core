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

import com.github.readingbat.InvalidConfigurationException
import com.github.readingbat.InvalidPathException
import com.github.readingbat.dsl.Challenge.Companion.challenge
import com.github.readingbat.dsl.ReturnType.Runtime
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet
import mu.KLogging
import kotlin.reflect.KProperty

@ReadingBatDslMarker
class ChallengeGroup<T : Challenge>(internal val languageGroup: LanguageGroup<T>, internal val groupName: String) {
  internal val languageType = languageGroup.languageType
  internal val readingBatContent = languageGroup.readingBatContent
  internal val challenges = mutableListOf<T>()

  private val prefix by lazy { "${languageType.lowerName}/$groupName" }
  internal val parsedDescription
      by lazy {
        val options = MutableDataSet().apply { set(HtmlRenderer.SOFT_BREAK, "<br />\n") }
        val parser = Parser.builder(options).build()
        val renderer = HtmlRenderer.builder(options).build()
        val document = parser.parse(description.trimIndent())
        renderer.render(document)
      }

  private class IncludeFiles(val languageType: LanguageType, val includeList: MutableList<PatternReturnType>) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>) = includeList.toString()
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
      if (languageType.isJava())
        includeList += PatternReturnType(value, Runtime)
      else
        throw InvalidConfigurationException("Use includeFilesWithType instead of includeFiles for ${languageType.lowerName} challenges")
    }
  }

  private class IncludeFilesWithType(val languageType: LanguageType, val includeList: MutableList<PatternReturnType>) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): PatternReturnType = PatternReturnType("", Runtime)
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: PatternReturnType) {
      if (!languageType.isJava())
        includeList += value
      else
        throw InvalidConfigurationException("Use includeFiles instead of includeFilesWithType for ${languageType.lowerName} challenges")
    }
  }

  internal val includeList = mutableListOf<PatternReturnType>()

  // User properties
  var packageName = ""
  var description = ""
  var includeFiles by IncludeFiles(languageType, includeList)
  var includeFilesWithType by IncludeFilesWithType(languageType, includeList)

  fun hasChallenge(name: String) = challenges.any { it.name == name }

  fun removeChallenge(name: String) {
    val pos =
      challenges
        .asSequence()
        .mapIndexed { i, challenge -> i to challenge }
        .first { it.second.name == name }
        .first
    challenges.removeAt(pos)
  }

  fun findChallenge(name: String): T =
    challenges.firstOrNull { it.name == name }
      ?: throw InvalidPathException("Challenge $prefix/$name not found.")

  @ReadingBatDslMarker
  fun T.unaryPlus() {
    this@ChallengeGroup.checkChallengeName(name)
    this@ChallengeGroup.challenges += this
  }

  //@ReadingBatDslMarker
  //fun includeFiles(vararg patterns: String) = import(patterns.toList())

  data class PatternReturnType(val pattern: String, val returnType: ReturnType)

  @ReadingBatDslMarker
  infix fun String.returns(returnType: ReturnType) = PatternReturnType(this, returnType)


  internal fun addChallenge(challengeNames: List<LanguageGroup.ChallengeFile>) {
    challengeNames
      .forEach { challengeFile ->
        val challengeName = challengeFile.fileName.split(".").first()
        if (checkChallengeName(challengeName, false)) {
          logger.debug { "Adding $challengeName" }
          val challenge = challenge(this, challengeName, true)
          // Skip this next step for Java because returnType is calculated
          when {
            languageType.isPython() -> (challenge as PythonChallenge).apply { returnType = challengeFile.returnType }
            languageType.isKotlin() -> (challenge as KotlinChallenge).apply { returnType = challengeFile.returnType }
          }
          challenges += challenge as T
        }
      }
  }

  private fun checkChallengeName(challengeName: String, throwExceptionIfPresent: Boolean = true): Boolean {
    if (hasChallenge(challengeName)) {
      val challenge = findChallenge(challengeName)
      if (challenge.replaceable) {
        removeChallenge(challengeName)
      }
      else {
        if (throwExceptionIfPresent)
          throw InvalidConfigurationException("Challenge $prefix/$challengeName already exists")
        else
          return false
      }
    }
    return true
  }

  @ReadingBatDslMarker
  fun challenge(challengeName: String, block: T.() -> Unit) {
    checkChallengeName(challengeName)
    val challenge = challenge(this, challengeName, false) as T
    challenges += challenge.apply(block).apply { validate() }
  }

  override fun toString() = "ChallengeGroup(name='$groupName', challenges=$challenges, packageName='$packageName')"

  companion object : KLogging()
}
