/*
 * Copyright © 2023 Paul Ambrose (pambrose@mac.com)
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
import com.github.readingbat.server.Invocation
import io.github.oshai.kotlinlogging.KotlinLogging

internal object PythonParse {
  private val logger = KotlinLogging.logger {}
  internal val defMainRegex = Regex("""def\s+main\(""")
  internal val ifMainEndRegex = Regex("__main__")
  private val defRegex = Regex("^def.*\\(")
  private const val PRINT_PREFIX = "print("
  internal const val VAR_NAME = "answers"

  fun extractPythonFunction(code: List<String>): String {
    val lineNums =
      code.mapIndexed { i, str -> i to str }
        .filter { it.second.contains(defRegex) }
        .map { it.first }
    return code.subList(lineNums.first(), lineNums.last() - 1).joinToString("\n").trimIndent()
  }

  fun convertToPythonScript(code: List<String>) =
    buildString {
      var insideMain = false
      code.forEach { line ->
        when {
          line.contains(defMainRegex) -> insideMain = true
          insideMain -> {
            // Skip everything after def main(): that does not have a print
            if (line.contains(PRINT_PREFIX)) {
              val expr = line.substringBetween(PRINT_PREFIX, ")")
              val str = "$VAR_NAME.add($expr)"
              logger.debug { "Transformed: ${line.trim()} to: $str" }
              appendLine(str)
            }
          }

          else -> appendLine(line)
        }
      }
      appendLine("")
    }

  fun extractPythonInvocations(code: String, start: Regex, end: Regex) =
    extractPythonInvocations(code.lines(), start, end)

  fun extractPythonInvocations(code: List<String>, start: Regex, end: Regex) =
    code.linesBetween(start, end)
      .filter { it.contains(PRINT_PREFIX) }
      .map { it.substringBetween(PRINT_PREFIX, ")") }
      .map { Invocation(it) }
}
