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

package com.github.readingbat

import com.github.readingbat.dsl.JavaChallenge
import com.github.readingbat.dsl.JavaChallenge.Companion.javaEndRegex
import com.github.readingbat.dsl.JavaChallenge.Companion.psvmRegex
import com.github.readingbat.dsl.KotlinChallenge
import com.github.readingbat.dsl.KotlinChallenge.Companion.kotlinEndRegex
import com.github.readingbat.dsl.KotlinChallenge.Companion.kotlinStartRegex
import com.github.readingbat.dsl.PythonChallenge
import com.github.readingbat.dsl.PythonChallenge.Companion.defMainRegex
import com.github.readingbat.dsl.PythonChallenge.Companion.ifMainEndRegex
import com.github.readingbat.dsl.addImports
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test

class UtilsTest {
  @Test
  fun pythonInvokesTest() {
    val s = """
        def simple_choice2(sunny, raining):
            if (sunny or raining):
                return sunny
            return not raining
        
        
        def main():
            print(simple_choice2(True, True))
            print(simple_choice2(True, False))
        
        
        if __name__ == "__main__":
            main()
    """.trimIndent()

    PythonChallenge.extractArguments(s,
                                     defMainRegex,
                                     ifMainEndRegex) shouldBeEqualTo listOf("simple_choice2(True, True)",
                                                                            "simple_choice2(True, False)")
  }

  @Test
  fun javaInvokesTest() {
    val s = """
      package warmup1;
      
      public class JoinEnds {
      
          public static String joinEnds(String str) {
              if (str.length() < 2)
                  return str;
      
              String b = str.substring(0, 1);
              String e = str.substring(str.length() - 1);
      
              return e + b;
          }
      
          public static void main(String[] args) {
              System.out.println(joinEnds("Blue zebra"));
              System.out.println(joinEnds("Tree"));
          }
      }
    """.trimIndent()

    JavaChallenge.extractArguments(s, psvmRegex, javaEndRegex) shouldBeEqualTo listOf("""joinEnds("Blue zebra")""",
                                                                                      """joinEnds("Tree")""")
  }

  @Test
  fun kotlinInvokesTest() {
    val s = """
      package lambda1
      
      fun List<String>.combine2(): String = mapIndexed { i, s -> i.toString() + s }.joinToString(", ")
      
      fun main() {
        println(listOf("a").combine2())
        println(listOf("a", "b", "c", "d").combine2())
      }
    """.trimIndent()

    KotlinChallenge.extractArguments(s,
                                     kotlinStartRegex,
                                     kotlinEndRegex) shouldBeEqualTo listOf("""listOf("a").combine2()""",
                                                                            """listOf("a", "b", "c", "d").combine2()""")
  }

  @Test
  fun classImportTest() {

    val variable = "content"
    addImports("", variable).trim() shouldBeEqualTo variable

    val s1 = "ReadingBatServer"
    addImports(s1, variable).trimIndent() shouldBeEqualTo
        """
        $s1
        $variable
        """.trimIndent()

    val s2 = "ReadingBatServer()"
    addImports(s2, variable).trimIndent() shouldBeEqualTo
        """
        import com.github.readingbat.ReadingBatServer

        $s2
        $variable
        """.trimIndent()

    val s3 = "GitHubContent"
    addImports(s3, variable).trimIndent() shouldBeEqualTo
        """
        $s3
        $variable
        """.trimIndent()

    val s4 = "GitHubContent()"
    addImports(s4, variable).trimIndent() shouldBeEqualTo
        """
        import com.github.readingbat.dsl.GitHubContent

        $s4
        $variable
        """.trimIndent()

    val s5 = "ReadingBatServer GitHubContent"
    addImports(s5, variable).trimIndent() shouldBeEqualTo
        """
        $s5
        $variable
        """.trimIndent()

    val s6 = "ReadingBatServer() GitHubContent()"
    addImports(s6, variable).trimIndent() shouldBeEqualTo
        """
        import com.github.readingbat.ReadingBatServer
        import com.github.readingbat.dsl.GitHubContent

        $s6
        $variable
        """.trimIndent()
  }

  @Test
  fun methodImportTest() {

    val variable = "content"

    val s1 = "readingBatContent"
    addImports(s1, variable).trimIndent() shouldBeEqualTo
        """
        $s1
        $variable
        """.trimIndent()

    val s2 = "readingBatContent()"
    addImports(s2, variable).trimIndent() shouldBeEqualTo
        """
        import com.github.readingbat.dsl.readingBatContent
        
        $s2
        $variable
        """.trimIndent()

    val s3 = "include"
    addImports(s3, variable).trimIndent() shouldBeEqualTo
        """
        $s3
        $variable
        """.trimIndent()

    val s4 = "include()"
    addImports(s4, variable).trimIndent() shouldBeEqualTo
        """
        import com.github.readingbat.dsl.include
        
        $s4
        $variable
        """.trimIndent()

    val s5 = "ReadingBatServer GitHubContent"
    println(addImports(s5, variable))
    addImports(s5, variable).trimIndent() shouldBeEqualTo
        """
        $s5
        $variable
        """.trimIndent()

    val s6 = "ReadingBatServer() GitHubContent()"
    println(addImports(s6, variable))
    addImports(s6, variable).trimIndent() shouldBeEqualTo
        """
        import com.github.readingbat.ReadingBatServer
        import com.github.readingbat.dsl.GitHubContent

        $s6
        $variable
        """.trimIndent()
  }

  @Test
  fun combinedImportTest() {
    val variable = "content"

    val s1 = "readingBatContent ReadingBatServer"
    addImports(s1, variable).trimIndent() shouldBeEqualTo
        """
        $s1
        $variable
        """.trimIndent()

    val s2 = "readingBatContent() ReadingBatServer()"
    addImports(s2, variable).trimIndent() shouldBeEqualTo
        """
        import com.github.readingbat.ReadingBatServer
        import com.github.readingbat.dsl.readingBatContent
        
        $s2
        $variable
        """.trimIndent()

    val s3 = "include readingBatContent ReadingBatServer GitHubContent"
    addImports(s3, variable).trimIndent() shouldBeEqualTo
        """
        $s3
        $variable
        """.trimIndent()

    val s4 = "include() readingBatContent() ReadingBatServer() GitHubContent()"
    addImports(s4, variable).trimIndent() shouldBeEqualTo
        """
        import com.github.readingbat.ReadingBatServer
        import com.github.readingbat.dsl.GitHubContent
        import com.github.readingbat.dsl.readingBatContent
        import com.github.readingbat.dsl.include
        
        $s4
        $variable
        """.trimIndent()
  }
}