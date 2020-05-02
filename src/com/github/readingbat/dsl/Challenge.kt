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
import com.github.readingbat.ReturnType.*
import com.github.readingbat.dsl.JavaChallenge.Companion.convertToScript
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet
import mu.KLogging
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass
import kotlin.reflect.typeOf
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
        val options = MutableDataSet().apply { set(HtmlRenderer.SOFT_BREAK, "<br />\n") }
        val parser = Parser.builder(options).build()
        val renderer = HtmlRenderer.builder(options).build()
        val document = parser.parse(description.trimIndent())
        renderer.render(document)
      }

  var name = ""
  var fileName = ""
  var codingBatEquiv = ""
  var description = ""
  var returnType: ReturnType? = null

  internal abstract fun computeFuncInfo(code: String, returnType: ReturnType?): FunctionInfo

  internal fun funcInfo() =
    sourcesMap
      .computeIfAbsent(challengeId) {
        val path = "${group.languageGroup.rawRepoRoot}$fqName"
        val (content, dur) = measureTimedValue { URL(path).readText() }
        logger.info { """Fetching "${group.name}/$fileName" $path in $dur""" }
        computeFuncInfo(content, returnType)
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

class PythonChallenge(group: ChallengeGroup) : Challenge(group) {
  override fun computeFuncInfo(code: String, returnType: ReturnType?): FunctionInfo {
    val lines = code.split("\n")
    val lineNums =
      lines.mapIndexed { i, str -> i to str }
        .filter { it.second.contains(Regex("^def.*\\(")) }
        .map { it.first }

    val funcCode = lines.subList(lineNums.first(), lineNums.last() - 1).joinToString("\n").trimIndent()
    val args = lines.pythonArguments(pythonStartRegex, pythonEndRegex)

    return FunctionInfo(this, code, funcCode, args, StringType, listOf<Any>())
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

class JavaChallenge(group: ChallengeGroup) : Challenge(group) {
  override fun computeFuncInfo(code: String, returnType: ReturnType?): FunctionInfo {
    val lines = code.split("\n").filter { !it.trimStart().startsWith("package") }
    val lineNums =
      lines.mapIndexed { i, str -> i to str }
        .filter { it.second.contains(staticRegex) }
        .map { it.first }

    val funcCode = lines.subList(lineNums.first(), lineNums.last() - 1).joinToString("\n").trimIndent()
    val args = lines.javaArguments(javaStartRegex, javaEndRegex)
    val script = lines.convertToScript()
    logger.info { "$name script: \n$script" }

    val rawAnswers: Any
    JavaScript()
      .apply {
        import(List::class.java)
        import(ArrayList::class.java)
        val timedValue = measureTimedValue { evalScript(script) }
        rawAnswers = timedValue.value
        logger.info { "Computed answers in ${timedValue.duration} for $name: $rawAnswers" }
      }

    if (rawAnswers !is List<*>)
      throw InvalidConfigurationException("Invalid type returned for $name")

    if (returnType == null)
      throw InvalidConfigurationException("$name missing returnType value")

    return FunctionInfo(this, code, funcCode, args, returnType, rawAnswers)
  }

  companion object : KLogging() {

    internal val staticRegex = Regex("static.*\\(")
    internal val javaStartRegex = Regex("static\\svoid\\smain\\(")
    internal val javaEndRegex = Regex("}")

    internal fun String.javaArguments(start: Regex, end: Regex) = split("\n").javaArguments(start, end)

    internal fun List<String>.javaArguments(start: Regex, end: Regex): List<String> {
      val prefixes =
        listOf("System.out.println", "ArrayUtils.arrayPrint", "ListUtils.listPrint", "arrayPrint", "listPrint")
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

    internal fun List<String>.convertToScript(): String {
      val scriptCode = mutableListOf<String>()
      val psvmRegex = Regex("""public\s*static\s*void\s*main.*\)""")
      val printlnRegex = Regex("""System\.out\.println\(.*\)""")
      val prefixes =
        listOf(Regex("""System\.out\.println\(.*\)"""),
               Regex("""ArrayUtils\.arrayPrint\("""),
               Regex("""ListUtils\.listPrint\("""),
               Regex("""arrayPrint\("""),
               Regex("""listPrint\("""))

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
          insideMain && prefixes.any { line.contains(it) } -> {
            exprIndent = kotlin.math.max(0, prefixes.map { line.indexOf(it.pattern.substring(0, 6)) }.max()!!)
            val firstParen = line.indexOfFirst { it == '(' }
            val lastParen = line.indexOfLast { it == ')' }
            val expr = line.substring(firstParen + 1, lastParen)
            //logger.info { "Content from: $firstParen to: $lastParen is: $expr" }
            val str = "".padStart(exprIndent) + "$varName.add($expr);"
            logger.info { "Transformed:\n$line\nto:\n$str" }
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

internal val String.deRegex get() = this.replace("\\", "")

class KotlinChallenge(group: ChallengeGroup) : Challenge(group) {
  val id = counter.incrementAndGet()

  override fun computeFuncInfo(code: String, returnType: ReturnType?): FunctionInfo {
    val lines = code.split("\n").filter { !it.trimStart().startsWith("package") }
    val funcCode = lines.subList(0, lines.lastLineNumberOf(Regex("fun main\\("))).joinToString("\n").trimIndent()
    val args = lines.kotlinArguments(kotlinStartRegex, kotlinEndRegex)
    val originalCode = lines.joinToString("\n")
    val codeSnippet = "\n$funcCode\n\n"

    return FunctionInfo(this, originalCode, codeSnippet, args, StringType, listOf<Any>())
  }

  companion object {
    internal val kotlinStartRegex = Regex("static\\svoid\\smain\\(")
    internal val kotlinEndRegex = Regex("}")

    internal fun String.kotlinArguments(start: Regex, end: Regex) = split("\n").kotlinArguments(start, end)

    internal fun List<String>.kotlinArguments(start: Regex, end: Regex) =
      linesBetween(start, end)
        .filter { it.contains("println(") }
        .map { it.trim() }
        .map { it.replaceFirst("println(", "") }
        .map { it.substring(0, it.indexOfLast { c -> c == ')' }) }
  }
}

class FunctionInfo(val challenge: Challenge,
                   val originalCode: String,
                   val codeSnippet: String,
                   val arguments: List<String>,
                   val answerType: ReturnType,
                   rawAnswers: List<*>) {

  val answers = mutableListOf<String>()

  init {
    rawAnswers.forEach { raw ->
      answers +=
        when (challenge.returnType!!) {
          BooleanType, IntType -> raw.toString()
          StringType -> raw.toString().toDoubleQuoted()
          BooleanArrayType -> (raw as BooleanArray).map { it }.joinToString().withBrackets
          IntArrayType -> (raw as IntArray).map { it }.joinToString().withBrackets
          StringArrayType -> (raw as Array<String>).map { it.toDoubleQuoted() }.joinToString().withBrackets
          BooleanListType -> (raw as List<Boolean>).toString()
          IntListType -> (raw as List<Int>).toString()
          StringListType -> "[${(raw as List<String>).map { it.toDoubleQuoted() }.joinToString()}]"
        }
    }

    logger.info { "Computed answers: $answers for arguments: $arguments in ${challenge.name}" }

    validate()
  }

  fun validate() {
    if (answers.size != arguments.size)
      throw InvalidConfigurationException("Mismatch between ${answers.size} answers and ${arguments.size} arguments in ${challenge.name}")
  }

  companion object : KLogging()
}

internal val String.withBrackets get() = "[$this]"

fun main() {
  val t = typeOf<List<String>>()
  val c = t.classifier as KClass<*>

  println(t.classifier)
  println(t.arguments[0])
  println()
}

fun main2() {
  val s = """
    public class JoinEnds {

        public static String joinEnds(String str) {
            if (str.length() < 2)
                return str;

            String b = str.substring(0, 1);
            String e = str.substring(str.length() - 1);

            return e + b;
        }

        public static void main(String[] args) {
            System.out.println(joinEnds("Blue zebra"));
            System.out.println(joinEnds("Tree"));
            System.out.println(joinEnds("Re"));
            System.out.println(joinEnds("p"));
            System.out.println(joinEnds(""));
        }
    }
  """.trimIndent()

  println(s.split("\n").convertToScript())
}

