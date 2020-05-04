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
import com.github.readingbat.InvalidConfigurationException
import com.github.readingbat.InvalidPathException
import com.github.readingbat.misc.Constants.github
import com.github.readingbat.misc.Constants.githubUserContent

@ReadingBatDslMarker
class LanguageGroup<T : Challenge>(internal val languageType: LanguageType) {
  internal val challengeGroups = mutableListOf<ChallengeGroup<T>>()
  private val rawRoot by lazy { checkedRepoRoot.replace(github, githubUserContent) }

  private val checkedRepoRoot: String
    get() {
      if (repoRoot.isEmpty())
        throw InvalidConfigurationException("${languageType.lowerName} section is missing a repoRoot value")
      return repoRoot
    }

  // User properties
  var srcPrefix = languageType.srcPrefix
  var repoRoot = ""
  var branchName = "master"

  internal val rawRepoRoot by lazy { listOf(rawRoot, branchName, srcPrefix).toPath() }
  internal val gitpodRoot by lazy { listOf(checkedRepoRoot, "blob/$branchName/", srcPrefix).toPath() }

  internal fun validate() {
    // Empty for now
  }

  internal fun addGroup(group: ChallengeGroup<T>) {
    if (languageType != group.languageType)
      throw InvalidConfigurationException("${group.name} language type mismatch: $languageType and ${group.languageType}")
    if (hasGroup(group.name))
      throw InvalidConfigurationException("Duplicate group name: ${group.name}")
    challengeGroups += group
  }

  fun hasGroup(groupName: String) = challengeGroups.any { it.name == groupName }

  @ReadingBatDslMarker
  fun group(name: String, block: ChallengeGroup<T>.() -> Unit) =
    addGroup(ChallengeGroup(this, name).apply(block))

  @ReadingBatDslMarker
  operator fun ChallengeGroup<T>.unaryPlus() = let { this@LanguageGroup.addGroup(this) }

  fun findGroup(groupName: String) =
    groupName.decode().let { decoded -> challengeGroups.firstOrNull { it.name == decoded } }
      ?: throw InvalidPathException("Group ${languageType.lowerName}/$groupName not found")

  fun findChallenge(groupName: String, challengeName: String) = findGroup(groupName).findChallenge(challengeName)

  override fun toString() =
    "LanguageGroup(languageType=$languageType, srcPrefix='$srcPrefix', challengeGroups=$challengeGroups, repoRoot='$repoRoot', branch='$branchName')"
}