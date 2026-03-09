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

internal fun String.extractBalancedContent(prefix: String): String {
  val openIndex = indexOf(prefix)
  if (openIndex < 0) error("Prefix '$prefix' not found in: $this")
  val parenStart = openIndex + prefix.length - 1
  if (parenStart >= length || this[parenStart] != '(') error("Expected '(' at end of prefix '$prefix' in: $this")
  var depth = 0
  for (i in parenStart until length) {
    when (this[i]) {
      '(' -> {
        depth++
      }

      ')' -> {
        depth--
        if (depth == 0) return substring(parenStart + 1, i)
      }
    }
  }
  error("Unbalanced parentheses in: $this")
}
