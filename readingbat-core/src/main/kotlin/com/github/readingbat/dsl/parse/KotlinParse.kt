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

import com.github.pambrose.common.util.lastLineNumberOf
import com.github.pambrose.common.util.linesBetween
import com.github.readingbat.server.Invocation

internal object KotlinParse {
  internal val funMainRegex = Regex("""^\s*fun\s+main.*\)""")
  internal val kotlinEndRegex = Regex("""\s*}\s*""")
  private const val PRINT_PREFIX = "println("
  internal const val VAR_NAME = "answers"

  fun extractKotlinFunction(code: List<String>): String {
    val endLine = code.lastLineNumberOf(funMainRegex)
    val snippet = code.subList(0, endLine)
    return stripLeadingComments(snippet).joinToString("\n").trimIndent()
  }

  internal fun stripLeadingComments(lines: List<String>): List<String> {
    var i = 0
    while (i < lines.size) {
      val trimmed = lines[i].trim()
      i =
        when {
          trimmed.isEmpty() -> i + 1
          trimmed.startsWith("//") && trimmed.contains("Copyright", ignoreCase = true) -> i + 1
          trimmed.startsWith("/*") -> skipBlockComment(lines, i) ?: return lines.subList(i, lines.size)
          else -> return lines.subList(i, lines.size)
        }
    }
    return lines.subList(i, lines.size)
  }

  /**
   * Scans past a block comment starting at [start]. Returns the index after the block comment
   * if it was a copyright block (should be stripped), or null if the block is not a copyright
   * comment (should be kept).
   */
  private fun skipBlockComment(lines: List<String>, start: Int): Int? {
    val trimmed = lines[start].trim()
    val isSingleLine = trimmed.contains("*/")

    if (isSingleLine) {
      return if (trimmed.contains("Copyright", ignoreCase = true)) start + 1 else null
    }

    // Multi-line block comment: find the closing */
    var end = start + 1
    while (end < lines.size && !lines[end].contains("*/")) end++
    if (end < lines.size) end++ // skip the closing */ line

    val blockLines = lines.subList(start, end)
    return if (blockLines.any { it.contains("Copyright", ignoreCase = true) }) end else null
  }

  fun extractKotlinInvocations(code: String, start: Regex, end: Regex) =
    extractKotlinInvocations(code.lines(), start, end)

  fun extractKotlinInvocations(code: List<String>, start: Regex, end: Regex) =
    code.linesBetween(start, end)
      .filter { it.trimStart().startsWith(PRINT_PREFIX) }
      .map { it.trimStart().extractBalancedContent(PRINT_PREFIX) }
      .map { Invocation(it) }

  fun convertToKotlinScript(code: List<String>) =
    buildString {
      var insideMain = false

      code.forEach { line ->
        when {
          line.contains(funMainRegex) -> {
            insideMain = true
          }

          insideMain && line.trimStart().startsWith(PRINT_PREFIX) -> {
            val expr = line.trimStart().extractBalancedContent(PRINT_PREFIX)
            appendLine("$VAR_NAME.add($expr)")
          }

          insideMain && line.trimStart().startsWith("}") -> {
            insideMain = false
          }

          else -> {
            appendLine(line)
          }
        }
      }
      appendLine("")
    }
}
