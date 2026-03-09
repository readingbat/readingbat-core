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

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs

class EmptyListDefaultTest : StringSpec() {
  init {
    "emptyList() should return the singleton empty list" {
      val list1 = emptyList<String>()
      val list2 = emptyList<String>()
      list1 shouldBeSameInstanceAs list2
    }

    "emptyList() default should behave identically to mutableListOf() when read-only" {
      data class Wrapper(val items: List<String> = emptyList())

      val w = Wrapper()
      w.items shouldBe emptyList()
      w.items.size shouldBe 0
      w.items.isEmpty() shouldBe true
    }

    "data class with emptyList default should support copy with new values" {
      data class Wrapper(val items: List<String> = emptyList())

      val w = Wrapper()
      val w2 = w.copy(items = listOf("a", "b"))
      w2.items shouldBe listOf("a", "b")
      w.items shouldBe emptyList()
    }
  }
}
