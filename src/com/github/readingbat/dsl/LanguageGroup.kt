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
import com.github.readingbat.misc.Constants.github
import com.github.readingbat.misc.Constants.githubUserContent
import com.github.readingbat.misc.GitHubUtils.folderContents

@ReadingBatDslMarker
class LanguageGroup<T : Challenge>(internal val languageType: LanguageType) {
  internal lateinit var readingBatContent: ReadingBatContent

  internal val challengeGroups = mutableListOf<ChallengeGroup<T>>()

  internal val checkedRepo: ContentRoot
    get() {
      if (!this::repo.isInitialized) {
        // Default to parent repo if language group's repo is null
        if (readingBatContent.isRepoInitialized)
          repo = readingBatContent.repo
        else
          throw InvalidConfigurationException("${languageType.lowerName} section is missing a repo value")
      }
      return repo
    }

  // User properties
  lateinit var repo: ContentRoot
  var branchName = "master"
  var srcPath = languageType.srcPrefix

  private val rawRoot by lazy {
    (checkedRepo.sourcePrefix.ensureSuffix("/") + branchName).replace(github,
                                                                      githubUserContent)
  }
  internal val rawRepoRoot by lazy { listOf(rawRoot, srcPath).toPath() }
  internal val gitpodRoot by lazy { listOf(checkedRepo.sourcePrefix, "blob/$branchName", srcPath).toPath() }

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

  fun hasGroups() = challengeGroups.isNotEmpty()

  fun hasGroup(groupName: String) = challengeGroups.any { it.name == groupName }

  private val excludes = Regex("^__.*__.*$")

  internal data class ChallengeFile(val fileName: String, val returnType: ReturnType)

  @ReadingBatDslMarker
  fun group(name: String, block: ChallengeGroup<T>.() -> Unit) {
    val group = ChallengeGroup(this, name).apply(block)

    if (group.includeList.isNotEmpty()) {
      val fileList =
        repo.let {
          when {
            (it is GitHubRepo) -> it.folderContents(group.languageGroup.branchName,
                                                    group.languageGroup.srcPath.ensureSuffix("/") + group.packageName)
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
                .filterNot { it.contains(excludes) }
                .filter { filter.invoke(it) }
                .map { ChallengeFile(it, prt.returnType) }
          }
        }

      group.addChallenge(languageType, uniqueVals.toList().sortedWith(compareBy { it.fileName }))
    }

    addGroup(group)
  }

  @ReadingBatDslMarker
  operator fun ChallengeGroup<T>.unaryPlus() {
    this@LanguageGroup.addGroup(this)
  }

  fun findGroup(groupName: String) =
    groupName.decode().let { decoded -> challengeGroups.firstOrNull { it.name == decoded } }
      ?: throw InvalidPathException("Group ${languageType.lowerName}/$groupName not found")

  fun findChallenge(groupName: String, challengeName: String) = findGroup(groupName).findChallenge(challengeName)

  override fun toString() =
    "LanguageGroup(languageType=$languageType, srcPrefix='$srcPath', challengeGroups=$challengeGroups)"
}