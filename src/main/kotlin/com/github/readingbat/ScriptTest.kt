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

import com.github.pambrose.common.script.JavaScript
import com.github.pambrose.common.script.PythonScript
import com.github.readingbat.dsl.parse.KotlinParse
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

fun main() {
  javaTest()
}

fun javaTest() {
  val correctAnswers = mutableListOf<Any>()
  val script = """
  public class LessThan {

      public static boolean compare(int val1, int val2) {
          boolean result = val1 < val2;
          return result;
      }

      public List<Object> answers = new ArrayList<Object>();

      public List<Object> getValue() {
          answers.add(compare(4, 6));
          answers.add(compare(8, 12));
          answers.add(compare(19, 19));
          answers.add(compare(12, 8));
          answers.add(compare(11, 28));

          return answers;
      }
  }
""".trimIndent()

  repeat(100000) {
    val timedValue =
      JavaScript().use {
        it.run {
          import(List::class.java)
          import(ArrayList::class.java)
          measureTimedValue { evalScript(script) }
        }
      }

    println("Interation: $it ${timedValue.duration} for values: ${timedValue.value}")
  }

  Thread.sleep(100000000000000000)

}


fun pythonTest() {

  val correctAnswers = mutableListOf<Any>()

  val script = """
def less_than(val1, val2):
    result = val1 < val2
    return result


answers.add(less_than(4, 6))
answers.add(less_than(8, 12))
answers.add(less_than(19, 19))
answers.add(less_than(12, 8))
answers.add(less_than(11, 28))
  """.trimIndent()
  repeat(100000) {
    PythonScript().use {
      it.run {
        add(KotlinParse.varName, correctAnswers)
        measureTime { eval(script) }
      }
    }
    println(it)
  }

  Thread.sleep(100000000000000000)

  println(correctAnswers)
}