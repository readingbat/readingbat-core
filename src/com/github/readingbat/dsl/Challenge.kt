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
import com.github.pambrose.common.util.*
import com.github.readingbat.InvalidConfigurationException
import com.github.readingbat.dsl.LanguageType.*
import com.github.readingbat.dsl.parse.JavaParse
import com.github.readingbat.dsl.parse.JavaParse.deriveJavaReturnType
import com.github.readingbat.dsl.parse.JavaParse.extractJavaArguments
import com.github.readingbat.dsl.parse.JavaParse.extractJavaFunction
import com.github.readingbat.dsl.parse.JavaParse.javaEndRegex
import com.github.readingbat.dsl.parse.JavaParse.svmRegex
import com.github.readingbat.dsl.parse.KotlinParse.convertToKotlinScript
import com.github.readingbat.dsl.parse.KotlinParse.extractKotlinArguments
import com.github.readingbat.dsl.parse.KotlinParse.extractKotlinFunction
import com.github.readingbat.dsl.parse.KotlinParse.funMainRegex
import com.github.readingbat.dsl.parse.KotlinParse.kotlinEndRegex
import com.github.readingbat.dsl.parse.KotlinParse.varName
import com.github.readingbat.dsl.parse.PythonParse.convertToPythonScript
import com.github.readingbat.dsl.parse.PythonParse.defMainRegex
import com.github.readingbat.dsl.parse.PythonParse.extractPythonArguments
import com.github.readingbat.dsl.parse.PythonParse.extractPythonFunction
import com.github.readingbat.dsl.parse.PythonParse.ifMainEndRegex
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet
import mu.KLogging
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.typeOf
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

@ReadingBatDslMarker
sealed class Challenge(challengeGroup: ChallengeGroup<*>, val challengeName: String, val replaceable: Boolean) {
  private val challengeId = counter.incrementAndGet()
  private val languageGroup = challengeGroup.languageGroup
  private val repo = languageGroup.repo
  private val branchName = languageGroup.branchName
  internal val srcPath = languageGroup.srcPath
  private val packageName = challengeGroup.packageName
  internal val languageType = challengeGroup.languageType
  internal val groupName = challengeGroup.groupName

  private val fqName by lazy { packageName.ensureSuffix("/") + fileName.ensureSuffix(".${languageType.suffix}") }
  internal val gitpodUrl by lazy { listOf(repo.sourcePrefix, "blob/${branchName}", srcPath, fqName).toPath(false) }

  internal val parsedDescription
      by lazy {
        val options = MutableDataSet().apply { set(HtmlRenderer.SOFT_BREAK, "<br />\n") }
        val parser = Parser.builder(options).build()
        val renderer = HtmlRenderer.builder(options).build()
        val document = parser.parse(description.trimIndent())
        renderer.render(document)
      }

  // User properties
  var fileName = "$challengeName.${languageType.suffix}"
  var codingBatEquiv = ""
  var description = ""

  internal abstract fun computeFuncInfo(code: String): FunctionInfo

