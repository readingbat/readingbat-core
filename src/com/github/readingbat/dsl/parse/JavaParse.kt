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

package com.github.readingbat.dsl.parse

import com.github.pambrose.common.util.linesBetween
import com.github.pambrose.common.util.substringBetween
import com.github.readingbat.dsl.InvalidConfigurationException
import com.github.readingbat.dsl.ReturnType.Companion.asReturnType
import mu.KLogging
import kotlin.math.max

object JavaParse : KLogging() {

  private val spaceRegex = Regex("""\s+""")
  private val staticRegex = Regex("""static.+\(""")
  private val staticStartRegex = Regex("""\sstatic.+\(""")
  private val psRegex = Regex("""^\s+public\s+static.+\(""")
  internal val javaEndRegex = Regex("""\s*}\s*""")
  internal val svmRegex = Regex("""\s*static\s+void\s+main\(""")
  internal val psvmRegex = Regex("""^\s*public\s+static\s+void\s+main.+\)""")

  private val prefixRegex =
    listOf(Regex("""System\.out\.println\("""),
           Regex("""ArrayUtils\.arrayPrint\("""),
           Regex("""ListUtils\.listPrint\("""),
           Regex("""arrayPrint\("""),
           Regex("""listPrint\("""))
  private val prefixes =
    listOf("System.out.println", "ArrayUtils.arrayPrint", "ListUtils.listPrint", "arrayPrint", "listPrint")

  internal fun deriveJavaReturnType(name: String, code: List<String>) =
    code.asSequence()
      .filter {
        !it.contains(svmRegex) && (it.contains(
          staticStartRegex) || it.contains(psRegex))
      }
      .map { str ->
        val words = str.trim().split(spaceRegex).filter { it.isNotBlank() }
        val staticPos = words.indices.first { words[it] == "static" }
        val typeStr = words[staticPos + 1]
        typeStr.asReturnType ?: throw InvalidConfigurationException("In $name invalid type $typeStr")
      }
      .firstOrNull() ?: throw InvalidConfigurationException("In $name unable to determine return type")

  internal fun extractJavaFunction(code: List<String>): String {
    val lineNums =
      code.mapIndexed { i, str -> i to str }.filter { it.second.contains(staticRegex) }.map { it.first }
    return code.subList(lineNums.first(), lineNums.last() - 1).joinToString("\n").trimIndent()
  }

  internal fun extractJavaArguments(code: String, start: Regex, end: Regex) =
    extractJavaArguments(code.lines(), start, end)

  internal fun extractJavaArguments(code: List<String>, start: Regex, end: Regex): List<String> {
    val lines = mutableListOf<String>()
    prefixes
      .forEach { prefix ->
        lines.addAll(
          code.linesBetween(start, end)
            .map { it.trimStart() }
            .filter { it.startsWith("$prefix(") }
            .map { it.substringBetween("$prefix(", ")") })
      }
    return lines
  }

  internal fun convertToScript(code: List<String>): String {
    val scriptCode = mutableListOf<String>()
    val varName = "answers"
    var exprIndent = 0
    var insideMain = false

    code.forEach { line ->
      when {
        line.contains(psvmRegex) -> {
          insideMain = true
          val publicIndent = line.indexOf("public ")
          val indent = "".padStart(publicIndent)
          scriptCode += indent + "public List<Object> $varName = new ArrayList<Object>();"
          scriptCode += ""
          scriptCode += indent + line.replace(psvmRegex, "public List<Object> getValue()")
        }
        insideMain && prefixRegex.any { line.contains(it) } -> {
          val expr = line.substringBetween("(", ")")
          exprIndent = max(0, prefixRegex.map { line.indexOf(it.pattern.substring(0, 6)) }.max()!!)
          val str = "".padStart(exprIndent) + "$varName.add($expr);"
          logger.debug { "Transformed:\n$line\nto:\n$str" }
          scriptCode += str
        }
        insideMain && line.trimStart().startsWith("}") -> {
          insideMain = false
          scriptCode += ""
          scriptCode += "".padStart(exprIndent) + "return $varName;"
          scriptCode += line
        }
        else -> scriptCode += line
      }
    }
    scriptCode += ""
    return scriptCode.joinToString("\n")
  }
}