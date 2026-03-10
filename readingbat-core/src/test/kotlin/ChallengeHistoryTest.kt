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

import com.github.readingbat.posts.ChallengeHistory
import com.github.readingbat.posts.ChallengeResults
import com.github.readingbat.server.Invocation
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class ChallengeHistoryTest : StringSpec() {
  init {
    "markCorrect sets correct flag and appends answer" {
      val history = ChallengeHistory(Invocation("test(1)"))
      history.correct shouldBe false

      history.markCorrect("42")

      history.correct shouldBe true
      history.answers shouldContainExactly listOf("42")
      history.incorrectAttempts shouldBe 0
    }

    "markIncorrect sets correct false and increments attempts" {
      val history = ChallengeHistory(Invocation("test(1)"))

      history.markIncorrect("wrong")

      history.correct shouldBe false
      history.incorrectAttempts shouldBe 1
      history.answers shouldContainExactly listOf("wrong")
    }

    "markUnanswered sets correct to false" {
      val history = ChallengeHistory(Invocation("test(1)"), correct = true)
      history.correct shouldBe true

      history.markUnanswered()

      history.correct shouldBe false
    }

    "duplicate consecutive answers are not appended" {
      val history = ChallengeHistory(Invocation("test(1)"))

      history.markIncorrect("wrong")
      history.markIncorrect("wrong")

      history.answers shouldContainExactly listOf("wrong")
      history.incorrectAttempts shouldBe 1
    }

    "different answers are appended in order" {
      val history = ChallengeHistory(Invocation("test(1)"))

      history.markIncorrect("first")
      history.markIncorrect("second")
      history.markCorrect("correct")

      history.answers shouldContainExactly listOf("first", "second", "correct")
      history.incorrectAttempts shouldBe 2
      history.correct shouldBe true
    }

    "blank answers are not appended" {
      val history = ChallengeHistory(Invocation("test(1)"))

      history.markCorrect("")
      history.correct shouldBe true
      history.answers.shouldBeEmpty()

      history.markIncorrect("")
      history.correct shouldBe false
      history.incorrectAttempts shouldBe 0
      history.answers.shouldBeEmpty()
    }

    "pre-fetched histories produce correct marking results" {
      // Simulate the pre-fetch pattern: create histories first, then mark them
      val invocations =
        listOf(
          Invocation("test(1)"),
          Invocation("test(2)"),
          Invocation("test(3)"),
        )
      val results =
        listOf(
          ChallengeResults(invocations[0], userResponse = "42", answered = true, correct = true),
          ChallengeResults(invocations[1], userResponse = "wrong", answered = true, correct = false),
          ChallengeResults(invocations[2], userResponse = "", answered = false, correct = false),
        )

      // Pre-fetch: create histories before processing (simulates reading from DB before write tx)
      val histories = invocations.map { ChallengeHistory(it) }

      // Process: mark histories based on results (happens inside write tx)
      results.zip(histories).forEach { (result, history) ->
        when {
          !result.answered -> history.markUnanswered()
          result.correct -> history.markCorrect(result.userResponse)
          else -> history.markIncorrect(result.userResponse)
        }
      }

      // Verify results
      histories[0].correct shouldBe true
      histories[0].answers shouldContainExactly listOf("42")
      histories[0].incorrectAttempts shouldBe 0

      histories[1].correct shouldBe false
      histories[1].answers shouldContainExactly listOf("wrong")
      histories[1].incorrectAttempts shouldBe 1

      histories[2].correct shouldBe false
      histories[2].answers.shouldBeEmpty()
    }

    "history with existing state is updated correctly by marking" {
      // Simulate a history that was previously fetched from the DB with existing answers
      val history =
        ChallengeHistory(
          invocation = Invocation("test(1)"),
          correct = false,
          incorrectAttempts = 2,
          answers = mutableListOf("attempt1", "attempt2"),
        )

      // User now submits the correct answer
      history.markCorrect("correct_answer")

      history.correct shouldBe true
      history.incorrectAttempts shouldBe 2 // unchanged
      history.answers shouldContainExactly listOf("attempt1", "attempt2", "correct_answer")
    }
  }
}
