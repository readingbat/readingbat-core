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

import com.github.readingbat.dsl.parse.JavaParse.extractJavaFunction
import com.github.readingbat.dsl.parse.PythonParse.extractPythonFunction
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class IndicesFilterTest : StringSpec() {
  init {
    "extractJavaFunction should find static method body" {
      val code =
        listOf(
          "import java.util.*;",
          "",
          "public class Warmup {",
          "  public static boolean sleepIn(boolean weekday, boolean vacation) {",
          "    return !weekday || vacation;",
          "  }",
          "",
          "  public static void main(String[] args) {",
          "    System.out.println(sleepIn(false, false));",
          "  }",
          "}",
        )
      val result = extractJavaFunction(code)
      result shouldContain "sleepIn"
      result shouldContain "return !weekday || vacation"
      result shouldNotContain "main"
    }

    "extractPythonFunction should find def function body" {
      val code =
        listOf(
          "def sleep_in(weekday, vacation):",
          "  return not weekday or vacation",
          "",
          "def main():",
          "  print(sleep_in(False, False))",
        )
      val result = extractPythonFunction(code)
      result shouldContain "sleep_in"
      result shouldContain "return not weekday or vacation"
      result shouldNotContain "main"
    }

    "indices.filter produces same results as mapIndexed for index extraction" {
      val items = listOf("apple", "banana", "avocado", "cherry", "apricot")
      val pattern = Regex("^a")

      val viaMapIndexed =
        items.mapIndexed { i, str -> i to str }
          .filter { it.second.contains(pattern) }
          .map { it.first }

      val viaIndicesFilter = items.indices.filter { items[it].contains(pattern) }

      viaIndicesFilter shouldBe viaMapIndexed
      viaIndicesFilter shouldBe listOf(0, 2, 4)
    }
  }
}
