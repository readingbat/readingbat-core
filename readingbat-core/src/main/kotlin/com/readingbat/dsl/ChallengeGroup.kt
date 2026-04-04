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

import com.pambrose.common.util.FileSystemSource
import com.pambrose.common.util.GitHubRepo
import com.pambrose.common.util.ensureSuffix
import com.pambrose.common.util.md5Of
import com.pambrose.common.util.pathOf
import com.pambrose.common.util.toDoubleQuoted
import com.readingbat.common.Constants.CHALLENGE_NOT_FOUND
import com.readingbat.common.KeyConstants.DIR_CONTENTS_KEY
import com.readingbat.common.KeyConstants.keyOf
import com.readingbat.dsl.ContentCaches.dirCache
import com.readingbat.dsl.GitHubUtils.organizationDirectoryContents
import com.readingbat.dsl.GitHubUtils.userDirectoryContents
import com.readingbat.dsl.ReturnType.Runtime
import com.readingbat.dsl.challenge.Challenge
import com.readingbat.dsl.challenge.Challenge.Companion.challenge
import com.readingbat.dsl.challenge.KotlinChallenge
import com.readingbat.dsl.challenge.PythonChallenge
import com.readingbat.server.ChallengeName
import com.readingbat.server.GroupName
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import kotlin.reflect.KProperty

/**
 * A named group of programming challenges within a single language.
 *
 * Challenge groups correspond to a directory of source files (identified by [packageName]) and
 * appear as sections on the language page (e.g., "Warmup-1", "String-2"). Challenges can be
 * added individually via [challenge] or in bulk via [includeFiles].
 *
 * Within the DSL, a [ChallengeGroup] is created inside a [LanguageGroup.group] block:
 * ```kotlin
 * java {
 *   group("Warmup-1") {
 *     packageName = "warmup1"
 *     description = "Simple warmup problems"
 *     includeFiles = "*.java"
 *   }
 * }
 * ```
 *
 * @param T the language-specific [Challenge] subtype
 */
