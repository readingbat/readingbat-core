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

import com.github.pambrose.common.util.*
import com.github.readingbat.dsl.GitHubUtils.directoryContents
import com.github.readingbat.misc.PageUtils.pathOf
import com.github.readingbat.server.GroupName
import java.io.File

@ReadingBatDslMarker
class LanguageGroup<T : Challenge>(internal val content: ReadingBatContent,
                                   internal val languageType: LanguageType) {

  internal val challengeGroups = mutableListOf<ChallengeGroup<T>>()

  // User properties
  var repo: ContentRoot = content.repo           // Defaults to outer-level value
    get() =
      if (field == defaultContentRoot)
        throw InvalidConfigurationException("${languageType.languageName} section is missing a repo value")
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
    if (hasGroup(group.groupName.value))
      throw InvalidConfigurationException("Duplicate group name: ${group.groupName}")
    challengeGroups += group
  }

  fun isEmpty() = challengeGroups.isEmpty()

  fun isNotEmpty() = challengeGroups.isNotEmpty()

  fun hasGroup(groupName: String) = challengeGroups.any { it.groupName.value == groupName }

  private val excludes = Regex("^__.*__.*$")

  internal data class ChallengeFile(val fileName: String, val returnType: ReturnType)

  @ReadingBatDslMarker
  fun group(name: String, block: ChallengeGroup<T>.() -> Unit) {
    val group = ChallengeGroup(this, GroupName(name)).apply(block)

    if (group.includeList.isNotEmpty()) {
      val fileList =
        repo.let { root ->
          when {
            (root is GitHubRepo) -> root.directoryContents(branchName, srcPath.ensureSuffix("/") + group.packageName)
            (root is FileSystemSource) ->
              File(pathOf(root.pathPrefix, srcPath, group.packageName)).walk().map { it.name }.toList()
            else -> throw InvalidConfigurationException("Invalid repo type")
          }
        }

      val uniqueVals = mutableSetOf<ChallengeFile>()
      group.includeList
        .forEach { prt ->
          if (prt.pattern.isNotBlank()) {
            val regex = prt.pattern.asRegex()
            val filter: (String) -> Boolean = { it.contains(regex) }
            uniqueVals +=
              fileList
                .filter { !it.contains(excludes) && filter.invoke(it) }
                .map { ChallengeFile(it, prt.returnType) }
          }
        }

      group.addChallenge(uniqueVals.toList().sortedWith(compareBy { it.fileName }))
    }

    addGroup(group)
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
      ?: throw InvalidPathException("Group ${languageType.languageName}/$groupName not found")

  fun findChallenge(groupName: String, challengeName: String) = findGroup(groupName).findChallenge(challengeName)

  operator fun get(groupName: String): ChallengeGroup<T> = findGroup(groupName)

  operator fun get(groupName: String, challengeName: String): T = findChallenge(groupName, challengeName)

  override fun toString() =
    "LanguageGroup(languageType=$languageType, srcPrefix='$srcPath', challengeGroups=$challengeGroups)"

  companion object {
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