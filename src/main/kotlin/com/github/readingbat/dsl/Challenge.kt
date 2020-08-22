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

import com.github.pambrose.common.script.JavaScript
import com.github.pambrose.common.script.KotlinScript
import com.github.pambrose.common.script.PythonScript
import com.github.pambrose.common.util.AbstractRepo
import com.github.pambrose.common.util.FileSystemSource
import com.github.pambrose.common.util.ensureSuffix
import com.github.pambrose.common.util.toDoubleQuoted
import com.github.pambrose.common.util.toSingleQuoted
import com.github.pambrose.common.util.withLineNumbers
import com.github.readingbat.dsl.LanguageType.Java
import com.github.readingbat.dsl.LanguageType.Kotlin
import com.github.readingbat.dsl.LanguageType.Python
import com.github.readingbat.dsl.parse.JavaParse
import com.github.readingbat.dsl.parse.JavaParse.deriveJavaReturnType
import com.github.readingbat.dsl.parse.JavaParse.extractJavaFunction
import com.github.readingbat.dsl.parse.JavaParse.extractJavaInvocations
import com.github.readingbat.dsl.parse.JavaParse.javaEndRegex
import com.github.readingbat.dsl.parse.JavaParse.svmRegex
import com.github.readingbat.dsl.parse.KotlinParse.convertToKotlinScript
import com.github.readingbat.dsl.parse.KotlinParse.extractKotlinFunction
import com.github.readingbat.dsl.parse.KotlinParse.extractKotlinInvocations
import com.github.readingbat.dsl.parse.KotlinParse.funMainRegex
import com.github.readingbat.dsl.parse.KotlinParse.kotlinEndRegex
import com.github.readingbat.dsl.parse.KotlinParse.varName
import com.github.readingbat.dsl.parse.PythonParse.convertToPythonScript
import com.github.readingbat.dsl.parse.PythonParse.defMainRegex
import com.github.readingbat.dsl.parse.PythonParse.extractPythonFunction
import com.github.readingbat.dsl.parse.PythonParse.extractPythonInvocations
import com.github.readingbat.dsl.parse.PythonParse.ifMainEndRegex
import com.github.readingbat.misc.PageUtils.pathOf
import com.github.readingbat.server.ChallengeName
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet
import mu.KLogging
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.typeOf
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

