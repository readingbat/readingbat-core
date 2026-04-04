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

package com.readingbat.dsl.parse

import com.pambrose.common.util.linesBetween
import com.readingbat.dsl.ReturnType.Companion.asReturnType
import com.readingbat.server.ChallengeName
import com.readingbat.server.Invocation
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.math.max

/**
 * Parser for Java challenge source code.
 *
 * Extracts function bodies, return types, and invocations from Java source files that follow
 * a convention where a `public static void main` method contains `println`-style calls that
 * serve as test invocations for the challenge function defined above it.
 */
internal object JavaParse {
  private val logger = KotlinLogging.logger {}
  private val spaceRegex = Regex("""\s+""")
  private val staticRegex = Regex("""static.+\(""")
  private val staticStartRegex = Regex("""\sstatic.+\(""")
  private val psRegex = Regex("""^\s+public\s+static.+\(""")

  /** Matches a `public static void main(...)` declaration line. */
  internal val psvmRegex = Regex("""^\s*public\s+static\s+void\s+main.+\)""")

  /** Matches a closing brace line, used to detect the end of Java method bodies. */
  internal val javaEndRegex = Regex("""\s*}\s*""")

  /** Matches `static void main(` to identify the main method entry point. */
  internal val svmRegex = Regex("""\s*static\s+void\s+main\(""")

  private val prefixRegex =
    listOf(
      Regex("""System\.out\.println\("""),
      Regex("""ArrayUtils\.arrayPrint\("""),
      Regex("""ListUtils\.listPrint\("""),
      Regex("""arrayPrint\("""),
      Regex("""listPrint\("""),
    )
  private val prefixes =
    listOf("System.out.println", "ArrayUtils.arrayPrint", "ListUtils.listPrint", "arrayPrint", "listPrint")

  /**
   * Derives the return type of a Java challenge function by locating the first `static` method
   * declaration (excluding `main`) and extracting the type keyword that follows `static`.
   *
   * @throws IllegalStateException if the return type is unrecognized or no static method is found.
   */
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

  /**
   * Extracts the challenge function body from Java source code.
   *
   * Returns the lines between the first and last `static` method declarations (exclusive of the
   * `main` method), which represents the user-visible function displayed on the challenge page.
   */
  fun extractJavaFunction(code: List<String>): String {
    val lineNums = code.indices.filter { code[it].contains(staticRegex) }
    return code.subList(lineNums.first(), lineNums.last() - 1).joinToString("\n").trimIndent()
  }

  /** Convenience overload that accepts source code as a single [String]. */
  fun extractJavaInvocations(code: String, start: Regex, end: Regex) =
    extractJavaInvocations(code.lines(), start, end)

  /**
   * Extracts challenge invocations from the `main` method of Java source code.
   *
   * Looks for lines between [start] and [end] regex boundaries that call one of the recognized
   * print prefixes (e.g., `System.out.println`, `arrayPrint`, `listPrint`), and extracts the
   * inner expression as an [Invocation].
   */
  fun extractJavaInvocations(code: List<String>, start: Regex, end: Regex): List<Invocation> =
    prefixes.flatMap { prefix ->
      code.linesBetween(start, end)
        .map { it.trimStart() }
        .filter { it.startsWith("$prefix(") }
        .map { Invocation(it.extractBalancedContent("$prefix(")) }
    }

  /**
   * Converts Java challenge source code into an evaluable script.
   *
   * Transforms the `public static void main` method into a `getValue()` method that collects
   * results into an `ArrayList`. Each `println`/`arrayPrint`/`listPrint` call is replaced with
   * an `answers.add(...)` call, and the method returns the accumulated list of results for
   * answer verification.
   */
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
            val matchedPrefix = prefixes.first { line.contains("$it(") }
            val expr = line.trimStart().extractBalancedContent("$matchedPrefix(")
            exprIndent = max(0, prefixRegex.maxOfOrNull { line.indexOf(it.pattern.substring(0, 6)) } ?: 0)
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

          else -> {
            appendLine(line)
          }
        }
      }
      appendLine("")
    }
}
