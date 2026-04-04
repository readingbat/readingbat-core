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

package com.readingbat.server

import com.readingbat.common.User
import com.readingbat.posts.ChallengeHistory
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.sql.Connection

class SerializableTransactionTest : StringSpec() {
  init {
    "TRANSACTION_SERIALIZABLE constant has expected value" {
      Connection.TRANSACTION_SERIALIZABLE shouldBe 8
    }

    "TRANSACTION_REPEATABLE_READ is less strict than SERIALIZABLE" {
      Connection.TRANSACTION_REPEATABLE_READ shouldBe 4
      (Connection.TRANSACTION_SERIALIZABLE > Connection.TRANSACTION_REPEATABLE_READ) shouldBe true
    }

    "ChallengeHistory markCorrect preserves existing incorrectAttempts" {
      val history =
        ChallengeHistory(
        invocation = Invocation("test()"),
        correct = false,
        incorrectAttempts = 3,
        answers = mutableListOf("wrong1", "wrong2", "wrong3"),
      )

      history.markCorrect("right")
      history.correct shouldBe true
      history.incorrectAttempts shouldBe 3
      history.answers.last() shouldBe "right"
    }

    "ChallengeHistory markIncorrect increments incorrectAttempts" {
      val history =
        ChallengeHistory(
        invocation = Invocation("test()"),
        correct = false,
        incorrectAttempts = 2,
        answers = mutableListOf("wrong1", "wrong2"),
      )

      history.markIncorrect("wrong3")
      history.correct shouldBe false
      history.incorrectAttempts shouldBe 3
      history.answers shouldBe mutableListOf("wrong1", "wrong2", "wrong3")
    }

    "ChallengeHistory markIncorrect does not increment for duplicate answer" {
      val history =
        ChallengeHistory(
        invocation = Invocation("test()"),
        correct = false,
        incorrectAttempts = 1,
        answers = mutableListOf("wrong1"),
      )

      history.markIncorrect("wrong1")
      history.incorrectAttempts shouldBe 1
      history.answers shouldBe mutableListOf("wrong1")
    }

    "ChallengeHistory concurrent mutation scenario shows why isolation matters" {
      // Simulate what happens without proper isolation:
      // Two requests read the same history state, both mutate, last writer wins
      val sharedState =
        ChallengeHistory(
        invocation = Invocation("test()"),
        correct = false,
        incorrectAttempts = 0,
      )

      // Request 1 reads: incorrectAttempts = 0
      val request1Copy =
        ChallengeHistory(
        sharedState.invocation,
        sharedState.correct,
        sharedState.incorrectAttempts,
        sharedState.answers.toMutableList(),
      )

      // Request 2 reads: incorrectAttempts = 0 (stale!)
      val request2Copy =
        ChallengeHistory(
        sharedState.invocation,
        sharedState.correct,
        sharedState.incorrectAttempts,
        sharedState.answers.toMutableList(),
      )

      // Both increment independently
      request1Copy.markIncorrect("attempt1")
      request2Copy.markIncorrect("attempt2")

      // Without serializable isolation, last writer wins — only 1 attempt recorded
      request1Copy.incorrectAttempts shouldBe 1
      request2Copy.incorrectAttempts shouldBe 1

      // With serializable isolation, the second transaction would see the first's commit
      // and correctly increment to 2
    }

    "answerHistoryInTransaction method should exist on User class" {
      val method =
        User::class.java.methods.find {
          it.name == "answerHistoryInTransaction"
        }
      (method != null) shouldBe true
      method!!.parameterCount shouldBe 2
    }
  }
}
