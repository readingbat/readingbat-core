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
import com.github.pambrose.common.util.randomId
import io.ktor.util.getDigestFunction
import mu.KLogging
import org.kohsuke.github.GitHub
import java.security.MessageDigest
import java.security.SecureRandom

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

fun String.md5(salt: String): String = encodedByteArray(this, { salt }, "MD5").asText

fun String.sha256(salt: String): String = encodedByteArray(this, { salt }, "SHA-256").asText

fun String.md5(salt: ByteArray): String = encodedByteArray(this, salt, "MD5").asText

fun String.sha256(salt: ByteArray): String = encodedByteArray(this, salt, "SHA-256").asText

val ByteArray.asText get() = fold("", { str, it -> str + "%02x".format(it) })

private fun encodedByteArray(input: String, salt: ByteArray, algorithm: String) =
  with(MessageDigest.getInstance(algorithm)) {
    update(salt)
    digest(input.toByteArray())
  }

private fun encodedByteArray(input: String, salt: (String) -> String, algorithm: String) =
  with(MessageDigest.getInstance(algorithm)) {
    update(salt(input).toByteArray())
    digest(input.toByteArray())
  }

fun newByteArraySalt(len: Int = 16): ByteArray = ByteArray(len).apply { SecureRandom().nextBytes(this) }
fun newStringSalt(len: Int = 16): String = randomId(len)


fun main() {
  println("test".sha256("test2"))
  println("test".md5("test2"))

  // https://docs.oracle.com/javase/6/docs/api/java/security/SecureRandom.html
  val digester = getDigestFunction("SHA-256") { "it${it.length}" }
  val kk = digester.invoke("test")
  val mm = kk.asText

  println(mm)
}