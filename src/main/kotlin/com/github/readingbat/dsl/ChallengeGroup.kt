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

import com.github.pambrose.common.util.FileSystemSource
import com.github.pambrose.common.util.GitHubRepo
import com.github.pambrose.common.util.ensureSuffix
import com.github.readingbat.dsl.Challenge.Companion.challenge
import com.github.readingbat.dsl.GitHubUtils.organizationDirectoryContents
import com.github.readingbat.dsl.GitHubUtils.userDirectoryContents
import com.github.readingbat.dsl.ReturnType.Runtime
import com.github.readingbat.misc.PageUtils
import com.github.readingbat.server.ChallengeName
import com.github.readingbat.server.GroupName
import com.github.readingbat.server.ReadingBatServer
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet
import mu.KLogging
import java.io.File
import kotlin.reflect.KProperty

@ReadingBatDslMarker
class ChallengeGroup<T : Challenge>(internal val languageGroup: LanguageGroup<T>,
                                    internal val groupNameSuffix: GroupName) {
  private val options = MutableDataSet().apply { set(HtmlRenderer.SOFT_BREAK, "<br />\n") }
  internal val challenges = mutableListOf<T>()
  internal var namePrefix = ""

  internal val groupName by lazy { GroupName("${if (namePrefix.isNotBlank()) namePrefix else ""}${groupNameSuffix.value}") }
  private val groupPrefix by lazy { "$languageName/$groupName" }
  private val parser by lazy { Parser.builder(options).build() }
  private val renderer by lazy { HtmlRenderer.builder(options).build() }
  internal val parsedDescription: String
    get() {
      val document = parser.parse(description.trimIndent())
      return renderer.render(document)
    }

  private val srcPath get() = languageGroup.srcPath
  internal val languageType get() = languageGroup.languageType
  internal val languageName get() = languageType.languageName
  internal val repo get() = languageGroup.repo
  internal val branchName get() = languageGroup.branchName
  internal val metrics get() = languageGroup.metrics

  internal val fileList by lazy {
    repo.let { root ->
      when (root) {
        is GitHubRepo -> {
          val path = srcPath.ensureSuffix("/") + packageName
          if (root.ownerType.isUser())
            root.userDirectoryContents(branchName, path, metrics)
          else
            root.organizationDirectoryContents(branchName, path, metrics)
        }
        is FileSystemSource -> File(PageUtils.pathOf(root.pathPrefix, srcPath, packageName)).walk().map { it.name }
          .toList()
        else -> throw InvalidConfigurationException("Invalid repo type")
      }
    }
  }

  // User properties
  var packageName = ""
  var description = ""
  var includeFiles by IncludeFiles(this, languageType)
  var includeFilesWithType by IncludeFilesWithType(this, languageType)

  private class IncludeFiles<T : Challenge>(val group: ChallengeGroup<T>, val languageType: LanguageType) {
    val includeList = mutableListOf<PatternReturnType>()
    operator fun getValue(thisRef: Any?, property: KProperty<*>) = includeList.toString()
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
      if (languageType.isJava()) {
        val prt = PatternReturnType(value, Runtime)
        includeList += prt
        group.languageGroup.addIncludedFiles(group, prt)
      }
      else {
        val lang = languageType.languageName
        throw InvalidConfigurationException("Use includeFilesWithType instead of includeFiles for $lang challenges")
      }
    }
  }

  private class IncludeFilesWithType<T : Challenge>(val group: ChallengeGroup<T>, val languageType: LanguageType) {
    val includeList = mutableListOf<PatternReturnType>()
    operator fun getValue(thisRef: Any?, property: KProperty<*>): PatternReturnType = PatternReturnType("", Runtime)
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: PatternReturnType) {
      if (!languageType.isJava()) {
        includeList += value
        group.languageGroup.addIncludedFiles(group, value)
      }
      else {
        val lang = languageType.languageName
        throw InvalidConfigurationException("Use includeFiles instead of includeFilesWithType for $lang challenges")
      }
    }
  }

  fun hasChallenge(challengeName: String) = challenges.any { it.challengeName.value == challengeName }

  operator fun contains(challengeName: String) = hasChallenge(challengeName)

  private fun removeChallenge(challengeName: ChallengeName) = challenges.removeAt(indexOf(challengeName))

  fun findChallenge(challengeName: String): T =
    challenges.firstOrNull { it.challengeName.value == challengeName }
      ?: throw InvalidPathException("Challenge $groupPrefix/$challengeName not found.")

  operator fun get(challengeName: String): T = findChallenge(challengeName)

  internal fun indexOf(challengeName: ChallengeName): Int {
    val pos = challenges.indexOfFirst { it.challengeName == challengeName }
    if (pos == -1)
      throw InvalidPathException("Challenge $groupPrefix/$challengeName not found.")
    return pos
  }

  @ReadingBatDslMarker
  fun T.unaryPlus() {
    this@ChallengeGroup.checkChallengeName(challengeName)
    this@ChallengeGroup.challenges += this
  }

  @ReadingBatDslMarker
  fun include(challenge: T) {
    checkChallengeName(challenge.challengeName)
    challenges += challenge
  }

  //@ReadingBatDslMarker
  //fun includeFiles(vararg patterns: String) = import(patterns.toList())

  data class PatternReturnType(val pattern: String, val returnType: ReturnType)

  @ReadingBatDslMarker
  infix fun String.returns(returnType: ReturnType) = PatternReturnType(this, returnType)

  internal fun addChallenge(challengeFile: LanguageGroup.ChallengeFile, pattern: String) {
    val challengeName = ChallengeName(challengeFile.fileName.split(".").first())
    if (checkChallengeName(challengeName, false)) {
      logger.debug { """Adding $challengeName by pattern "$pattern"""" }
      val challenge = challenge(this, challengeName, true, ReadingBatServer.metrics)
      // Skip this next step for Java because returnType is calculated
      when {
        languageType.isPython() -> (challenge as PythonChallenge).apply { returnType = challengeFile.returnType }
        languageType.isKotlin() -> (challenge as KotlinChallenge).apply { returnType = challengeFile.returnType }
      }
      challenges += challenge as T
    }
  }

  private fun checkChallengeName(challengeName: ChallengeName, throwExceptionIfPresent: Boolean = true): Boolean {
    if (challengeName.value in this) {
      val challenge = this[challengeName.value]
      if (challenge.replaceable) {
        removeChallenge(challengeName)
      }
      else {
        if (throwExceptionIfPresent)
          throw InvalidConfigurationException("Challenge $groupPrefix/$challengeName already exists")
        else
          return false
      }
    }
    return true
  }

  @ReadingBatDslMarker
  fun challenge(name: String, block: T.() -> Unit = {}) {
    val challengeName = ChallengeName(name)
    logger.debug { "Adding $challengeName" }
    checkChallengeName(challengeName)
    val challenge = challenge(this, challengeName, false, ReadingBatServer.metrics) as T
    challenges += challenge.apply(block).apply { validate() }
  }

  override fun toString() = "ChallengeGroup(name='$groupName', challenges=$challenges, packageName='$packageName')"

  companion object : KLogging()
}