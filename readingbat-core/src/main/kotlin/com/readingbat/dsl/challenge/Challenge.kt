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

package com.readingbat.dsl.challenge

import ch.obermuhlner.scriptengine.java.Isolation
import com.pambrose.common.util.AbstractRepo
import com.pambrose.common.util.FileSystemSource
import com.pambrose.common.util.ensureSuffix
import com.pambrose.common.util.md5Of
import com.pambrose.common.util.pathOf
import com.pambrose.common.util.toDoubleQuoted
import com.pambrose.common.util.toSingleQuoted
import com.pambrose.common.util.withLineNumbers
import com.readingbat.common.FunctionInfo
import com.readingbat.common.KeyConstants.SOURCE_CODE_KEY
import com.readingbat.common.KeyConstants.keyOf
import com.readingbat.dsl.ChallengeGroup
import com.readingbat.dsl.ContentCaches.sourceCache
import com.readingbat.dsl.LanguageType.Java
import com.readingbat.dsl.LanguageType.Kotlin
import com.readingbat.dsl.LanguageType.Python
import com.readingbat.dsl.MarkdownParser
import com.readingbat.dsl.ReadingBatDslMarker
import com.readingbat.dsl.ReturnType
import com.readingbat.dsl.agentLaunchId
import com.readingbat.dsl.isContentCachingEnabled
import com.readingbat.dsl.parse.JavaParse
import com.readingbat.dsl.parse.JavaParse.convertToScript
import com.readingbat.dsl.parse.JavaParse.deriveJavaReturnType
import com.readingbat.dsl.parse.JavaParse.extractJavaInvocations
import com.readingbat.dsl.parse.KotlinParse
import com.readingbat.dsl.parse.KotlinParse.convertToKotlinScript
import com.readingbat.dsl.parse.KotlinParse.extractKotlinFunction
import com.readingbat.dsl.parse.KotlinParse.extractKotlinInvocations
import com.readingbat.dsl.parse.PythonParse
import com.readingbat.dsl.parse.PythonParse.convertToPythonScript
import com.readingbat.dsl.parse.PythonParse.extractPythonFunction
import com.readingbat.dsl.parse.PythonParse.extractPythonInvocations
import com.readingbat.server.ChallengeName
import com.readingbat.server.Invocation
import com.readingbat.server.ScriptPools
import com.readingbat.utils.toCapitalized
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import java.net.URL
import java.nio.file.Paths
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.reflect.typeOf
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

/**
 * Base class for an individual programming challenge.
 *
 * A challenge corresponds to a single source file containing a function and its test invocations.
 * When loaded, the source code is parsed to extract the function body (shown to the user),
 * the invocations (used as test cases), and the correct answers (computed by evaluating the code
 * via a JSR-223 script engine).
 *
 * Challenges are created within a [ChallengeGroup] via the `challenge("name") { }` DSL block
 * or automatically through `includeFiles` / `includeFilesWithType` patterns.
 *
 * This is a sealed class with language-specific subclasses: [JavaChallenge], [KotlinChallenge],
 * and [PythonChallenge].
 *
 * @property challengeGroup the parent group containing this challenge
 * @property challengeName the challenge identifier, typically matching the source file name
 * @property replaceable whether this challenge can be replaced by a later definition with the same name
 */
