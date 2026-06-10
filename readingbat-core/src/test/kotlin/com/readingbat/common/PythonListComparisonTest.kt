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
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Tests for [FunctionInfo.pythonListEquals], the eval-free replacement for the former
 * `pythonEvaluatorPool.eval` comparison. The previous implementation interpolated raw user
 * input into a Python expression and executed it, which was a remote-code-execution surface.
 * These tests pin down the behavior the pure comparison must preserve (Python's lenient list
 * equality) and verify that malicious payloads are treated as inert, non-matching data.
 */
class PythonListComparisonTest : StringSpec() {
  init {
    "identical int lists are equal" {
      pythonListEquals("[1, 2, 3]", "[1, 2, 3]") shouldBe true
    }

    "differing whitespace does not matter" {
      pythonListEquals("[1,2,3]", "[1, 2, 3]") shouldBe true
    }

    "single and double quoted strings are equal" {
      pythonListEquals("['a', 'b']", """["a", "b"]""") shouldBe true
    }

    "int and float forms of the same number are equal" {
      pythonListEquals("[1.0, 2.0]", "[1, 2]") shouldBe true
    }

    "boolean lists are equal" {
      pythonListEquals("[True, False]", "[True, False]") shouldBe true
    }

    "empty lists are equal" {
      pythonListEquals("[]", "[]") shouldBe true
    }

    "surrounding whitespace around the whole list is ignored" {
      pythonListEquals("  [1, 2, 3]  ", "[1, 2, 3]") shouldBe true
    }

    "lists of different length are not equal" {
      pythonListEquals("[1, 2]", "[1, 2, 3]") shouldBe false
    }

    "lists with a differing element are not equal" {
      pythonListEquals("[1, 2, 4]", "[1, 2, 3]") shouldBe false
    }

    "different string values are not equal" {
      pythonListEquals("""["a", "b"]""", """["a", "c"]""") shouldBe false
    }

    "empty list does not equal non-empty list" {
      pythonListEquals("[]", "[1]") shouldBe false
    }

    // Security: a code-injection payload must be treated as inert data, never evaluated,
    // and must not match a legitimate answer.
    "code injection payload is inert and returns false" {
      pythonListEquals("__import__('os').system('echo pwned')", "[1, 2]") shouldBe false
    }

    "bracketed code injection payload is inert and returns false" {
      pythonListEquals("[__import__('subprocess').call(['ls'])]", "[1]") shouldBe false
    }
  }
}
