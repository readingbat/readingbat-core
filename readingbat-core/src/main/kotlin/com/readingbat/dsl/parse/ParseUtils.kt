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

/**
 * Extracts the content inside the outermost parentheses following the given [prefix].
 *
 * The [prefix] must end with an opening parenthesis `(`. This function handles nested
 * parentheses by tracking depth, returning only the content between the matching open
 * and close parens. For example, given `"println(foo(1, 2))"` with prefix `"println("`,
 * this returns `"foo(1, 2)"`.
 *
 * @throws IllegalStateException if the prefix is not found or parentheses are unbalanced.
 */
internal fun String.extractBalancedContent(prefix: String): String {
  val openIndex = indexOf(prefix)
  if (openIndex < 0) error("Prefix '$prefix' not found in: $this")
  val parenStart = openIndex + prefix.length - 1
  if (parenStart >= length || this[parenStart] != '(') error("Expected '(' at end of prefix '$prefix' in: $this")
  var depth = 0
  var i = parenStart
  while (i < length) {
    when (this[i]) {
      '"' -> {
        i++
        while (i < length && this[i] != '"') {
          if (this[i] == '\\') i++ // skip escaped character
          i++
        }
      }

      '(' -> {
        depth++
      }

      ')' -> {
        depth--
        if (depth == 0) return substring(parenStart + 1, i)
      }
    }
    i++
  }
  error("Unbalanced parentheses in: $this")
}
