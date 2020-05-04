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

import com.github.pambrose.common.util.lastLineNumberOf
import com.github.pambrose.common.util.linesBetween
import com.github.pambrose.common.util.substringBetween

object KotlinParse {
  internal val funMainRegex = Regex("""^\s*fun\s+main.*\)""")
  internal val kotlinEndRegex = Regex("""\s*}\s*""")
  private const val printlnPrefix = "println("
  internal val varName = "answers"

  internal fun extractKotlinFunction(code: List<String>) =
    code.subList(0, code.lastLineNumberOf(funMainRegex)).joinToString("\n").trimIndent()

  internal fun extractKotlinArguments(code: String, start: Regex, end: Regex) =
    extractKotlinArguments(code.lines(), start, end)

  internal fun extractKotlinArguments(code: List<String>, start: Regex, end: Regex) =
    code.linesBetween(start, end)
      .filter { it.trimStart().startsWith(printlnPrefix) }
      .map { it.substringBetween(printlnPrefix, ")") }

  internal fun convertToKotlinScript(code: List<String>): String {
    val scriptCode = mutableListOf<String>()
    var insideMain = false

    code.forEach { line ->
      when {
        line.contains(funMainRegex) -> insideMain = true
        insideMain && line.trimStart().startsWith(printlnPrefix) -> {
          val expr = line.substringBetween(printlnPrefix, ")")
          scriptCode += "$varName.add($expr)"
        }
        insideMain && line.trimStart().startsWith("}") -> insideMain = false
        else -> scriptCode += line
      }
    }
    scriptCode += ""
    return scriptCode.joinToString("\n")
  }
}