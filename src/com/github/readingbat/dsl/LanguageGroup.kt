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

import com.github.pambrose.common.util.decode
import com.github.pambrose.common.util.toPath
import com.github.readingbat.Constants.github
import com.github.readingbat.Constants.githubUserContent
import com.github.readingbat.InvalidPathException
import com.github.readingbat.dsl.LanguageType.*

@ReadingBatDslMarker
sealed class LanguageGroup(internal val languageType: LanguageType) {
  protected var localGroupCount = 0
  internal val challengeGroups = mutableListOf<ChallengeGroup>()

  private val rawRoot by lazy { repoRoot.replace(github, githubUserContent) }
  internal val rawRepoRoot by lazy { listOf(rawRoot, "master", srcPrefix).toPath() }
  internal val gitpodRoot by lazy { listOf(repoRoot, "blob/master/", srcPrefix).toPath() }

  var srcPrefix = languageType.srcPrefix
  var repoRoot = ""

  fun findChallenge(groupName: String, challengeName: String) =
    findGroup(groupName).findChallenge(challengeName)

  fun hasGroup(groupName: String) = challengeGroups.any { it.name == groupName }

  fun findGroup(groupName: String) =
    groupName.decode()
      .let { decoded -> challengeGroups.firstOrNull { it.name == decoded } }
      ?: throw InvalidPathException("Group ${languageType.lowerName}/$groupName not found")

  internal fun addGroup(group: ChallengeGroup) {
    if (hasGroup(group.name))
      throw InvalidConfigurationException("Duplicate group name: ${group.name}")
    challengeGroups += group
    localGroupCount++
  }

  internal fun validate() {
    if (localGroupCount > 0 && repoRoot.isEmpty())
      throw InvalidConfigurationException("${languageType.lowerName} section is missing a repoRoot value")
  }

  @ReadingBatDslMarker
  operator fun ChallengeGroup.unaryPlus() {
    this@LanguageGroup.addGroup(this)
  }

  override fun toString() =
    "LanguageGroup(languageType=$languageType, srcPrefix='$srcPrefix', challengeGroups=$challengeGroups, repoRoot='$repoRoot')"

}

class PythonGroup : LanguageGroup(Python) {
  @ReadingBatDslMarker
  fun group(name: String, block: PythonChallengeGroup.() -> Unit) =
    addGroup(PythonChallengeGroup(this, name).apply(block))
}

class JavaGroup : LanguageGroup(Java) {
  @ReadingBatDslMarker
  fun group(name: String, block: JavaChallengeGroup.() -> Unit) =
    addGroup(JavaChallengeGroup(this, name).apply(block))
}

class KotlinGroup : LanguageGroup(Kotlin) {
  @ReadingBatDslMarker
  fun group(name: String, block: KotlinChallengeGroup.() -> Unit) =
    addGroup(KotlinChallengeGroup(this, name).apply(block))
}