@ReadingBatDslMarker
sealed class Challenge(val challengeGroup: ChallengeGroup<*>,
                       val challengeName: ChallengeName,
                       val replaceable: Boolean) {
  private val challengeId = counter.incrementAndGet()
  private val fqName by lazy { packageName.ensureSuffix("/") + fileName.ensureSuffix(".${languageType.suffix}") }
  private val options = MutableDataSet().apply { set(HtmlRenderer.SOFT_BREAK, "<br />\n") }
  private val parser = Parser.builder(options).build()
  private val renderer = HtmlRenderer.builder(options).build()

  private val languageGroup get() = challengeGroup.languageGroup
  private val metrics get() = challengeGroup.languageGroup.metrics
  private val repo get() = languageGroup.repo
  private val branchName get() = languageGroup.branchName
  private val srcPath get() = languageGroup.srcPath
  internal val languageType get() = challengeGroup.languageType
  internal val languageName get() = languageType.languageName
  internal val groupName get() = challengeGroup.groupName
  protected val packageName get() = challengeGroup.packageName

  // Allow description updates only if not found in the Content.kt decl
  private val descriptionSetInDsl by lazy { description.isNotBlank() }

  internal val gitpodUrl by lazy { pathOf(repo.sourcePrefix, "blob/${branchName}", srcPath, fqName) }
  internal val parsedDescription: String
    get() {
      val document = parser.parse(description.trimIndent())
      return renderer.render(document)
    }

  // User properties
  var fileName = "$challengeName.${languageType.suffix}"
  var codingBatEquiv = ""
  var description = ""

  internal abstract fun computeFuncInfo(code: String): FunctionInfo

  private fun measureParsing(code: String): FunctionInfo {
    val timer = metrics.challengeParseDuration.labels(agentLaunchId(), languageType.toString()).startTimer()
    try {
      return computeFuncInfo(code)
    } finally {
      timer.observeDuration()
    }
  }

  internal fun funcInfo(content: ReadingBatContent) =
    if (repo.remote) {
      content.sourcesMap
        .computeIfAbsent(challengeId) {
          fun fetchCode(): String {
            val timer = metrics.challengeRemoteReadDuration.labels(agentLaunchId()).startTimer()
            return try {
              val path = pathOf((repo as AbstractRepo).rawSourcePrefix, branchName, srcPath, fqName)
              val (code, dur) = measureTimedValue { URL(path).readText() }
              logger.info { """Fetched "$groupName/$fileName" in: $dur from: $path""" }
              code
            } finally {
              timer.observeDuration()
            }
          }

          val code = fetchCode()
          measureParsing(code)
        }
    }
    else {
      fun parseCodeFunc(): FunctionInfo {
        val fs = repo as FileSystemSource
        val file = fs.file(pathOf(fs.pathPrefix, srcPath, packageName, fileName))
        logger.info { """Fetching "${file.fileName}" from filesystem""" }
        return measureParsing(file.content)
      }

      if (content.cacheChallenges)
        content.sourcesMap.computeIfAbsent(challengeId) { parseCodeFunc() }
      else
        parseCodeFunc()
    }

  internal open fun validate() {
    if (challengeName.value.isEmpty())
      throw InvalidConfigurationException(""""$challengeName" is empty""")
  }

  private fun Any?.prettyQuote(capitalizePythonBooleans: Boolean = true, useDoubleQuotes: Boolean = false) =
    when {
      this is String -> if (languageType.useDoubleQuotes || useDoubleQuotes) toDoubleQuoted() else toSingleQuoted()
      capitalizePythonBooleans && this is Boolean && languageType.isPython() -> toString().capitalize()
      else -> toString()
    }

  protected fun deriveDescription(code: String, commentPrefix: String) =
    if (descriptionSetInDsl) {
      description
    }
    else {
      code.lines().asSequence()
        .filter { it.startsWith(commentPrefix) && it.contains(DESC) }
        .map { it.replaceFirst(commentPrefix, "") }   // Remove comment prefix
        .map { it.replaceFirst(DESC, "") }            // Remove @desc
        .map { it.trim() }                            // Strip leading and trailing spaces
        .joinToString("\n")
        .also { logger.debug { """Assigning $challengeName description = "$it"""" } }
    }

  companion object : KLogging() {
    internal val counter = AtomicInteger(0)
    internal const val DESC = "@desc "

    internal fun challenge(challengeGroup: ChallengeGroup<*>,
                           challengeName: ChallengeName,
                           replaceable: Boolean) =
      when (challengeGroup.languageType) {
        Python -> PythonChallenge(challengeGroup, challengeName, replaceable)
        Java -> JavaChallenge(challengeGroup, challengeName, replaceable)
        Kotlin -> KotlinChallenge(challengeGroup, challengeName, replaceable)
      }
  }
}

class PythonChallenge(challengeGroup: ChallengeGroup<*>,
                      challengeName: ChallengeName,
                      replaceable: Boolean) :
  Challenge(challengeGroup, challengeName, replaceable) {

  // User properties
  lateinit var returnType: ReturnType

  override fun validate() {
    super.validate()

    if (!this::returnType.isInitialized)
      throw InvalidConfigurationException("$challengeName missing returnType value")
  }

  override fun computeFuncInfo(code: String): FunctionInfo {
    val lines = code.lines().filterNot { it.startsWith("#") && it.contains(DESC) }
    val funcCode = extractPythonFunction(lines)
    val invocations = extractPythonInvocations(lines, defMainRegex, ifMainEndRegex)
    val script = convertToPythonScript(lines)
    val correctAnswers = mutableListOf<Any>()

    logger.debug { "$challengeName return type: $returnType script: \n${script.withLineNumbers()}" }

    description = deriveDescription(code, "#")

    val duration =
      PythonScript().use {
        it.run {
          add(varName, correctAnswers)
          measureTime { eval(script) }
        }
      }

    logger.debug { "$challengeName computed answers in $duration for: $correctAnswers" }

    return FunctionInfo(languageType,
                        challengeGroup,
                        challengeName,
                        code,
                        funcCode,
                        invocations,
                        returnType,
                        correctAnswers)
  }

  override fun toString() = "PythonChallenge(packageName='$packageName', fileName='$fileName', returnType=$returnType)"
}

class JavaChallenge(challengeGroup: ChallengeGroup<*>,
                    challengeName: ChallengeName,
                    replaceable: Boolean) :
  Challenge(challengeGroup, challengeName, replaceable) {

  override fun computeFuncInfo(code: String): FunctionInfo {
    val lines =
      code.lines()
        .filterNot { it.startsWith("//") && it.contains(DESC) }
        .filterNot { it.trimStart().startsWith("package") }
    val funcCode = extractJavaFunction(lines)
    val invocations = extractJavaInvocations(lines, svmRegex, javaEndRegex)
    val returnType = deriveJavaReturnType(challengeName, lines)
    val script = JavaParse.convertToScript(lines)

    logger.debug { "$challengeName return type: $returnType script: \n${script.withLineNumbers()}" }

    description = deriveDescription(code, "//")

    val timedValue =
      JavaScript().use {
        it.run {
          import(List::class.java)
          import(ArrayList::class.java)
          measureTimedValue { evalScript(script) }
        }
      }

    val correctAnswers = timedValue.value
    logger.debug { "$challengeName computed answers in ${timedValue.duration}" }

    if (correctAnswers !is List<*>)
      throw InvalidConfigurationException("Invalid type returned for $challengeName")

    return FunctionInfo(languageType,
                        challengeGroup,
                        challengeName,
                        code,
                        funcCode,
                        invocations,
                        returnType,
                        correctAnswers)
  }

  override fun toString() = "JavaChallenge(packageName='$packageName', fileName='$fileName')"
}

class KotlinChallenge(challengeGroup: ChallengeGroup<*>,
                      challengeName: ChallengeName,
                      replaceable: Boolean) :
  Challenge(challengeGroup, challengeName, replaceable) {

  // User properties
  lateinit var returnType: ReturnType

  override fun validate() {
    super.validate()

    if (!this::returnType.isInitialized)
      throw InvalidConfigurationException("$challengeName missing returnType value")
  }

  override fun computeFuncInfo(code: String): FunctionInfo {
    val lines =
      code.lines()
        .filterNot { it.startsWith("//") && it.contains(DESC) }
        .filterNot { it.trimStart().startsWith("package") }
    val strippedCode = lines.joinToString("\n")
    val funcCode = "\n${extractKotlinFunction(lines)}\n\n"
    val invocations = extractKotlinInvocations(lines, funMainRegex, kotlinEndRegex)
    val script = convertToKotlinScript(lines)

    logger.debug { "$challengeName return type: $returnType script: \n${script.withLineNumbers()}" }

    description = deriveDescription(code, "//")

    val correctAnswers = mutableListOf<Any>()
    val duration =
      KotlinScript().use {
        it.run {
          add(varName, correctAnswers, typeOf<Any>())
          measureTime { eval(script) }
        }
      }

    logger.debug { "$challengeName computed answers in $duration for: $correctAnswers" }

    return FunctionInfo(languageType,
                        challengeGroup,
                        challengeName,
                        strippedCode,
                        funcCode,
                        invocations,
                        returnType,
                        correctAnswers)
  }

  override fun toString() = "KotlinChallenge(packageName='$packageName', fileName='$fileName', returnType=$returnType)"
}