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

import com.github.readingbat.dsl.JavaChallenge.Companion.javaEnd
import com.github.readingbat.dsl.JavaChallenge.Companion.javaInvokes
import com.github.readingbat.dsl.JavaChallenge.Companion.javaStart
import com.github.readingbat.dsl.KotlinChallenge.Companion.kotlinInvokes
import com.github.readingbat.dsl.PythonChallenge.Companion.pythonEnd
import com.github.readingbat.dsl.PythonChallenge.Companion.pythonInvokes
import com.github.readingbat.dsl.PythonChallenge.Companion.pythonStart
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

    s.pythonInvokes(pythonStart, pythonEnd) shouldBeEqualTo listOf("simple_choice2(True, True)",
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

    s.javaInvokes(javaStart, javaEnd) shouldBeEqualTo listOf("""joinEnds("Blue zebra")""",
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

    s.kotlinInvokes(javaStart, javaEnd) shouldBeEqualTo listOf("""listOf("a").combine2()""",
                                                               """listOf("a", "b", "c", "d").combine2()""")
  }
}