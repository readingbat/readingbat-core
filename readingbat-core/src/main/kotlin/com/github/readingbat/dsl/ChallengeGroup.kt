/*
 * Copyright © 2023 Paul Ambrose (pambrose@mac.com)
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

import com.github.pambrose.common.redis.RedisUtils.withNonNullRedisPool
import com.github.pambrose.common.redis.RedisUtils.withRedisPool
import com.github.pambrose.common.util.FileSystemSource
import com.github.pambrose.common.util.GitHubRepo
import com.github.pambrose.common.util.ensureSuffix
import com.github.pambrose.common.util.isNotNull
import com.github.pambrose.common.util.md5Of
import com.github.pambrose.common.util.pathOf
import com.github.pambrose.common.util.toDoubleQuoted
import com.github.readingbat.common.Constants.CHALLENGE_NOT_FOUND
import com.github.readingbat.common.KeyConstants.DIR_CONTENTS_KEY
import com.github.readingbat.common.KeyConstants.keyOf
import com.github.readingbat.dsl.GitHubUtils.organizationDirectoryContents
import com.github.readingbat.dsl.GitHubUtils.userDirectoryContents
import com.github.readingbat.dsl.ReturnType.Runtime
import com.github.readingbat.dsl.challenge.Challenge
import com.github.readingbat.dsl.challenge.Challenge.Companion.challenge
import com.github.readingbat.dsl.challenge.KotlinChallenge
import com.github.readingbat.dsl.challenge.PythonChallenge
import com.github.readingbat.server.ChallengeName
import com.github.readingbat.server.GroupName
import com.github.readingbat.server.ReadingBatServer.redisPool
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import kotlin.reflect.KProperty

@ReadingBatDslMarker
@Suppress("unused")
class ChallengeGroup<T : Challenge>(
  val languageGroup: LanguageGroup<T>,
  internal val groupNameSuffix: GroupName,
) {
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

  private fun fetchDirContentsFromRedis(path: String) =
    if (isContentCachingEnabled())
      redisPool?.withRedisPool { redis -> redis?.lrange(dirContentsKey(path), 0, -1) }
        ?.apply { logger.debug { """Retrieved "$path" from redis""" } }
    else
      null

  private fun fetchRemoteFiles(root: GitHubRepo, path: String) =
    (
      if (root.ownerType.isUser())
        root.userDirectoryContents(branchName, path, metrics)
      else
        root.organizationDirectoryContents(branchName, path, metrics)
      ).also {
        if (isContentCachingEnabled()) {
          redisPool?.withNonNullRedisPool(true) { redis ->
            val dirContentsKey = dirContentsKey(path)
            it.forEach { redis.rpush(dirContentsKey, it) }
            logger.info { "Saved to redis: ${path.toDoubleQuoted()}" }
          }
        }
      }

  internal val fileList by lazy {
    repo.let { root ->
      when (root) {
        is GitHubRepo -> {
          val path = "${srcPath.ensureSuffix("/")}$packageNameAsPath"

          if (isContentCachingEnabled()) {
            fetchDirContentsFromRedis(path)
              .let {
                if (it.isNotNull() && it.isNotEmpty()) it else fetchRemoteFiles(root, path)
              }
          } else {
            fetchRemoteFiles(root, path)
          }
        }

        is FileSystemSource ->
          File(pathOf(root.pathPrefix, srcPath, packageNameAsPath)).walk().map { it.name }.toList()

        else ->
          error("Invalid repo type: $root")
      }
    }
  }

  // User properties
  var packageName = ""
  var description = ""
  var includeFiles by IncludeFiles(this, languageType)
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

  fun T.unaryPlus() {
    this@ChallengeGroup.checkChallengeName(challengeName)
    this@ChallengeGroup.challenges += this
  }

  fun include(challenge: T) {
    checkChallengeName(challenge.challengeName)
    challenges += challenge
  }

  // @ReadingBatDslMarker
  // fun includeFiles(vararg patterns: String) = import(patterns.toList())

  data class PatternReturnType(val pattern: String, val returnType: ReturnType)

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
