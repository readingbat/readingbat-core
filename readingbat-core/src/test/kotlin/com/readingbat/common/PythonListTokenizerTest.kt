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

package com.readingbat.common

import com.readingbat.common.FunctionInfo.Companion.pythonListEquals
import com.readingbat.common.FunctionInfo.Companion.splitTopLevelCommas
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Tests the quote-aware list tokenizer that replaced the naive `split(",")` in the Python/JVM
 * list answer paths. A comma inside a quoted string element (e.g. `'a,b'`) must not split the
 * element, and single- and double-quoted elements must compare as equal (Python list semantics).
 */
class PythonListTokenizerTest : StringSpec() {
  init {
    "splitTopLevelCommas keeps commas inside single-quoted elements together" {
      splitTopLevelCommas("'a,b', 'c'").map { it.trim() } shouldBe listOf("'a,b'", "'c'")
    }

    "splitTopLevelCommas keeps commas inside double-quoted elements together" {
      splitTopLevelCommas(""""a,b", "c"""").map { it.trim() } shouldBe listOf("\"a,b\"", "\"c\"")
    }

    "splitTopLevelCommas splits unquoted top-level elements" {
      splitTopLevelCommas("1, 2, 3").map { it.trim() } shouldBe listOf("1", "2", "3")
    }

    "splitTopLevelCommas returns an empty list for blank input" {
      splitTopLevelCommas("") shouldBe emptyList()
      splitTopLevelCommas("   ") shouldBe emptyList()
    }

    "pythonListEquals matches a single-element string containing a comma across quote styles" {
      // The element 'a,b' is one element, not two; single and double quotes are interchangeable.
      pythonListEquals("['a,b']", "[\"a,b\"]") shouldBe true
    }

    "pythonListEquals does not equate one comma-containing element with two elements" {
      pythonListEquals("['a,b']", "['a', 'b']") shouldBe false
    }
  }
}
