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

import com.readingbat.dsl.parse.JavaParse.extractJavaFunction
import com.readingbat.server.ChallengeName
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * Tests that [extractJavaFunction] guards malformed challenge source with a meaningful,
 * challenge-named error instead of an opaque NoSuchElementException / IllegalArgumentException
 * from `lineNums.first()` or `subList(n, n - 1)` when fewer than two `static` declarations exist.
 */
class JavaFunctionExtractTest : StringSpec() {
  init {
    val challengeName = ChallengeName("FooChallenge")

    "extractJavaFunction returns the function body between the two static declarations" {
      val code =
        """
          public class Foo {
              public static int bar(int x) {
                  return x + 1;
              }

              public static void main(String[] args) {
                  System.out.println(bar(1));
              }
          }
        """.trimIndent().lines()

      val func = extractJavaFunction(challengeName, code)
      func shouldContain "public static int bar(int x)"
      func shouldContain "return x + 1;"
      // The main method is excluded from the displayed function body.
      func.contains("static void main") shouldBe false
    }

    "extractJavaFunction throws a named error when no static declaration is present" {
      val code =
        """
          public class Foo {
              int bar(int x) {
                  return x;
              }
          }
        """.trimIndent().lines()

      val ex = shouldThrow<IllegalStateException> { extractJavaFunction(challengeName, code) }
      ex.message shouldContain "FooChallenge"
      ex.message shouldContain "static"
    }

    "extractJavaFunction throws a named error when only one static declaration is present" {
      val code =
        """
          public class Foo {
              public static void main(String[] args) {
                  System.out.println("hi");
              }
          }
        """.trimIndent().lines()

      val ex = shouldThrow<IllegalStateException> { extractJavaFunction(challengeName, code) }
      ex.message shouldContain "FooChallenge"
    }
  }
}