@ReadingBatDslMarker
sealed class Challenge(
  val challengeGroup: ChallengeGroup<*>,
  val challengeName: ChallengeName,
  val replaceable: Boolean,
) {
  internal val challengeId = counter.incrementAndFetch()
  private val fqName by lazy { packageNameAsPath.ensureSuffix("/") + fileName.ensureSuffix(".${languageType.suffix}") }

  // Allow description updates only if not found in the Content.kt decl
  private val isDescriptionSetInDsl by lazy { description.isNotBlank() }
  internal val gitpodUrl by lazy { pathOf(repo.sourcePrefix, "blob/$branchName", srcPath, fqName) }
  internal val parsedDescription by lazy { MarkdownParser.toHtml(description) }
  internal val path by lazy { pathOf(languageName, groupName, challengeName) }

  private val languageGroup get() = challengeGroup.languageGroup
  private val metrics get() = challengeGroup.languageGroup.metrics
  private val repo get() = languageGroup.repo
  private val branchName get() = languageGroup.branchName
  private val srcPath get() = languageGroup.srcPath
  internal val languageType get() = challengeGroup.languageType
  internal val languageName get() = languageType.languageName
  internal val groupName get() = challengeGroup.groupName
  protected val packageNameAsPath get() = challengeGroup.packageNameAsPath
  private val content get() = challengeGroup.languageGroup.content

  /** The list of correct answers for each invocation, computed by evaluating the challenge source code. */
  val correctAnswers get() = functionInfo().correctAnswers

  /** Source file name. Defaults to `challengeName.suffix` but can be overridden in the DSL. */
  var fileName = "$challengeName.${languageType.suffix}"

  /** Optional URL or identifier for the equivalent CodingBat problem. */
  var codingBatEquiv = ""

  /** Optional Markdown description displayed above the challenge. Can also be set via `@desc` comments in the source. */
  var description = ""

  /**
   * Parses the challenge source [code] and evaluates it to produce a [FunctionInfo] containing
   * the function body, invocations, return type, and computed correct answers.
   */
  internal abstract suspend fun computeFunctionInfo(code: String): FunctionInfo

  /** Computes an MD5 hash uniquely identifying this challenge by its language, group, and name. */
  fun md5() = md5Of(languageName, groupName, challengeName)

  /** Computes an MD5 hash uniquely identifying a specific invocation of this challenge. */
  fun md5(invocation: Invocation) = md5Of(languageName, groupName, challengeName, invocation)

  private fun measureParsing(code: String) =
    metrics.challengeParseDuration.labels(agentLaunchId(), languageType.toString()).startTimer()
      .let {
        try {
          runBlocking { computeFunctionInfo(code) }
        } finally {
          it.observeDuration()
        }
      }

  private val sourceCodeKey by lazy { keyOf(SOURCE_CODE_KEY, languageType.name, md5()) }

  private fun fetchSourceCodeFromCache() = if (isContentCachingEnabled()) sourceCache[sourceCodeKey] else null

  /**
   * Retrieves or computes the [FunctionInfo] for this challenge. Results are cached when running
   * in production or test mode. Source code is fetched from the configured repo (local or remote).
   */
  fun functionInfo() =
    if (repo.remote) {
      content.functionInfoMap
        .computeIfAbsent(challengeId) {
          val timer = metrics.challengeRemoteReadDuration.labels(agentLaunchId()).startTimer()
          val code =
            fetchSourceCodeFromCache() ?: try {
              val path = pathOf((repo as AbstractRepo).rawSourcePrefix, branchName, srcPath, fqName)
              val (text, dur) = measureTimedValue { URL(path).readText() }
              logger.debug { """Fetched "${pathOf(groupName, fileName)}" in: $dur from: $path""" }

              if (isContentCachingEnabled()) {
                sourceCache[sourceCodeKey] = text
                logger.debug { """Saved "${pathOf(groupName, fileName)}" to content cache""" }
              }
              text
            } finally {
              timer.observeDuration()
            }
          measureParsing(code)
        }
    } else {
      fun parseCode(): FunctionInfo {
        val fs = repo as FileSystemSource
        val file = fs.file(pathOf(fs.pathPrefix, srcPath, packageNameAsPath, fileName))
        logger.info { """Fetching "${file.fileName}" from "${Paths.get("").toAbsolutePath()}""" }
        return measureParsing(file.content)
      }

      if (content.cacheChallenges)
        content.functionInfoMap.computeIfAbsent(challengeId) { parseCode() }
      else
        parseCode()
    }

  internal open fun validate() {
    if (challengeName.value.isEmpty())
      error(""""$challengeName" is empty""")
  }

  @Suppress("unused")
  private fun Any?.prettyQuote(capitalizePythonBooleans: Boolean = true, useDoubleQuotes: Boolean = false) =
    when {
      this is String -> if (languageType.useDoubleQuotes || useDoubleQuotes) toDoubleQuoted() else toSingleQuoted()
      capitalizePythonBooleans && this is Boolean && languageType.isPython -> toString().toCapitalized()
      else -> toString()
    }

  protected fun deriveDescription(code: String, commentPrefix: String) =
    if (isDescriptionSetInDsl) {
      description
    } else {
      code.lines().asSequence()
        .filter { it.startsWith(commentPrefix) && it.contains(DESC) }
        .map { it.replaceFirst(commentPrefix, "") }  // Remove comment prefix
        .map { it.replaceFirst(DESC, "") }           // Remove @desc
        .joinToString("\n") { it.trim() }                      // Strip leading and trailing spaces
        .also { logger.debug { """Assigning $challengeName description = "$it"""" } }
    }

  override fun toString() = "$languageName $groupName $challengeName"

  companion object {
    internal val logger = KotlinLogging.logger {}
    internal val counter = AtomicInt(0)
    internal const val DESC = "@desc "

    internal fun challenge(
      challengeGroup: ChallengeGroup<*>,
      challengeName: ChallengeName,
      replaceable: Boolean,
    ) =
      when (challengeGroup.languageType) {
        Python -> PythonChallenge(challengeGroup, challengeName, replaceable)
        Java -> JavaChallenge(challengeGroup, challengeName, replaceable)
        Kotlin -> KotlinChallenge(challengeGroup, challengeName, replaceable)
      }
  }
}

/** A Java challenge. The return type is inferred from the source code at parse time. */
class JavaChallenge(
  challengeGroup: ChallengeGroup<*>,
  challengeName: ChallengeName,
  replaceable: Boolean,
) : Challenge(challengeGroup, challengeName, replaceable) {
  override suspend fun computeFunctionInfo(code: String): FunctionInfo {
    val lines =
      code.lines()
        .filterNot { it.startsWith("//") && it.contains(DESC) }
        .filterNot { it.trimStart().startsWith("package") }
    val funcCode = JavaParse.extractJavaFunction(lines)
    val invocations = extractJavaInvocations(lines, JavaParse.svmRegex, JavaParse.javaEndRegex)
    val returnType = deriveJavaReturnType(challengeName, lines)
    val script = convertToScript(lines)

    logger.debug { "$challengeName return type: $returnType script: \n${script.withLineNumbers()}" }

    description = deriveDescription(code, "//")

    val timedValue =
      measureTimedValue {
        ScriptPools.javaScriptPool
          .eval {
            assignIsolation(Isolation.IsolatedClassLoader) // https://github.com/eobermuhlner/java-scriptengine
            import(List::class.java)
            import(ArrayList::class.java)
            evalScript(script)
          }
      }

    val correctAnswers = timedValue.value
    logger.debug { "$challengeName computed answers in ${timedValue.duration}" }

    if (correctAnswers !is List<*>)
      error("Invalid type returned for $challengeName [${correctAnswers::class.java.simpleName}]")

    return FunctionInfo(this, Java.languageName, code, funcCode, invocations, returnType, correctAnswers)
  }

  override fun toString() = "JavaChallenge(packageName='$packageNameAsPath', fileName='$fileName')"
}

/** A Kotlin challenge. Requires an explicit `returnType` to be set in the DSL. */
class KotlinChallenge(
  challengeGroup: ChallengeGroup<*>,
  challengeName: ChallengeName,
  replaceable: Boolean,
) : Challenge(challengeGroup, challengeName, replaceable) {
  // User properties
  lateinit var returnType: ReturnType

  override fun validate() {
    super.validate()

    if (!this::returnType.isInitialized)
      error("$challengeName missing returnType value")
  }

  override suspend fun computeFunctionInfo(code: String): FunctionInfo {
    val lines =
      code.lines()
        .filterNot { it.startsWith("//") && it.contains(DESC) }
        .filterNot { it.trimStart().startsWith("package") }
    val strippedCode = lines.joinToString("\n")
    val funcCode = "\n${extractKotlinFunction(lines)}\n\n"
    val invocations = extractKotlinInvocations(lines, KotlinParse.funMainRegex, KotlinParse.kotlinEndRegex)
    val script = convertToKotlinScript(lines).also { logger.debug { "Kotlin: $it" } }
    val correctAnswers = mutableListOf<Any>()

    logger.debug { "$challengeName return type: $returnType script: \n${script.withLineNumbers()}" }

    description = deriveDescription(code, "//")

    measureTime {
      ScriptPools.kotlinScriptPool
        .eval {
          add(KotlinParse.VAR_NAME, correctAnswers, typeOf<Any>())
          eval(script)
        }
    }.also {
      logger.debug { "$challengeName computed answers in $it for: $correctAnswers" }
    }

    return FunctionInfo(
      this,
      Kotlin.languageName,
      strippedCode,
      funcCode,
      invocations,
      returnType,
      correctAnswers,
    )
  }

  override fun toString() =
    "KotlinChallenge(packageName='$packageNameAsPath', fileName='$fileName', returnType=$returnType)"
}

/** A Python challenge. Requires an explicit `returnType` to be set in the DSL. */
class PythonChallenge(
  challengeGroup: ChallengeGroup<*>,
  challengeName: ChallengeName,
  replaceable: Boolean,
) : Challenge(challengeGroup, challengeName, replaceable) {
  // User properties
  lateinit var returnType: ReturnType

  override fun validate() {
    super.validate()

    if (!this::returnType.isInitialized)
      error("$challengeName missing returnType value")
  }

  override suspend fun computeFunctionInfo(code: String): FunctionInfo {
    val lines = code.lines().filterNot { it.startsWith("#") && it.contains(DESC) }
    val funcCode = extractPythonFunction(lines)
    val invocations = extractPythonInvocations(lines, PythonParse.defMainRegex, PythonParse.ifMainEndRegex)
    val script = convertToPythonScript(lines)
    val correctAnswers = mutableListOf<Any>()

    logger.debug { "$challengeName return type: $returnType script: \n${script.withLineNumbers()}" }

    description = deriveDescription(code, "#")

    measureTime {
      ScriptPools.pythonScriptPool
        .eval {
          add(PythonParse.VAR_NAME, correctAnswers)
          eval(script)
        }
    }.also {
      logger.debug { "$challengeName computed answers in $it for: $correctAnswers" }
    }

    return FunctionInfo(this, Python.languageName, code, funcCode, invocations, returnType, correctAnswers)
  }

  override fun toString() =
    "PythonChallenge(packageName='$packageNameAsPath', fileName='$fileName', returnType=$returnType)"
}
