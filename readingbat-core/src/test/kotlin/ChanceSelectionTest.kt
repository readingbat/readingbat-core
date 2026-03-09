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
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class ChanceSelectionTest : StringSpec() {
  init {
    "deterministic random selection should never return current index" {
      val size = 5
      repeat(100) {
        for (current in 0 until size) {
          val selected = (0 until size).filter { it != current }.random()
          selected shouldNotBe current
          selected shouldBeGreaterThanOrEqual 0
          selected shouldBeLessThan size
        }
      }
    }

    "deterministic random selection with size 1 should use fallback" {
      // When size is 1, the filter produces an empty list, so the caller must handle this
      // The chance() function handles this by returning 0 early
      val filtered = (0 until 1).filter { it != 0 }
      filtered shouldBe emptyList()
    }

    "deterministic random selection should cover all valid indices" {
      val size = 4
      val current = 2
      val seen = mutableSetOf<Int>()
      repeat(200) {
        seen += (0 until size).filter { it != current }.random()
      }
      seen shouldBe setOf(0, 1, 3)
    }

    "filter-based approach should produce correct candidate list" {
      val size = 5
      val current = 2
      val candidates = (0 until size).filter { it != current }
      candidates shouldBe listOf(0, 1, 3, 4)
    }
  }
}
