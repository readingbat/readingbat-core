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
import com.github.pambrose.common.util.*
import com.github.readingbat.ReturnType
import com.github.readingbat.ReturnType.Companion.asReturnType
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet
import mu.KLogging
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.measureTimedValue

@ReadingBatDslMarker
sealed class Challenge(group: ChallengeGroup<*>) {
  private val paramSignature = mutableListOf<String>()
  private var multiArgTypes = ""
  private val challengeId = counter.incrementAndGet()

  private val inputOutput = mutableListOf<Pair<String, String>>()
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

    //if (returnType == null)
    //  throw InvalidConfigurationException("$name is missing returnType property value")
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
    internal val sourcesMap = ConcurrentHashMap<Int, FunctionInfo>()
  }
}

class PythonChallenge(group: ChallengeGroup<*>) : Challenge(group) {

  // User properties
  lateinit var returnType: ReturnType

  override fun computeFuncInfo(code: String): FunctionInfo {
    val lines = code.split("\n")
    val lineNums =
      lines.mapIndexed { i, str -> i to str }
        .filter { it.second.contains(Regex("^def.*\\(")) }
        .map { it.first }

    val funcCode = lines.subList(lineNums.first(), lineNums.last() - 1).joinToString("\n").trimIndent()
    val args = lines.pythonArguments(pythonStartRegex, pythonEndRegex)

    if (!this::returnType.isInitialized)
      throw InvalidConfigurationException("$name missing returnType value")

    return FunctionInfo(name, code, funcCode, args, returnType, listOf<Any>())
  }

  companion object {
    internal val pythonStartRegex = Regex("def main\\(")
    internal val pythonEndRegex = Regex("__main__")

    internal fun String.pythonArguments(start: Regex, end: Regex) = split("\n").pythonArguments(start, end)

    internal fun List<String>.pythonArguments(start: Regex, end: Regex) =
      linesBetween(start, end)
        .filter { it.contains("print(") }
        .map { it.trim() }
        .map { it.replaceFirst("print(", "") }
        .map { it.substring(0, it.indexOfLast { c -> c == ')' }) }
  }
}

class JavaChallenge(group: ChallengeGroup<*>) : Challenge(group) {
  override fun computeFuncInfo(code: String): FunctionInfo {
    val lines = code.split("\n").filter { !it.trimStart().startsWith("package") }
    val lineNums =
      lines.mapIndexed { i, str -> i to str }
        .filter { it.second.contains(staticRegex) }
        .map { it.first }

    val funcCode = lines.subList(lineNums.first(), lineNums.last() - 1).joinToString("\n").trimIndent()
    val args = lines.javaArguments(svmRegex, javaEndRegex)
    val derivedReturnType = lines.deriveReturnType(this)
    val script = lines.convertToScript()

    logger.info { "$name return type: $derivedReturnType script: \n$script" }

    val rawAnswers: Any
    JavaScript()
      .apply {
        import(List::class.java)
        import(ArrayList::class.java)
        val timedValue = measureTimedValue { evalScript(script) }
        rawAnswers = timedValue.value
        logger.info { "$name computed answers in ${timedValue.duration} for: $rawAnswers" }
      }

    if (rawAnswers !is List<*>)
      throw InvalidConfigurationException("Invalid type returned for $name")

    return FunctionInfo(name, code, funcCode, args, derivedReturnType, rawAnswers)
  }

