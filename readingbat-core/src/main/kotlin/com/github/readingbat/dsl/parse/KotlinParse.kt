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
import com.github.pambrose.common.util.substringBetween
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

  private fun stripLeadingComments(lines: List<String>): List<String> {
    var i = 0
    while (i < lines.size) {
      val trimmed = lines[i].trim()
      when {
        trimmed.startsWith("/*") -> {
          // Collect the entire block comment to check for "Copyright"
          val blockStart = i
          val isSingleLine = trimmed.contains("*/")
          if (isSingleLine) {
            if (trimmed.contains("Copyright", ignoreCase = true)) {
              i++
            } else {
              break
            }
          } else {
            // Find the end of the block comment
            i++
            while (i < lines.size && !lines[i].contains("*/")) i++
            if (i < lines.size) i++ // skip the closing */ line
            // Check if any line in the block contains "Copyright"
            val blockLines = lines.subList(blockStart, i)
            if (blockLines.any { it.contains("Copyright", ignoreCase = true) }) {
              // Copyright block removed, continue scanning
            } else {
              // Not a copyright comment — keep it
              return lines.subList(blockStart, lines.size)
            }
          }
        }

        trimmed.startsWith("//") && trimmed.contains("Copyright", ignoreCase = true) -> {
          i++
        }

        trimmed.isEmpty() -> {
          i++
        }

        else -> {
          break
        }
      }
    }
    return lines.subList(i, lines.size)
  }

  fun extractKotlinInvocations(code: String, start: Regex, end: Regex) =
    extractKotlinInvocations(code.lines(), start, end)

  fun extractKotlinInvocations(code: List<String>, start: Regex, end: Regex) =
    code.linesBetween(start, end)
      .filter { it.trimStart().startsWith(PRINT_PREFIX) }
      .map { it.substringBetween(PRINT_PREFIX, ")") }
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
            val expr = line.substringBetween(PRINT_PREFIX, ")")
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