  private val compute = {
    val fs = repo as FileSystemSource
    val file = fs.file(listOf(fs.pathPrefix, srcPath, packageName, fileName).toPath())
    logger.info { """Fetching "${file.fileName}"""" }
    computeFuncInfo(file.content)
  }

  internal fun funcInfo(readingBatContent: ReadingBatContent): FunctionInfo =
    if (repo.remote) {
      sourcesMap
        .computeIfAbsent(challengeId) {
          val path = listOf((repo as AbstractRepo).rawSourcePrefix, branchName, srcPath, fqName).toPath(false)
          logger.info { """Fetching "$groupName/$fileName" from: $path""" }
          val (content, dur) = measureTimedValue { URL(path).readText() }
          logger.info { """Fetched "$groupName/$fileName" in: $dur""" }
          computeFuncInfo(content)
        }
    }
    else {
      if (readingBatContent.cacheChallenges)
        sourcesMap.computeIfAbsent(challengeId) { compute.invoke() }
      else
        compute.invoke()
    }

  internal open fun validate() {
    if (challengeName.isEmpty())
      throw InvalidConfigurationException(""""$challengeName" is empty""")
  }

  private fun Any?.prettyQuote(capitalizePythonBooleans: Boolean = true, useDoubleQuotes: Boolean = false) =
    when {
      this is String -> if (languageType.useDoubleQuotes || useDoubleQuotes) toDoubleQuoted() else toSingleQuoted()
      capitalizePythonBooleans && this is Boolean && languageType.isPython() -> toString().capitalize()
      else -> toString()
    }

  override fun toString() = "AbstractChallenge(packageName='$packageName', fileName='$fileName')"

  companion object : KLogging() {
    internal val counter = AtomicInteger(0)
    internal val sourcesMap = ConcurrentHashMap<Int, FunctionInfo>()

    internal fun challenge(challengeGroup: ChallengeGroup<*>, challengeName: String, replaceable: Boolean) =
      when (challengeGroup.languageType) {
        Python -> PythonChallenge(challengeGroup, challengeName, replaceable)
        Java -> JavaChallenge(challengeGroup, challengeName, replaceable)
        Kotlin -> KotlinChallenge(challengeGroup, challengeName, replaceable)
      }
  }
}

class PythonChallenge(challengeGroup: ChallengeGroup<*>, challengeName: String, replaceable: Boolean) :
  Challenge(challengeGroup, challengeName, replaceable) {

  // User properties
  lateinit var returnType: ReturnType

  override fun validate() {
    super.validate()

    if (!this::returnType.isInitialized)
      throw InvalidConfigurationException("$challengeName missing returnType value")
  }

  override fun computeFuncInfo(code: String): FunctionInfo {
    val lines = code.lines()
    val funcCode = extractPythonFunction(lines)
    val args = extractPythonArguments(lines, defMainRegex, ifMainEndRegex)
    val script = convertToPythonScript(lines)
    val answers = mutableListOf<Any>()

    logger.info { "$challengeName return type: $returnType script: \n${script.withLineNumbers()}" }

    val duration =
      PythonScript()
        .run {
          add(varName, answers)
          measureTime { eval(script) }
        }

    logger.info { "$challengeName computed answers in $duration for: $answers" }

    return FunctionInfo(languageType, challengeName, code, funcCode, args, returnType, answers)
  }
}

class JavaChallenge(challengeGroup: ChallengeGroup<*>, challengeName: String, replaceable: Boolean) :
  Challenge(challengeGroup, challengeName, replaceable) {

  override fun computeFuncInfo(code: String): FunctionInfo {
    val lines = code.lines().filter { !it.trimStart().startsWith("package") }
    val funcCode = extractJavaFunction(lines)
    val args = extractJavaArguments(lines, svmRegex, javaEndRegex)
    val returnType = deriveJavaReturnType(challengeName, lines)
    val script = JavaParse.convertToScript(lines)

    logger.info { "$challengeName return type: $returnType script: \n${script.withLineNumbers()}" }

    val timedValue =
      JavaScript()
        .run {
          import(List::class.java)
          import(ArrayList::class.java)
          measureTimedValue { evalScript(script) }
        }

    val answers = timedValue.value
    logger.info { "$challengeName computed answers in ${timedValue.duration} for: $answers" }

    if (answers !is List<*>)
      throw InvalidConfigurationException("Invalid type returned for $challengeName")

    return FunctionInfo(languageType, challengeName, code, funcCode, args, returnType, answers)
  }
}

class KotlinChallenge(challengeGroup: ChallengeGroup<*>, challengeName: String, replaceable: Boolean) :
  Challenge(challengeGroup, challengeName, replaceable) {

  // User properties
  lateinit var returnType: ReturnType

  override fun validate() {
    super.validate()

    if (!this::returnType.isInitialized)
      throw InvalidConfigurationException("$challengeName missing returnType value")
  }

  override fun computeFuncInfo(code: String): FunctionInfo {
    val lines = code.lines().filter { !it.trimStart().startsWith("package") }
    val strippedCode = lines.joinToString("\n")

    val funcCode = "\n${extractKotlinFunction(lines)}\n\n"
    val args = extractKotlinArguments(lines, funMainRegex, kotlinEndRegex)
    val script = convertToKotlinScript(lines)

    logger.info { "$challengeName return type: $returnType script: \n${script.withLineNumbers()}" }

    val answers = mutableListOf<Any>()
    val duration =
      KotlinScript().run {
        add(varName, answers, typeOf<Any>())
        measureTime { eval(script) }
      }

    logger.info { "$challengeName computed answers in $duration for: $answers" }

    return FunctionInfo(languageType, challengeName, strippedCode, funcCode, args, returnType, answers)
  }
}