@ReadingBatDslMarker
@Suppress("unused")
class ChallengeGroup<T : Challenge>(
  val languageGroup: LanguageGroup<T>,
  internal val groupNameSuffix: GroupName,
) {
  /** The ordered list of challenges in this group. */
  val challenges = mutableListOf<T>()

  private val groupPrefix by lazy { pathOf(languageName, groupName) }
  private val srcPath get() = languageGroup.srcPath
  private val languageName get() = languageType.languageName
  private val repo get() = languageGroup.repo
  private val branchName get() = languageGroup.branchName
  private val metrics get() = languageGroup.metrics

  internal val parsedDescription by lazy { MarkdownParser.toHtml(description) }
  internal val languageType get() = languageGroup.languageType
  internal val packageNameAsPath get() = packageName.replace(".", "/")
  internal var namePrefix = ""

  // Do not use lazy for groupName because namePrefix is assigned late in the process of includes
  val groupName get() = GroupName("${namePrefix.ifBlank { "" }}${groupNameSuffix.value}")

  private fun dirContentsKey(path: String) = keyOf(DIR_CONTENTS_KEY, md5Of(path))

  private fun fetchDirContentsFromDirCache(path: String) =
    if (isContentCachingEnabled()) {
      dirCache[path]?.toList().apply { logger.debug { """Retrieved "$path" from dir cache""" } }
    } else {
      null
    }

  private fun fetchRemoteFiles(root: GitHubRepo, path: String) =
    (
      if (root.ownerType.isUser())
        root.userDirectoryContents(branchName, path, metrics)
      else
        root.organizationDirectoryContents(branchName, path, metrics)
      ).also {
        if (isContentCachingEnabled()) {
          synchronized(dirCache) {
            val dirContentsKey = dirContentsKey(path)
            dirCache.computeIfAbsent(dirContentsKey) { mutableListOf() }
            dirCache[dirContentsKey]!!.addAll(it)
            logger.info { "Saved to dir cache: ${path.toDoubleQuoted()}" }
          }
        }
      }

  internal val fileList by lazy {
    repo.let { root ->
      when (root) {
        is GitHubRepo -> {
          val path = "${srcPath.ensureSuffix("/")}$packageNameAsPath"

          if (isContentCachingEnabled()) {
            fetchDirContentsFromDirCache(path).let { if (!it.isNullOrEmpty()) it else fetchRemoteFiles(root, path) }
          } else {
            fetchRemoteFiles(root, path)
          }
        }

        is FileSystemSource -> {
          File(pathOf(root.pathPrefix, srcPath, packageNameAsPath)).walk().map { it.name }.toList()
        }

        else -> {
          error("Invalid repo type: $root")
        }
      }
    }
  }

  /** The Java/Kotlin package name (dot-separated) or Python directory path for locating challenge source files. */
  var packageName = ""

  /** An optional Markdown description displayed at the top of the group page. */
  var description = ""

  /**
   * Glob pattern for auto-including challenge files from the package directory.
   * Only valid for Java challenges (return type is inferred at runtime).
   * For Python/Kotlin, set the `returnType` explicitly.
   */
  var includeFiles by IncludeFiles(this, languageType)

  /**
   * Pattern-and-return-type pair for auto-including challenge files.
   * Required for Python and Kotlin challenges where the return type cannot be inferred.
   * Assign using the `returns` infix function: `includeFilesWithType = "*.py" returns StringType`.
   */
  var includeFilesWithType by IncludeFilesWithType(this, languageType)

  private class IncludeFiles<T : Challenge>(val group: ChallengeGroup<T>, val languageType: LanguageType) {
    val includeList = mutableListOf<PatternReturnType>()

    operator fun getValue(thisRef: Any?, property: KProperty<*>) = includeList.toString()

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
      if (languageType.isJava) {
        val prt = PatternReturnType(value, Runtime)
        includeList += prt
        group.languageGroup.addIncludedFiles(group, prt)
      } else {
        val lang = languageType.languageName
        error("Use includeFilesWithType instead of includeFiles for $lang challenges")
      }
    }
  }

  private class IncludeFilesWithType<T : Challenge>(val group: ChallengeGroup<T>, val languageType: LanguageType) {
    val includeList = mutableListOf<PatternReturnType>()

    operator fun getValue(thisRef: Any?, property: KProperty<*>): PatternReturnType = PatternReturnType("", Runtime)

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: PatternReturnType) {
      if (!languageType.isJava) {
        includeList += value
        group.languageGroup.addIncludedFiles(group, value)
      } else {
        val lang = languageType.languageName
        error("Use includeFiles instead of includeFilesWithType for $lang challenges")
      }
    }
  }

  private fun hasChallenge(challengeName: String) = challenges.any { it.challengeName.value == challengeName }

  operator fun contains(challengeName: String) = hasChallenge(challengeName)

  private fun removeChallenge(challengeName: ChallengeName) = challenges.removeAt(indexOf(challengeName))

  /** Finds a challenge by name, throwing [InvalidRequestException] if not found. */
  fun findChallenge(challengeName: String): T =
    challenges.firstOrNull { it.challengeName.value == challengeName }
      ?: throw InvalidRequestException("$CHALLENGE_NOT_FOUND: ${pathOf(groupPrefix, challengeName)}")

  operator fun get(challengeName: String): T = findChallenge(challengeName)

  internal fun indexOf(challengeName: ChallengeName): Int {
    val pos = challenges.indexOfFirst { it.challengeName == challengeName }
    if (pos == -1)
      throw InvalidRequestException("$CHALLENGE_NOT_FOUND: ${pathOf(groupPrefix, challengeName)}")
    return pos
  }

  /** Adds a challenge to this group using the unary `+` operator. */
  fun T.unaryPlus() {
    this@ChallengeGroup.checkChallengeName(challengeName)
    this@ChallengeGroup.challenges += this
  }

  /** Includes an externally defined challenge into this group. */
  fun include(challenge: T) {
    checkChallengeName(challenge.challengeName)
    challenges += challenge
  }

  // @ReadingBatDslMarker
  // fun includeFiles(vararg patterns: String) = import(patterns.toList())

  /** Associates a file glob pattern with an expected [ReturnType] for bulk challenge inclusion. */
  data class PatternReturnType(val pattern: String, val returnType: ReturnType)

  /** Infix function to pair a file pattern with a [ReturnType], e.g., `"*.py" returns StringType`. */
  infix fun String.returns(returnType: ReturnType) = PatternReturnType(this, returnType)

  internal fun addChallenge(challengeFile: LanguageGroup.ChallengeFile, pattern: String) {
    val challengeName = ChallengeName(challengeFile.fileName.split(".").first())
    if (checkChallengeName(challengeName, false)) {
      logger.debug { "Adding $challengeName by pattern ${pattern.toDoubleQuoted()}" }
      val challenge = challenge(this, challengeName, true)
      // Skip this next step for Java because returnType is calculated
      when {
        languageType.isPython -> (challenge as PythonChallenge).apply { returnType = challengeFile.returnType }
        languageType.isKotlin -> (challenge as KotlinChallenge).apply { returnType = challengeFile.returnType }
      }
      @Suppress("UNCHECKED_CAST")
      challenges += challenge as T
    }
  }

  private fun checkChallengeName(challengeName: ChallengeName, throwExceptionIfPresent: Boolean = true): Boolean {
    if (challengeName.value in this) {
      val challenge = this[challengeName.value]
      if (challenge.replaceable) {
        removeChallenge(challengeName)
      } else {
        if (throwExceptionIfPresent)
          error("Challenge ${pathOf(groupPrefix, challengeName)} already exists")
        else
          return false
      }
    }
    return true
  }

  /**
   * Defines a single challenge within this group.
   *
   * @param name the challenge name, which must match the source file name (without extension)
   * @param block optional DSL configuration block for setting challenge properties like [Challenge.description]
   */
  fun challenge(name: String, block: T.() -> Unit = {}) {
    val challengeName = ChallengeName(name)
    logger.debug { "Adding $challengeName" }
    checkChallengeName(challengeName)

    @Suppress("UNCHECKED_CAST")
    val challenge = challenge(this, challengeName, false) as T
    challenges += challenge.apply(block).apply { validate() }
  }

  override fun toString() = "ChallengeGroup(name='$groupName', challenges=$challenges, packageName='$packageName')"

  companion object {
    private val logger = KotlinLogging.logger {}
  }
}
