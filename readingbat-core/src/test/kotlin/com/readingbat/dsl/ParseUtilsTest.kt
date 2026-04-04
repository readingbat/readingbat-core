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

package com.readingbat.dsl

import com.readingbat.dsl.parse.extractBalancedContent
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ParseUtilsTest : StringSpec() {
  init {
    "simple print with no nesting" {
      "print(42)".extractBalancedContent("print(") shouldBe "42"
    }

    "print with single nested function call" {
      "print(foo(a, b))".extractBalancedContent("print(") shouldBe "foo(a, b)"
    }

    "print with deeply nested function calls" {
      "print(foo(bar(a, b), c))".extractBalancedContent("print(") shouldBe "foo(bar(a, b), c)"
    }

    "println prefix" {
      "println(foo(a, b))".extractBalancedContent("println(") shouldBe "foo(a, b)"
    }

    "System.out.println prefix" {
      "System.out.println(foo(a, b))".extractBalancedContent("System.out.println(") shouldBe "foo(a, b)"
    }

    "print with leading whitespace" {
      "  print(foo(a))".extractBalancedContent("print(") shouldBe "foo(a)"
    }

    "print with string argument" {
      """print("hello")""".extractBalancedContent("print(") shouldBe """"hello""""
    }

    "print with multiple nested levels" {
      "print(a(b(c(d))))".extractBalancedContent("print(") shouldBe "a(b(c(d)))"
    }

    "missing prefix throws" {
      shouldThrow<IllegalStateException> {
        "foo(42)".extractBalancedContent("print(")
      }
    }

    "unbalanced parentheses throws" {
      shouldThrow<IllegalStateException> {
        "print(foo(a, b)".extractBalancedContent("print(")
      }
    }
  }
}
