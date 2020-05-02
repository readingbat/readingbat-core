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
import com.github.readingbat.dsl.JavaChallenge.Companion.convertToScript
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet
import mu.KLogging
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KTypeParameter
import kotlin.reflect.full.isSuperclassOf
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
  var returnType = typeOf<Challenge>()

  internal abstract fun computeFuncInfo(code: String): FuncInfo

  internal fun funcInfo() =
    sourcesMap
      .computeIfAbsent(challengeId) {
        val path = "${group.languageGroup.rawRepoRoot}$fqName"
        val (content, dur) = measureTimedValue { URL(path).readText() }
        logger.info { """Fetching "${group.name}/$fileName" $path in $dur""" }
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

    if (returnType == typeOf<Challenge>())
      throw InvalidConfigurationException("returnType property not set for $name")
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
  override fun computeFuncInfo(code: String): FuncInfo {
    val lines = code.split("\n")
    val lineNums =
      lines.mapIndexed { i, str -> i to str }
        .filter { it.second.contains(Regex("^def.*\\(")) }
        .map { it.first }

    val funcCode = lines.subList(lineNums.first(), lineNums.last() - 1).joinToString("\n").trimIndent()
    val args = lines.pythonArguments(pythonStartRegex, pythonEndRegex)

    return FuncInfo(code, funcCode, args, listOf<Any>(), typeOf<List<String>>())
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
  override fun computeFuncInfo(code: String): FuncInfo {
    val lines = code.split("\n").filter { !it.trimStart().startsWith("package") }
    val lineNums =
      lines.mapIndexed { i, str -> i to str }
        .filter { it.second.contains(staticRegex) }
        .map { it.first }

    val funcCode = lines.subList(lineNums.first(), lineNums.last() - 1).joinToString("\n").trimIndent()
    val args = lines.javaArguments(javaStartRegex, javaEndRegex)
    val script = lines.convertToScript()

    val answers: Any
    JavaScript()
      .apply {
        import(List::class.java)
        import(ArrayList::class.java)
        answers = evalScript(script)
      }

    if (answers !is List<*>)
      throw InvalidConfigurationException("Invalid type returned from script")

    return FuncInfo(code, funcCode, args, answers, typeOf<List<String>>())
  }

  companion object {

    internal val staticRegex = Regex("static.*\\(")
    internal val javaStartRegex = Regex("static\\svoid\\smain\\(")
    internal val javaEndRegex = Regex("}")

    internal fun String.javaArguments(start: Regex, end: Regex) = split("\n").javaArguments(start, end)

    internal fun List<String>.javaArguments(start: Regex, end: Regex) =
      linesBetween(start, end)
        .filter { it.contains("System.out.println(") }
        .map { it.trim() }
        .map { it.replaceFirst("System.out.println(", "") }
        .map { it.substring(0, it.indexOfLast { c -> c == ')' }) }

    internal fun List<String>.convertToScript(): String {
      val scriptCode = mutableListOf<String>()
      val psvmRegex = Regex("""public\s*static\s*void\s*main.*\)""")
      val printlnRegex = Regex("""System\.out\.println\(.*\)""")
      var insideMain = false
      var printIndent = 0
      val varName = "answers"
      this.forEach { line ->
        when {
          line.contains(psvmRegex) -> {
            val indent = line.indexOf("public")
            scriptCode += "".padStart(indent) + "public List<Object> $varName = new ArrayList<Object>();"
            scriptCode += ""
            insideMain = true
            scriptCode += line.replace(psvmRegex, "public List<Object> getValue()")
          }
          insideMain && line.contains(printlnRegex) -> {
            printIndent = line.indexOf("System")
            val firstQuote = line.indexOfFirst { it == '(' }
            val lastQuote = line.indexOfLast { it == ')' }
            val printlnExpr = line.substring(firstQuote + 1, lastQuote)
            scriptCode += line.replace(printlnRegex, "$varName.add($printlnExpr)")
          }
          insideMain && line.trim() == "}" -> {
            scriptCode += ""
            scriptCode += "".padStart(printIndent) + "return $varName;"
            scriptCode += line
            insideMain = false
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

class KotlinChallenge(group: ChallengeGroup) : Challenge(group) {
  val id = counter.incrementAndGet()

  override fun computeFuncInfo(code: String): FuncInfo {
    val lines = code.split("\n").filter { !it.trimStart().startsWith("package") }
    val funcCode = lines.subList(0, lines.lastLineNumberOf(Regex("fun main\\("))).joinToString("\n").trimIndent()
    val args = lines.kotlinArguments(kotlinStartRegex, kotlinEndRegex)

    return FuncInfo(lines.joinToString("\n"), "\n$funcCode\n\n", args, listOf<Any>(), typeOf<List<String>>())
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

class TypeDesc(val clazz: KType
              )

class FuncInfo(val originalCode: String,
               val codeSnippet: String,
               val arguments: List<String>,
               rawAnswers: List<*>,
               val answerType: KType) {

  val answers = mutableListOf<String>()

  init {
    val classifier = answerType.classifier
    rawAnswers.forEach {
      if (classifier is KClass<*>) {
        if (List::class.isSuperclassOf(classifier) || Array<Any>::class == classifier) {
          if (classifier.typeParameters.size != 1)
            throw InvalidConfigurationException("Invalid type: $answerType")
          answers +=
            if (classifier.typeParameters[0] == typeOf<String>())
              it.toString().toDoubleQuoted().toDoubleQuoted()
            else
              it.toString().toDoubleQuoted()
        }
      }
      else if (classifier is KTypeParameter) {
        answers +=
          if (classifier == String::class)
            it.toString().toDoubleQuoted().toDoubleQuoted()
          else
            it.toString().toDoubleQuoted()
      }
      else
        throw InvalidConfigurationException("Invalid type: $answerType")
    }
  }
}

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

