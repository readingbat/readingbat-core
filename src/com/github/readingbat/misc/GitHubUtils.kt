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

package com.github.readingbat.misc

import com.github.pambrose.common.util.GitHubRepo
import com.github.pambrose.common.util.asRegex
import com.github.pambrose.common.util.ensureSuffix
import mu.KLogging
import org.kohsuke.github.GitHub

object GitHubUtils : KLogging() {

  private val github by lazy { GitHub.connect() }

  fun folderContents(githubRepo: GitHubRepo,
                     branchName: String,
                     srcPath: String,
                     packageName: String,
                     filePattern: String = ""): List<String> {
    val repo = github.getOrganization(githubRepo.organizationName).getRepository(githubRepo.repoName)
    val elems = (srcPath.ensureSuffix("/") + packageName).split("/").filter { it.isNotEmpty() }

    //logger.info("Walking elems: $elems")
    var currRoot = repo.getTree(branchName)
    elems.forEach { elem ->
      currRoot = currRoot.tree.asSequence().filter { it.path == elem }.first().asTree()
    }

    var files = currRoot.tree.map { it.path }
    if (filePattern.isNotBlank()) {
      val regex = filePattern.asRegex()
      val filter: (String) -> Boolean = { it.contains(regex) }
      files = files.filter { filter.invoke(it) }
    }

    return files
  }
}