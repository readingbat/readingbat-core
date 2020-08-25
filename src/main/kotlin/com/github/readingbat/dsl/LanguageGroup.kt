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
import com.github.pambrose.common.util.ContentSource
import com.github.pambrose.common.util.asRegex
import com.github.pambrose.common.util.decode
import com.github.readingbat.server.GroupName
import com.github.readingbat.server.ReadingBatServer

@ReadingBatDslMarker
class LanguageGroup<T : Challenge>(internal val content: ReadingBatContent,
                                   internal val languageType: LanguageType) {

  internal val challengeGroups = mutableListOf<ChallengeGroup<T>>()
  internal val metrics get() = ReadingBatServer.metrics
  internal val languageName get() = languageType.languageName

  // User properties
  var repo: ContentRoot = content.repo           // Defaults to outer-level value
    get() =
      if (field == defaultContentRoot)
        throw InvalidConfigurationException("$languageName section is missing a repo value")
      else
        field
  var branchName = content.branchName    // Defaults to outer-level value
  var srcPath = languageType.srcPrefix

  internal fun validate() {
    // Empty for now
  }

  internal fun addGroup(group: ChallengeGroup<T>) {
    if (languageType != group.languageType)
      throw InvalidConfigurationException("${group.groupName} language type mismatch: $languageType and ${group.languageType}")
    if (hasGroupNameSuffix(group.groupNameSuffix))
      throw InvalidConfigurationException("Duplicate group name: ${group.groupNameSuffix}")
    challengeGroups += group
  }

  fun isEmpty() = challengeGroups.isEmpty()

  fun isNotEmpty() = challengeGroups.isNotEmpty()

  fun hasGroup(groupName: String) = challengeGroups.any { it.groupName.value == groupName }

  private fun hasGroupNameSuffix(groupNameSuffix: GroupName) =
    challengeGroups.any { it.groupNameSuffix.value == groupNameSuffix.value }

  internal data class ChallengeFile(val fileName: String, val returnType: ReturnType)

  @ReadingBatDslMarker
  fun group(name: String, block: ChallengeGroup<T>.() -> Unit) {
    val group = ChallengeGroup(this, GroupName(name)).apply(block)
    addGroup(group)
  }

  internal fun addIncludedFiles(group: ChallengeGroup<T>, prt: ChallengeGroup.PatternReturnType) {
    if (prt.pattern.isNotBlank()) {
      val regex = prt.pattern.asRegex()
      group.fileList
        .filter { !it.contains(excludes) && it.contains(regex) }
        .map { ChallengeFile(it, prt.returnType) }
        .sortedWith(compareBy { it.fileName })
        .forEach { group.addChallenge(it, prt.pattern) }
    }
  }

  @ReadingBatDslMarker
  operator fun ChallengeGroup<T>.unaryPlus() {
    this@LanguageGroup.addGroup(this)
  }

  @ReadingBatDslMarker
  fun include(challengeGroup: ChallengeGroup<T>) {
    addGroup(challengeGroup)
  }

  fun findGroup(groupName: String): ChallengeGroup<T> =
    groupName.decode().let { decoded -> challengeGroups.firstOrNull { it.groupName.value == decoded } }
      ?: throw InvalidPathException("Group $languageName/$groupName not found")

  fun findChallenge(groupName: String, challengeName: String) = findGroup(groupName).findChallenge(challengeName)

  operator fun get(groupName: String): ChallengeGroup<T> = findGroup(groupName)

  operator fun get(groupName: String, challengeName: String): T = findChallenge(groupName, challengeName)

  override fun toString() =
    "LanguageGroup(languageType=$languageType, srcPath='$srcPath', challengeGroups=$challengeGroups)"

  companion object {
    private val excludes = Regex("^__.*__.*$")

    internal val defaultContentRoot =
      object : ContentRoot {
        override val sourcePrefix = ""
        override val remote = false

        override fun file(path: String) =
          object : ContentSource {
            override val content = ""
            override val remote = false
            override val source = ""
          }
      }
  }
}