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
import com.readingbat.server.Invocation
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Parser for Python challenge source code.
 *
 * Extracts function bodies and invocations from Python source files that follow a convention
 * where a `def main():` function contains `print(...)` calls that serve as test invocations
 * for the challenge function defined above it.
 */
internal object PythonParse {
  private val logger = KotlinLogging.logger {}

  /** Matches a `def main(...)` declaration line. */
  internal val defMainRegex = Regex("""def\s+main\(""")

  /** Matches the `if __name__ == "__main__"` guard, used as the end boundary for invocations. */
  internal val ifMainEndRegex = Regex("__main__")
  private val defRegex = Regex("^def.*\\(")
  private const val PRINT_PREFIX = "print("

  /** Variable name used in the generated evaluation script to collect results. */
  internal const val VAR_NAME = "answers"

  /**
   * Extracts the challenge function body from Python source code.
   *
   * Returns lines between the first `def` and the last `def` (which is `def main`), giving
   * the user-visible function displayed on the challenge page.
   */
  fun extractPythonFunction(code: List<String>): String {
    val lineNums = code.indices.filter { code[it].contains(defRegex) }
    return code.subList(lineNums.first(), lineNums.last() - 1).joinToString("\n").trimIndent()
  }

  /**
   * Converts Python challenge source code into an evaluable script.
   *
   * Strips the `def main():` wrapper and replaces each `print(...)` call with
   * `answers.add(...)`, producing a script whose collected results can be used for
   * answer verification.
   */
  fun convertToPythonScript(code: List<String>) =
    buildString {
      var insideMain = false
      code.forEach { line ->
        when {
          line.contains(defMainRegex) -> {
            insideMain = true
          }

          insideMain -> {
            // Skip everything after def main(): that does not have a print
            if (line.contains(PRINT_PREFIX)) {
              val expr = line.trimStart().extractBalancedContent(PRINT_PREFIX)
              val str = "$VAR_NAME.add($expr)"
              logger.debug { "Transformed: ${line.trim()} to: $str" }
              appendLine(str)
            }
          }

          else -> {
            appendLine(line)
          }
        }
      }
      appendLine("")
    }

  /** Convenience overload that accepts source code as a single [String]. */
  fun extractPythonInvocations(code: String, start: Regex, end: Regex) =
    extractPythonInvocations(code.lines(), start, end)

  /**
   * Extracts challenge invocations from the `main` function of Python source code.
   *
   * Finds `print(...)` calls between the [start] and [end] regex boundaries and extracts
   * the inner expression as an [Invocation].
   */
  fun extractPythonInvocations(code: List<String>, start: Regex, end: Regex) =
    code.linesBetween(start, end)
      .filter { it.contains(PRINT_PREFIX) }
      .map { it.trimStart().extractBalancedContent(PRINT_PREFIX) }
      .map { Invocation(it) }
}