  companion object : KLogging() {
    private val spaceRegex = Regex("""\s+""")
    private val staticRegex = Regex("""static.+\(""")
    private val staticStartRegex = Regex("""\sstatic.+\(""")
    private val psRegex = Regex("""^\s+public\s+static.+\(""")
    internal val javaEndRegex = Regex("""\s*}\s*""")
    private val svmRegex = Regex("""\s*static\s+void\s+main\(""")
    internal val psvmRegex = Regex("""^\s*public\s+static\s+void\s+main.+\)""")

    private val prefixRegex =
      listOf(Regex("""System\.out\.println\("""),
             Regex("""ArrayUtils\.arrayPrint\("""),
             Regex("""ListUtils\.listPrint\("""),
             Regex("""arrayPrint\("""),
             Regex("""listPrint\("""))
    private val prefixes =
      listOf("System.out.println", "ArrayUtils.arrayPrint", "ListUtils.listPrint", "arrayPrint", "listPrint")

    internal fun String.javaArguments(start: Regex, end: Regex) = split("\n").javaArguments(start, end)

    internal fun List<String>.javaArguments(start: Regex, end: Regex): List<String> {
      val lines = mutableListOf<String>()
      prefixes.forEach { prefix ->
        lines.addAll(
          linesBetween(start, end)
            .map { it.trim() }
            .filter { it.startsWith("$prefix(") }
            .map { it.replaceFirst("$prefix(", "") }
            .map { it.substring(0, it.indexOfLast { c -> c == ')' }) })
      }
      return lines
    }

    internal fun List<String>.deriveReturnType(challenge: JavaChallenge) =
      this
        .asSequence()
        .filter { !it.contains(svmRegex) && (it.contains(staticStartRegex) || it.contains(psRegex)) }
        .map { str ->
          val words = str.trim().split(spaceRegex).filter { it.isNotBlank() }
          val staticPos = words.indices.first { words[it] == "static" }
          val typeStr = words[staticPos + 1]
          typeStr.asReturnType ?: throw InvalidConfigurationException("In ${challenge.name} invalid type $typeStr")
        }
        .firstOrNull() ?: throw InvalidConfigurationException("In ${challenge.name} unable to determine return type")

    internal fun List<String>.convertToScript(): String {
      val scriptCode = mutableListOf<String>()
      val varName = "answers"
      var exprIndent = 0
      var insideMain = false

      this.forEach { line ->
        when {
          line.contains(psvmRegex) -> {
            val publicIndent = line.indexOf("public")
            insideMain = true
            scriptCode += "".padStart(publicIndent) + "public List<Object> $varName = new ArrayList<Object>();"
            scriptCode += ""
            scriptCode += line.replace(psvmRegex, "public List<Object> getValue()")
          }
          insideMain && prefixRegex.any { line.contains(it) } -> {
            exprIndent = kotlin.math.max(0, prefixRegex.map { line.indexOf(it.pattern.substring(0, 6)) }.max()!!)
            val firstParen = line.indexOfFirst { it == '(' }
            val lastParen = line.indexOfLast { it == ')' }
            val expr = line.substring(firstParen + 1, lastParen)
            logger.debug { "Content from: $firstParen to: $lastParen is: $expr" }
            val str = "".padStart(exprIndent) + "$varName.add($expr);"
            logger.debug { "Transformed:\n$line\nto:\n$str" }
            scriptCode += str
          }
          insideMain && line.trim().startsWith("}") -> {
            logger.info { "Inserting return stmt" }
            insideMain = false
            scriptCode += ""
            scriptCode += "".padStart(exprIndent) + "return $varName;"
            scriptCode += line
          }
          else -> {
            scriptCode += line
          }
        }
      }
      return scriptCode.joinToString("\n")
    }
  }
}

class KotlinChallenge(group: ChallengeGroup<*>) : Challenge(group) {

  // User properties
  lateinit var returnType: ReturnType

  override fun computeFuncInfo(code: String): FunctionInfo {
    val lines = code.split("\n").filter { !it.trimStart().startsWith("package") }
    val funcCode = lines.subList(0, lines.lastLineNumberOf(kotlinStartRegex)).joinToString("\n").trimIndent()
    val args = lines.kotlinArguments(kotlinStartRegex, kotlinEndRegex)
    val originalCode = lines.joinToString("\n")
    val codeSnippet = "\n$funcCode\n\n"

    if (!this::returnType.isInitialized)
      throw InvalidConfigurationException("$name missing returnType value")

    return FunctionInfo(name, originalCode, codeSnippet, args, returnType, listOf<Any>())
  }

  companion object {
    internal val kotlinStartRegex = Regex("""^\s*fun\s*main.*\)""")
    internal val kotlinEndRegex = Regex("""\s*}\s*""")

    internal fun String.kotlinArguments(start: Regex, end: Regex) = split("\n").kotlinArguments(start, end)

    internal fun List<String>.kotlinArguments(start: Regex, end: Regex) =
      linesBetween(start, end)
        .filter { it.contains("println(") }
        .map { it.trim() }
        .map { it.replaceFirst("println(", "") }
        .map { it.substring(0, it.indexOfLast { c -> c == ')' }) }
  }
}