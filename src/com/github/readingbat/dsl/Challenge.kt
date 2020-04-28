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

import com.github.pambrose.common.util.*
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet
import mu.KLogging
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.measureTimedValue

@ReadingBatDslMarker
sealed class Challenge(private val group: ChallengeGroup) {
  private val paramSignature = mutableListOf<String>()
  private var multiArgTypes = ""
  private val challengeId = counter.incrementAndGet()

  internal val inputOutput = mutableListOf<Pair<String, String>>()
  internal val languageType = group.languageType
  internal val groupName = group.name
  private val packageName = group.packageName

  private val fqName by lazy { packageName.ensureSuffix("/") + fileName.ensureSuffix(".${languageType.suffix}") }
  internal val gitpodUrl by lazy { "${group.languageGroup.gitpodRoot}$fqName" }
  internal val parsedDescription
      by lazy {
        val options = MutableDataSet()
          .apply { set(HtmlRenderer.SOFT_BREAK, "<br />\n") }
        val parser = Parser.builder(options).build()
        val renderer = HtmlRenderer.builder(options).build()
        val document = parser.parse(description.trimIndent())
        renderer.render(document)
      }

  var name = ""
  var fileName = ""
  var codingBatEquiv = ""
  var description = ""

  internal abstract fun deriveFuncInfo(code: String): FuncInfo

  internal fun funcInfo() =
    sourcesMap
      .computeIfAbsent(challengeId) {
        val path = "${group.languageGroup.rawRepoRoot}$fqName"
        val (content, dur) = measureTimedValue { URL(path).readText() }
        logger.info { """Fetching "${group.name}/$fileName" $path in $dur""" }
        deriveFuncInfo(content)
      }

  internal fun validate() {
    if (name.isEmpty())
      throw InvalidConfigurationException(""""$name" is empty""")

    if (fileName.isEmpty())
      fileName = "$name.${languageType.suffix}"

    if (multiArgTypes.isNotEmpty())
      throw InvalidConfigurationException(""""$name" has $multiArgTypes""")

    val set = paramSignature.toSet()
    if (set.size > 1)
      throw InvalidConfigurationException(""""$name" has inconsistent function arguments: """ +
                                              set.joinToString(" and ") { "($it)" })
  }

  private fun Any?.prettyQuote(capitalizePythonBooleans: Boolean = true, useDoubleQuotes: Boolean = false) =
    when {
      this is String -> if (languageType.useDoubleQuotes || useDoubleQuotes) toDoubleQuoted() else toSingleQuoted()
      capitalizePythonBooleans && this is Boolean && languageType.isPython() -> toString().capitalize()
      else -> toString()
    }

  private fun List<*>.toQuotedStrings() = "[${map { it.prettyQuote() }.toCsv()}]"

  private fun processArrayTypes(types: List<String>, arg: List<*>) {
    val typesSet = types.toSet()
    if (typesSet.size > 1)
      multiArgTypes = "array values with inconsistent types: $typesSet in array ${arg.toQuotedStrings()}"
  }

  infix fun <A : Any, B : Any> A.returns(solution: B) {
    inputOutput.add(prettyQuote() to solution.prettyQuote(useDoubleQuotes = true))
    paramSignature.add(simpleClassName)
  }

  infix fun <A : Any, B : Any> List<A>.returns(solution: B) {
    val argTypes = mutableListOf<String>()
    val args =
      this.map { arg ->
        when (arg) {
          is List<*> -> {
            val arrayTypes = mutableListOf<String>()
            val quotedVals =
              arg.map {
                arrayTypes += it?.simpleClassName ?: "Null"
                it.prettyQuote()
              }
            processArrayTypes(arrayTypes, arg)
            argTypes += "[${arrayTypes.toCsv()}]"
            quotedVals.toString()
          }
          else -> {
            argTypes += arg.simpleClassName
            arg.prettyQuote()
          }
        }
      }.toCsv()

    inputOutput.add(args to solution.prettyQuote(useDoubleQuotes = true))
    paramSignature.add(argTypes.toCsv())
  }

