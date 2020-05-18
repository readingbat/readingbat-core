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
import com.github.readingbat.InvalidConfigurationException
import com.github.readingbat.InvalidPathException
import com.github.readingbat.misc.GitHubUtils.directoryContents
import java.io.File

@ReadingBatDslMarker
class LanguageGroup<T : Challenge>(internal val readingBatContent: ReadingBatContent,
                                   internal val languageType: LanguageType) {

  internal val challengeGroups = mutableListOf<ChallengeGroup<T>>()

  // User properties
  var repo: ContentRoot = readingBatContent.repo
    get() =
      if (field == defaultContentRoot)
        throw InvalidConfigurationException("${languageType.lowerName} section is missing a repo value")
      else
        field

  // User properties
  var branchName = "master"
  var srcPath = languageType.srcPrefix

  internal fun validate() {
    // Empty for now
  }

  internal fun addGroup(group: ChallengeGroup<T>) {
    if (languageType != group.languageType)
      throw InvalidConfigurationException("${group.groupName} language type mismatch: $languageType and ${group.languageType}")
    if (hasGroup(group.groupName))
      throw InvalidConfigurationException("Duplicate group name: ${group.groupName}")
    challengeGroups += group
  }

  fun hasGroups() = challengeGroups.isNotEmpty()

  fun hasGroup(groupName: String) = challengeGroups.any { it.groupName == groupName }

  private val excludes = Regex("^__.*__.*$")

  internal data class ChallengeFile(val fileName: String, val returnType: ReturnType)

  @ReadingBatDslMarker
  fun group(name: String, block: ChallengeGroup<T>.() -> Unit) {
    val group = ChallengeGroup(this, name).apply(block)

    if (group.includeList.isNotEmpty()) {
      val fileList =
        repo.let { root ->
          when {
            (root is GitHubRepo) -> root.directoryContents(branchName, srcPath.ensureSuffix("/") + group.packageName)
            (root is FileSystemSource) ->
              File(listOf(root.pathPrefix, srcPath, group.packageName).join()).walk().map { it.name }
                .toList()
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

  fun findGroup(groupName: String) =
    groupName.decode().let { decoded -> challengeGroups.firstOrNull { it.groupName == decoded } }
      ?: throw InvalidPathException("Group ${languageType.lowerName}/$groupName not found")

  fun findChallenge(groupName: String, challengeName: String) = findGroup(groupName).findChallenge(challengeName)

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