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
import com.github.readingbat.misc.GitHubUtils.folderContents
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet
import mu.KLogging

@ReadingBatDslMarker
class ChallengeGroup<T : Challenge>(internal val languageGroup: LanguageGroup<T>, internal val name: String) {
  internal val languageType = languageGroup.languageType
  internal val repo = languageGroup.repo
  internal val branchName = languageGroup.branchName
  internal val srcPath = languageGroup.srcPath

  internal val challenges = mutableListOf<T>()
  private val prefix by lazy { "${languageType.lowerName}/$name" }
  internal val parsedDescription
      by lazy {
        val options = MutableDataSet().apply { set(HtmlRenderer.SOFT_BREAK, "<br />\n") }
        val parser = Parser.builder(options).build()
        val renderer = HtmlRenderer.builder(options).build()
        val document = parser.parse(description.trimIndent())
        renderer.render(document)
      }

  // User properties
  var packageName = ""
  var description = ""

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
  operator fun T.unaryPlus() {
    val challengeName = name
    if (this@ChallengeGroup.hasChallenge(challengeName)) {
      val challenge = this@ChallengeGroup.findChallenge(challengeName)
      if (challenge.replaceable)
        this@ChallengeGroup.removeChallenge(challengeName)
      else
        throw InvalidConfigurationException("Challenge ${this@ChallengeGroup.prefix}/$challengeName already exists")
    }

    this@ChallengeGroup.challenges += this
  }

  @ReadingBatDslMarker
  fun import(pattern: String = "") {
    folderContents(repo, branchName, srcPath, packageName, pattern)
      .map { it.split(".").first() }
      .forEach { challengeName ->
        logger.info { "Adding ${challengeName}" }
        challenges += challenge(this, challengeName, true) as T
      }
  }

  @ReadingBatDslMarker
  fun challenge(challengeName: String, block: T.() -> Unit) {
    if (this@ChallengeGroup.hasChallenge(challengeName)) {
      val challenge = this@ChallengeGroup.findChallenge(challengeName)
      if (challenge.replaceable)
        this@ChallengeGroup.removeChallenge(challengeName)
      else
        throw InvalidConfigurationException("Challenge $prefix/$challengeName already exists")
    }

    val challenge = challenge(this, challengeName, false) as T
    challenges += challenge.apply(block).apply { validate() }
  }

  override fun toString() = "ChallengeGroup(name='$name', challenges=$challenges, packageName='$packageName')"

  companion object : KLogging()
}
