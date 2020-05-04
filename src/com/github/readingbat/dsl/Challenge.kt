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
import com.github.pambrose.common.util.ensureSuffix
import com.github.pambrose.common.util.toDoubleQuoted
import com.github.pambrose.common.util.toSingleQuoted
import com.github.pambrose.common.util.withLineNumbers
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
sealed class Challenge(group: ChallengeGroup<*>) {
  private val challengeId = counter.incrementAndGet()
  private val languageGroup = group.languageGroup
  private val packageName = group.packageName
  internal val languageType = group.languageType
  internal val groupName = group.name

  private val fqName by lazy { packageName.ensureSuffix("/") + fileName.ensureSuffix(".${languageType.suffix}") }
  internal val gitpodUrl by lazy { "${languageGroup.gitpodRoot}$fqName" }
  internal val parsedDescription
      by lazy {
        val options = MutableDataSet().apply { set(HtmlRenderer.SOFT_BREAK, "<br />\n") }
        val parser = Parser.builder(options).build()
        val renderer = HtmlRenderer.builder(options).build()
        val document = parser.parse(description.trimIndent())
        renderer.render(document)
      }

  // User properties
  var name = ""
  var fileName = ""
  var codingBatEquiv = ""
  var description = ""

  internal abstract fun computeFuncInfo(code: String): FunctionInfo

  internal fun funcInfo() =
    sourcesMap
      .computeIfAbsent(challengeId) {
        val path = "${languageGroup.rawRepoRoot}$fqName"
        val (content, dur) = measureTimedValue { URL(path).readText() }
        logger.info { """Fetching "$groupName/$fileName" $path in $dur""" }
        computeFuncInfo(content)
      }

  internal open fun validate() {
    if (name.isEmpty())
      throw InvalidConfigurationException(""""$name" is empty""")

    if (fileName.isEmpty())
      fileName = "$name.${languageType.suffix}"
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
  }
}

class PythonChallenge(group: ChallengeGroup<*>) : Challenge(group) {
  // User properties
  lateinit var returnType: ReturnType

  override fun validate() {
    super.validate()

    if (!this::returnType.isInitialized)
      throw InvalidConfigurationException("$name missing returnType value")
  }

  override fun computeFuncInfo(code: String): FunctionInfo {
    val lines = code.lines()
    val funcCode = extractPythonFunction(lines)
    val args = extractPythonArguments(lines, defMainRegex, ifMainEndRegex)
    val script = convertToPythonScript(lines)
    val answers = mutableListOf<Any>()

    logger.info { "$name return type: $returnType script: \n${script.withLineNumbers()}" }

    val duration =
      PythonScript()
        .run {
          add(varName, answers)
          measureTime { eval(script) }
        }

    logger.info { "$name computed answers in $duration for: $answers" }

    return FunctionInfo(languageType, name, code, funcCode, args, returnType, answers)
  }
}

class JavaChallenge(group: ChallengeGroup<*>) : Challenge(group) {
  override fun computeFuncInfo(code: String): FunctionInfo {
    val lines = code.lines().filter { !it.trimStart().startsWith("package") }
    val funcCode = extractJavaFunction(lines)
    val args = extractJavaArguments(lines, svmRegex, javaEndRegex)
    val returnType = deriveJavaReturnType(name, lines)
    val script = JavaParse.convertToScript(lines)

    logger.info { "$name return type: $returnType script: \n${script.withLineNumbers()}" }

    val timedValue =
      JavaScript()
        .run {
          import(List::class.java)
          import(ArrayList::class.java)
          measureTimedValue { evalScript(script) }
        }

    val answers = timedValue.value
    logger.info { "$name computed answers in ${timedValue.duration} for: $answers" }

    if (answers !is List<*>)
      throw InvalidConfigurationException("Invalid type returned for $name")

    return FunctionInfo(languageType, name, code, funcCode, args, returnType, answers)
  }
}

class KotlinChallenge(group: ChallengeGroup<*>) : Challenge(group) {
  // User properties
  lateinit var returnType: ReturnType

  override fun validate() {
    super.validate()

    if (!this::returnType.isInitialized)
      throw InvalidConfigurationException("$name missing returnType value")
  }

  override fun computeFuncInfo(code: String): FunctionInfo {
    val lines = code.lines().filter { !it.trimStart().startsWith("package") }
    val strippedCode = lines.joinToString("\n")

    val funcCode = "\n${extractKotlinFunction(lines)}\n\n"
    val args = extractKotlinArguments(lines, funMainRegex, kotlinEndRegex)
    val script = convertToKotlinScript(lines)

    logger.info { "$name return type: $returnType script: \n${script.withLineNumbers()}" }

    val answers = mutableListOf<Any>()
    val duration =
      KotlinScript().run {
        add(varName, answers, typeOf<Any>())
        measureTime { eval(script) }
      }

    logger.info { "$name computed answers in $duration for: $answers" }

    return FunctionInfo(languageType, name, strippedCode, funcCode, args, returnType, answers)
  }
}