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

import com.github.pambrose.common.util.GitHubRepo
import com.github.readingbat.common.Metrics
import mu.KLogging
import org.kohsuke.github.GHRepository
import org.kohsuke.github.GitHub
import kotlin.time.measureTimedValue

// See https://github-api.kohsuke.org for usage details

internal object GitHubUtils : KLogging() {

  private val github by lazy { GitHub.connect() }

  fun GitHubRepo.userDirectoryContents(branchName: String,
                                       path: String,
                                       metrics: Metrics): List<String> {
    val repo = github.getUser(ownerName).getRepository(repoName)
    return directoryContents(repo, branchName, path, metrics)
  }

  fun GitHubRepo.organizationDirectoryContents(branchName: String,
                                               path: String,
                                               metrics: Metrics): List<String> {
    val repo: GHRepository = github.getOrganization(ownerName).getRepository(repoName)
    return directoryContents(repo, branchName, path, metrics)
  }

  private fun directoryContents(repo: GHRepository,
                                branchName: String,
                                path: String,
                                metrics: Metrics): List<String> {
    val timer = metrics.githubDirectoryReadDuration.labels(agentLaunchId()).startTimer()
    try {
      val timedValue =
        measureTimedValue {
          val elems = path.split("/").filter { it.isNotEmpty() }
          var currRoot = repo.getTree(branchName)
          elems.forEach { elem ->
            currRoot = currRoot.tree.asSequence().filter { it.path == elem }.firstOrNull()?.asTree()
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
