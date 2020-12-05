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

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import io.kotest.property.forAll

class PropertyExample : StringSpec(
  {
    "String3 size" {
      forAll<String, String> { a, b ->
        ((a + b).length == a.length + b.length)
      }
    }
  })

class PropertyExample2 : StringSpec(
  {
    "integers under addition should have an identity value" {
      checkAll<Int, Int, Int> { a, b, c ->
        println("$a $b")
        a + 0 shouldBe a
        0 + a shouldBe a
      }
    }
  })

class PropertyExample3 : StringSpec(
  {
    "is allowed to drink in Chicago" {
      forAll(Arb.int(21..150)) { a ->
        isDrinkingAge(a) // assuming some function that calculates if we're old enough to drink
        true
      }
    }

    "is allowed to drink in London" {
      forAll(Arb.int(18..150)) { a ->
        isDrinkingAge(a) // assuming some function that calculates if we're old enough to drink
        true
      }
    }
  })

fun isDrinkingAge(a: Int) = a % 2 == 0
