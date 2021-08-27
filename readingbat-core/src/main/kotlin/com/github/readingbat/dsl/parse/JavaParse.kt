/*
 * Copyright © 2021 Paul Ambrose (pambrose@mac.com)
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
import com.github.readingbat.dsl.ReturnType.Companion.asReturnType
import com.github.readingbat.server.ChallengeName
import com.github.readingbat.server.Invocation
import mu.KLogging
import kotlin.math.max

internal object JavaParse : KLogging() {

  private val spaceRegex = Regex("""\s+""")
  private val staticRegex = Regex("""static.+\(""")
  private val staticStartRegex = Regex("""\sstatic.+\(""")
  private val psRegex = Regex("""^\s+public\s+static.+\(""")
  internal val psvmRegex = Regex("""^\s*public\s+static\s+void\s+main.+\)""")
  internal val javaEndRegex = Regex("""\s*}\s*""")
  internal val svmRegex = Regex("""\s*static\s+void\s+main\(""")

  private val prefixRegex =
    listOf(Regex("""System\.out\.println\("""),
           Regex("""ArrayUtils\.arrayPrint\("""),
           Regex("""ListUtils\.listPrint\("""),
           Regex("""arrayPrint\("""),
           Regex("""listPrint\("""))
  private val prefixes =
    listOf("System.out.println", "ArrayUtils.arrayPrint", "ListUtils.listPrint", "arrayPrint", "listPrint")

  fun deriveJavaReturnType(challengeName: ChallengeName, code: List<String>) =
    code.asSequence()
      .filter { !it.contains(svmRegex) && (it.contains(staticStartRegex) || it.contains(psRegex)) }
      .map { str ->
        val words = str.trim().split(spaceRegex).filter { it.isNotBlank() }
        val staticPos = words.indices.first { words[it] == "static" }
        val typeStr = words[staticPos + 1]
        typeStr.asReturnType ?: error("In $challengeName invalid type $typeStr")
      }
      .firstOrNull() ?: error("In $challengeName unable to determine return type")

  fun extractJavaFunction(code: List<String>): String {
    val lineNums =
      code.mapIndexed { i, str -> i to str }.filter { it.second.contains(staticRegex) }.map { it.first }
    return code.subList(lineNums.first(), lineNums.last() - 1).joinToString("\n").trimIndent()
  }

  fun extractJavaInvocations(code: String, start: Regex, end: Regex) =
    extractJavaInvocations(code.lines(), start, end)

  fun extractJavaInvocations(code: List<String>, start: Regex, end: Regex): List<Invocation> {
    val lines = mutableListOf<Invocation>()
    prefixes
      .forEach { prefix ->
        lines.addAll(
          code.linesBetween(start, end)
            .map { it.trimStart() }
            .filter { it.startsWith("$prefix(") }
            .map { it.substringBetween("$prefix(", ")") }
            .map { Invocation((it)) })
      }
    return lines
  }

  fun convertToScript(code: List<String>) =
    buildString {
      val varName = "answers"
      var exprIndent = 0
      var insideMain = false

      code.forEach { line ->
        when {
          line.contains(psvmRegex) -> {
            insideMain = true
            val publicIndent = line.indexOf("public ")
            val indent = "".padStart(publicIndent)
            appendLine(indent + "public List<Object> $varName = new ArrayList<Object>();")
            appendLine("")
            appendLine(indent + line.replace(psvmRegex, "public List<Object> getValue()"))
          }
          insideMain && prefixRegex.any { line.contains(it) } -> {
            val expr = line.substringBetween("(", ")")
            exprIndent = max(0, prefixRegex.map { line.indexOf(it.pattern.substring(0, 6)) }.maxOrNull() ?: 0)
            val str = "".padStart(exprIndent) + "$varName.add($expr);"
            logger.debug { "Transformed:\n$line\nto:\n$str" }
            appendLine(str)
          }
          insideMain && line.trimStart().startsWith("}") -> {
            insideMain = false
            appendLine("")
            appendLine("".padStart(exprIndent) + "return $varName;")
            appendLine(line)
          }
          else -> appendLine(line)
        }
      }
      appendLine("")
    }
}