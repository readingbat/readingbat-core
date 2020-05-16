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
import io.ktor.util.getDigestFunction
import mu.KLogging
import org.kohsuke.github.GitHub
import java.security.MessageDigest
import java.util.*

// https://github-api.kohsuke.org

object GitHubUtils : KLogging() {

  private val github by lazy { GitHub.connect() }

  fun GitHubRepo.directoryContents(branchName: String, path: String): List<String> {
    val repo = github.getOrganization(organizationName).getRepository(repoName)
    val elems = path.split("/").filter { it.isNotEmpty() }
    var currRoot = repo.getTree(branchName)
    elems.forEach { elem -> currRoot = currRoot.tree.asSequence().filter { it.path == elem }.first().asTree() }
    return currRoot.tree.map { it.path }
  }
}

fun String.md5(): String {
  return hashString(this, "MD5")
}

fun String.sha256(): String {
  return hashString(this, "SHA-256")
}

private fun hashString(input: String, algorithm: String) =
  MessageDigest
    .getInstance(algorithm)
    .digest(input.toByteArray())
    .fold("", { str, it -> str + "%02x".format(it) })

fun main() {
  println("test".sha256())
  println("test".md5())

  // https://docs.oracle.com/javase/6/docs/api/java/security/SecureRandom.html
  val digester = getDigestFunction("SHA-256") { "readingbat${it.length}" }
  val kk = digester.invoke("test")
  val mm = kk.fold("", { str, it -> str + "%02x".format(it) })

  val ii = Base64.getEncoder().encode(kk)
  println(mm)
  println(String(ii))

  val pp = Base64.getDecoder()
    .decode("GSjkHCHGAxTTbnkEDBbVYd+PUFRlcWiumc4+MWE9Rvw=").fold("", { str, it -> str + "%02x".format(it) })
  println(pp)

}