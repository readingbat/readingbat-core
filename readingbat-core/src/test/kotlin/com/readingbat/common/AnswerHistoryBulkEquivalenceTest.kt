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

package com.readingbat.common

import com.pambrose.common.email.Email
import com.pambrose.common.exposed.upsert
import com.readingbat.server.FullName
import com.readingbat.server.Invocation
import com.readingbat.server.UserAnswerHistoryTable
import com.readingbat.server.userAnswerHistoryIndex
import com.readingbat.withTestApp
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Characterizes the equivalence the ChallengeGroupWs N+1 fix relies on: a single
 * [User.answerHistoryBulk] call returns an entry for exactly the md5s that have stored history
 * (matching the per-invocation [User.historyExists]), with the same correct/incorrectAttempts as
 * [User.answerHistory]. This lets the dashboard aggregation use one query per enrollee instead of
 * two transactions per invocation.
 */
class AnswerHistoryBulkEquivalenceTest : StringSpec() {
  init {
    "answerHistoryBulk matches per-invocation historyExists/answerHistory" {
      withTestApp {
        val user =
          User.createOAuthUser(
            name = FullName("Bulk Equivalence User"),
            emailVal = Email("bulk-equiv@test.com"),
            provider = OAuthProvider.GITHUB,
            providerId = "bulk-equiv-001",
          )

        transaction {
          with(UserAnswerHistoryTable) {
            upsert(conflictIndex = userAnswerHistoryIndex) { row ->
              row[userRef] = user.userDbmsId
              row[md5] = "md5-a"
              row[invocation] = "f(1)"
              row[updated] = nowInstant()
              row[correct] = true
              row[incorrectAttempts] = 2
              row[historyJson] = "[]"
            }
            upsert(conflictIndex = userAnswerHistoryIndex) { row ->
              row[userRef] = user.userDbmsId
              row[md5] = "md5-b"
              row[invocation] = "f(2)"
              row[updated] = nowInstant()
              row[correct] = false
              row[incorrectAttempts] = 0
              row[historyJson] = "[]"
            }
          }
        }

        val bulk = user.answerHistoryBulk(listOf("md5-a", "md5-b", "md5-c"))

        // Present for exactly the md5s with stored history (== historyExists), absent otherwise.
        bulk.keys shouldBe setOf("md5-a", "md5-b")
        user.historyExists("md5-a", Invocation("f(1)")) shouldBe true
        user.historyExists("md5-c", Invocation("f(9)")) shouldBe false

        // Values match the per-invocation answerHistory.
        bulk.getValue("md5-a").correct shouldBe true
        bulk.getValue("md5-a").incorrectAttempts shouldBe 2
        bulk.getValue("md5-b").correct shouldBe false
        bulk.getValue("md5-a").correct shouldBe user.answerHistory("md5-a", Invocation("f(1)")).correct
      }
    }
  }
}