  override fun toString() = "AbstractChallenge(packageName='$packageName', fileName='$fileName')"

  companion object : KLogging() {
    internal val counter = AtomicInteger(0)
    internal val sourcesMap = ConcurrentHashMap<Int, FuncInfo>()
  }
}

class PythonChallenge(group: ChallengeGroup) : Challenge(group) {
  override fun deriveFuncInfo(code: String): FuncInfo {
    val lines = code.split("\n")
    val lineNums =
      lines.mapIndexed { i, str -> i to str }
        .filter { it.second.contains(Regex("^def.*\\(")) }
        .map { it.first }

    val funcCode = lines.subList(lineNums.first(), lineNums.last() - 1).joinToString("\n").trimIndent()
    return FuncInfo(code, funcCode, lines.pythonInvokes(pythonStart, pythonEnd))
  }

  companion object {
    internal val pythonStart = Regex("def main\\(")
    internal val pythonEnd = Regex("__main__")

    internal fun String.pythonInvokes(start: Regex, end: Regex) = split("\n").pythonInvokes(start, end)

    internal fun List<String>.pythonInvokes(start: Regex, end: Regex) =
      linesBetween(start, end)
        .filter { it.contains("print(") }
        .map { it.trim() }
        .map { it.replaceFirst("print(", "") }
        .map { it.substring(0, it.indexOfLast { c -> c == ')' }) }
  }
}

class JavaChallenge(group: ChallengeGroup) : Challenge(group) {
  override fun deriveFuncInfo(code: String): FuncInfo {
    val lines = code.split("\n")
    val lineNums =
      lines.mapIndexed { i, str -> i to str }
        .filter { it.second.contains(Regex("static.*\\(")) }
        .map { it.first }

    val funcCode = lines.subList(lineNums.first(), lineNums.last() - 1).joinToString("\n").trimIndent()
    return FuncInfo(code, funcCode, lines.javaInvokes(javaStart, javaEnd))
  }

  companion object {

    internal val javaStart = Regex("static\\svoid\\smain\\(")
    internal val javaEnd = Regex("}")

    internal fun String.javaInvokes(start: Regex, end: Regex) = split("\n").javaInvokes(start, end)

    internal fun List<String>.javaInvokes(start: Regex, end: Regex) =
      linesBetween(start, end)
        .filter { it.contains("System.out.println(") }
        .map { it.trim() }
        .map { it.replaceFirst("System.out.println(", "") }
        .map { it.substring(0, it.indexOfLast { c -> c == ')' }) }
  }
}

class KotlinChallenge(group: ChallengeGroup) : Challenge(group) {
  val id = counter.incrementAndGet()

  override fun deriveFuncInfo(code: String): FuncInfo {
    val lines = code.split("\n").filter { !it.trimStart().startsWith("package") }
    val funcCode = lines.subList(0, lines.lastLineNumberOf(Regex("fun main\\("))).joinToString("\n").trimIndent()
    return FuncInfo(lines.joinToString("\n"), "\n$funcCode\n\n", lines.kotlinInvokes(kotlinStart, kotlinEnd))
  }

  companion object {
    internal val kotlinStart = Regex("static\\svoid\\smain\\(")
    internal val kotlinEnd = Regex("}")

    internal fun String.kotlinInvokes(start: Regex, end: Regex) = split("\n").kotlinInvokes(start, end)

    internal fun List<String>.kotlinInvokes(start: Regex, end: Regex) =
      linesBetween(start, end)
        .filter { it.contains("println(") }
        .map { it.trim() }
        .map { it.replaceFirst("println(", "") }
        .map { it.substring(0, it.indexOfLast { c -> c == ')' }) }
  }
}

class FuncInfo(val code: String, val snippet: String, val invokes: List<String>)
