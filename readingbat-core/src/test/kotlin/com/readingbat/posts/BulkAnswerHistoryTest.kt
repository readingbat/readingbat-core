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

package com.readingbat.posts

import com.readingbat.server.Invocation
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class BulkAnswerHistoryTest : StringSpec() {
  init {
    "Default ChallengeHistory has correct defaults" {
      val history = ChallengeHistory(Invocation("test(1)"))
      history.correct shouldBe false
      history.incorrectAttempts shouldBe 0
      history.answers.shouldBeEmpty()
      history.invocation.value shouldBe "test(1)"
    }

    "ChallengeHistory with data preserves all fields" {
      val answers = mutableListOf("wrong1", "wrong2", "correct")
      val history =
        ChallengeHistory(
          invocation = Invocation("foo(2)"),
          correct = true,
          incorrectAttempts = 2,
          answers = answers,
        )
      history.correct shouldBe true
      history.incorrectAttempts shouldBe 2
      history.answers shouldContainExactly listOf("wrong1", "wrong2", "correct")
    }

    "Bulk result map lookup falls back to default for missing md5" {
      val historyMap =
        mapOf(
          "abc123" to
            ChallengeHistory(
              invocation = Invocation("test(1)"),
              correct = true,
              incorrectAttempts = 0,
              answers = mutableListOf("42"),
            ),
        )

      val found = historyMap["abc123"] ?: ChallengeHistory(Invocation("test(1)"))
      found.correct shouldBe true
      found.answers shouldContainExactly listOf("42")

      val missing = historyMap["xyz789"] ?: ChallengeHistory(Invocation("test(2)"))
      missing.correct shouldBe false
      missing.answers.shouldBeEmpty()
      missing.invocation.value shouldBe "test(2)"
    }

    "Multiple invocations map to distinct entries" {
      val historyMap =
        mapOf(
          "md5_1" to ChallengeHistory(Invocation("foo(1)"), true, 0, mutableListOf("1")),
          "md5_2" to ChallengeHistory(Invocation("foo(2)"), false, 3, mutableListOf("a", "b", "c")),
          "md5_3" to ChallengeHistory(Invocation("foo(3)"), true, 1, mutableListOf("x", "3")),
        )

      val invocations =
        listOf(
          Invocation("foo(1)") to "md5_1",
          Invocation("foo(2)") to "md5_2",
          Invocation("foo(3)") to "md5_3",
        )

      var numCorrect = 0
      val results =
        invocations.map { (invocation, md5) ->
          val history = historyMap[md5] ?: ChallengeHistory(invocation)
          if (history.correct) numCorrect++
          invocation to history
        }

      numCorrect shouldBe 2
      results.size shouldBe 3
      results[0].second.correct shouldBe true
      results[1].second.incorrectAttempts shouldBe 3
      results[2].second.answers shouldContainExactly listOf("x", "3")
    }

    "Empty history map returns defaults for all invocations" {
      val emptyMap = emptyMap<String, ChallengeHistory>()
      val invocations = listOf("md5_a", "md5_b", "md5_c")

      val results =
        invocations.map { md5 ->
          emptyMap[md5] ?: ChallengeHistory(Invocation("test()"))
        }

      results.forEach {
        it.correct shouldBe false
        it.incorrectAttempts shouldBe 0
        it.answers.shouldBeEmpty()
      }
    }
  }
}
