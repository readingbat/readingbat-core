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

class ForEachConsistencyTest : StringSpec() {
  init {
    "forEach with destructuring should process all pairs" {
      val pairs = listOf("a" to 1, "b" to 2, "c" to 3)
      val collected = mutableListOf<String>()
      pairs.forEach { (key, value) ->
        collected += "$key=$value"
      }
      collected shouldBe listOf("a=1", "b=2", "c=3")
    }

    "forEach with destructuring on empty list should be no-op" {
      val pairs = emptyList<Pair<String, Int>>()
      var count = 0
      pairs.forEach { (_, _) ->
        count++
      }
      count shouldBe 0
    }

    "forEach with destructuring should maintain order" {
      val pairs = (1..100).map { "key$it" to it }
      val indices = mutableListOf<Int>()
      pairs.forEach { (_, value) ->
        indices += value
      }
      indices shouldBe (1..100).toList()
    }
  }
}
