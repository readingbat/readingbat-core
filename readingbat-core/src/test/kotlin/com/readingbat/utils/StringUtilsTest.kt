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

package com.readingbat.utils

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class StringUtilsTest : StringSpec() {
  init {
    "toCapitalized capitalizes a lowercase first character" {
      "hello".toCapitalized() shouldBe "Hello"
    }

    "toCapitalized leaves an already-capitalized first character unchanged" {
      "Hello".toCapitalized() shouldBe "Hello"
    }

    "toCapitalized only affects the first character" {
      "hELLO wORLD".toCapitalized() shouldBe "HELLO wORLD"
    }

    "toCapitalized returns empty string when input is empty" {
      "".toCapitalized() shouldBe ""
    }

    "toCapitalized handles single lowercase character" {
      "a".toCapitalized() shouldBe "A"
    }

    "toCapitalized handles single uppercase character" {
      "A".toCapitalized() shouldBe "A"
    }

    "toCapitalized leaves non-letter first character unchanged" {
      "1abc".toCapitalized() shouldBe "1abc"
      "!hello".toCapitalized() shouldBe "!hello"
      " hello".toCapitalized() shouldBe " hello"
    }

    "toCapitalized title-cases title-case-aware characters" {
      // 'ǆ' (U+01C6) title-cases to 'ǅ' (U+01C5), distinct from uppercase 'Ǆ' (U+01C4).
      "ǆx".toCapitalized() shouldBe "ǅx"
    }
  }
}
