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

import com.readingbat.dsl.parse.JavaParse.extractJavaInvocations
import com.readingbat.dsl.parse.JavaParse.javaEndRegex
import com.readingbat.dsl.parse.JavaParse.psvmRegex
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Tests that [extractJavaInvocations] preserves source-line order when a challenge's main mixes
 * different print prefixes. The previous implementation grouped invocations by prefix, so e.g. all
 * println(...) calls came before all arrayPrint(...) calls regardless of their position in source —
 * misaligning the displayed invocations with the computed answers.
 */
class JavaInvocationOrderTest : StringSpec() {
  init {
    "extractJavaInvocations preserves source-line order across mixed print prefixes" {
      val code =
        """
          public class Test {
              public static void main(String[] args) {
                  System.out.println(a(1));
                  arrayPrint(b(2));
                  System.out.println(c(3));
              }
          }
        """.trimIndent()

      extractJavaInvocations(code, psvmRegex, javaEndRegex).map { it.toString() } shouldBe
        listOf("a(1)", "b(2)", "c(3)")
    }
  }
}
