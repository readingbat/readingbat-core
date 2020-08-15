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

package com.github.readingbat.server

import com.github.pambrose.common.script.PythonScript
import io.ktor.utils.io.pool.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class ScriptPool {

}

class PythonPool(size: Int) : DefaultPool<PythonScript>(size) {
  override fun produceInstance(): PythonScript = PythonScript()

  override fun clearInstance(instance: PythonScript): PythonScript =
    instance.apply {
      println("Resetting script")
      reset()
    }
}

fun main() {

  val pool = PythonPool(1)

  runBlocking {
    for (i in (1..10)) {
      launch {
        print("Launching $i\n")
        val s = pool.borrow()
        print("Executing $i\n")
        try {
          s.eval("print(5)")
          delay(5000)
        } finally {
          pool.recycle(s)
        }
      }
    }
  }

}
