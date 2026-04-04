/*
 * Copyright © 2026 Paul Ambrose (pambrose@mac.com)
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

package com.readingbat.dsl

import com.pambrose.common.util.GitHubRepo
import com.readingbat.common.Metrics
import io.github.oshai.kotlinlogging.KotlinLogging
import org.kohsuke.github.GHRepository
import org.kohsuke.github.GitHub
import kotlin.time.measureTimedValue

/**
 * Utilities for listing challenge source files from GitHub repositories using the
 * [github-api](https://github-api.kohsuke.org) library.
 *
 * These functions traverse the repository tree to enumerate files in a given directory path,
 * which is used by [ChallengeGroup] to discover challenge files for `includeFiles` patterns.
 */
internal object GitHubUtils {
  private val logger = KotlinLogging.logger {}
  private val github by lazy { GitHub.connect() }

  /** Lists file names in a directory of a user-owned GitHub repository. */
  fun GitHubRepo.userDirectoryContents(
    branchName: String,
    path: String,
    metrics: Metrics,
  ): List<String> {
    val repo = github.getUser(ownerName).getRepository(repoName)
    return directoryContents(repo, branchName, path, metrics)
  }

  /** Lists file names in a directory of an organization-owned GitHub repository. */
  fun GitHubRepo.organizationDirectoryContents(
    branchName: String,
    path: String,
    metrics: Metrics,
  ): List<String> {
    val repo: GHRepository = github.getOrganization(ownerName).getRepository(repoName)
    return directoryContents(repo, branchName, path, metrics)
  }

  private fun directoryContents(
    repo: GHRepository,
    branchName: String,
    path: String,
    metrics: Metrics,
  ): List<String> {
    val timer = metrics.githubDirectoryReadDuration.labels(agentLaunchId()).startTimer()
    try {
      val timedValue =
        measureTimedValue {
          val elems = path.split("/").filter { it.isNotEmpty() }
          var currRoot = repo.getTree(branchName)
          elems.forEach { elem ->
            currRoot = currRoot.tree.firstOrNull { it.path == elem }?.asTree()
          }
          currRoot?.tree?.map { it.path } ?: emptyList()
        }
      logger.info { "Found ${timedValue.value.size} files in $path in ${timedValue.duration}" }
      return timedValue.value
    } finally {
      timer.observeDuration()
    }
  }
}
