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

import com.github.readingbat.dsl.firstLineNumOf
import com.github.readingbat.dsl.lastLineNumOf
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test

class UtilsTest {
  @Test
  fun testLineIndexes() {
    val s = """
      aaa
      bbb
      ccc
      ddd
      eee
      aaa
      bbb
      ccc
      ddd
      eee
    """.trimIndent()

    s.firstLineNumOf(Regex("zzz")) shouldBeEqualTo -1
    s.firstLineNumOf(Regex("bbb")) shouldBeEqualTo 1

    s.lastLineNumOf(Regex("zzz")) shouldBeEqualTo -1
    s.lastLineNumOf(Regex("bbb")) shouldBeEqualTo 6
  }
}