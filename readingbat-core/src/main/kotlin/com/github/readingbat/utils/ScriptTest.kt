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

package com.github.readingbat.utils

import com.github.pambrose.common.script.JavaScript
import com.github.pambrose.common.script.PythonScript
import com.github.readingbat.common.Limiter
import com.github.readingbat.dsl.parse.KotlinParse
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.script.Bindings
import javax.script.ScriptContext
import javax.script.ScriptContext.ENGINE_SCOPE
import javax.script.ScriptEngine
import javax.script.ScriptEngineManager
import javax.script.SimpleScriptContext
import kotlin.reflect.typeOf
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

fun main() {
  val limiter = Limiter(3)

  runBlocking {
    repeat(100) {
      println("Firing off $it")
      launch {
        limiter.request {
          println("I am here $it")
          delay(5.seconds)
        }
      }
    }
  }
}

@Suppress("unused")
fun javaTest() {
  val correctAnswers = mutableListOf<Any>()
  val script =
    """
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

  val engine = JavaScript()
  measureTime {
    repeat(100) {
      // JavaScript().use {
      engine.apply {
        import(List::class.java)
        import(ArrayList::class.java)
        add(KotlinParse.VAR_NAME, correctAnswers, typeOf<Any>())
        evalScript(script)
        println(it)
        // }
      }
      engine.resetContext(false)
    }
  }.also {
    println(it)
  }
}

@Suppress("unused")
fun pythonTest() {
  val correctAnswers = mutableListOf<Any>()

  val script =
    """
      def less_than(val1, val2):
          result = val1 < val2
          return result

      answers.add(less_than(4, 6))
      answers.add(less_than(8, 12))
      answers.add(less_than(19, 19))
      answers.add(less_than(12, 8))
      answers.add(less_than(11, 28))
    """.trimIndent()
  val engine = PythonScript()
  measureTime {
    repeat(1000) {
      // engine.use {
      engine.run {
        add(KotlinParse.VAR_NAME, correctAnswers)
        measureTime { eval(script) }
      }
      correctAnswers.clear()
      //  }
      engine.resetContext(true)
      println(it)
    }
  }.also {
    println(it)
  }

  println(correctAnswers)
}

val ScriptEngine.bindings: Bindings get() = getBindings(ENGINE_SCOPE)

@Suppress("unused")
val ScriptContext.bindings: Bindings
  get() = getBindings(ENGINE_SCOPE)

fun ScriptEngine.reset(scope: Int = ENGINE_SCOPE) {
  context = SimpleScriptContext().apply { setBindings(createBindings(), scope) }
}

object MultipleScopes {
  @JvmStatic
  fun main(args: Array<String>) {
    val manager = ScriptEngineManager()
    val engine = manager.getEngineByName("jython")

    engine.reset()
    engine.eval("y = 44")
    engine.bindings["x"] = "hello"
    engine.eval("print(x)")
    engine.eval("print(y)")
    engine.eval("x = \"really\"")
    engine.eval("print(x)")

    engine.reset()
    engine.bindings["x"] = "world"
    engine.eval("print(x)")
    // engine.eval("print(y)")
    engine.eval("print(x)")
  }
}
