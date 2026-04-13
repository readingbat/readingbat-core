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
import com.pambrose.common.exposed.get
import com.pambrose.common.exposed.readonlyTx
import com.pambrose.common.exposed.upsert
import com.readingbat.server.FullName
import com.readingbat.server.OAuthLinksTable
import com.readingbat.server.UserAnswerHistoryTable
import com.readingbat.server.UserChallengeInfoTable
import com.readingbat.server.UsersTable
import com.readingbat.server.userAnswerHistoryIndex
import com.readingbat.server.userChallengeInfoIndex
import com.readingbat.withTestApp
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class DateTimeMigrationTest : StringSpec() {
  init {
    "nowInstant should return a recent timestamp" {
      val before = nowInstant()
      val now = nowInstant()
      val after = nowInstant()

      now shouldBeGreaterThanOrEqualTo before
      after shouldBeGreaterThanOrEqualTo now
      (after - before) shouldBeLessThanOrEqualTo 1.seconds
    }

    "nowInstant should return kotlin.time.Instant type" {
      val now: Instant = nowInstant()
      now.toEpochMilliseconds() shouldBeGreaterThan 0
    }

    "user created timestamp should round-trip through database" {
      withTestApp {
        val beforeCreate = nowInstant()

        val user =
          User.createOAuthUser(
            name = FullName("DateTime Migration User"),
            emailVal = Email("datetime-migration@test.com"),
            provider = OAuthProvider.GITHUB,
            providerId = "datetime-migration-001",
            accessToken = "token-datetime-migration",
          )

        val afterCreate = nowInstant()

        // Read back the created and updated timestamps from the database
        readonlyTx {
          UsersTable
            .select(UsersTable.created, UsersTable.updated)
            .where { UsersTable.id eq user.userDbmsId }
            .single()
            .let { row ->
              val created = row[UsersTable.created]
              val updated = row[UsersTable.updated]

              created.shouldNotBeNull()
              updated.shouldNotBeNull()

              // Timestamps should be within the window of user creation
              created shouldBeGreaterThanOrEqualTo beforeCreate
              created shouldBeLessThanOrEqualTo afterCreate
              updated shouldBeGreaterThanOrEqualTo beforeCreate
              updated shouldBeLessThanOrEqualTo afterCreate
            }
        }
      }
    }

    "oauth link timestamps should persist correctly" {
      withTestApp {
        val beforeCreate = nowInstant()

        User.createOAuthUser(
          name = FullName("OAuth Timestamp User"),
          emailVal = Email("oauth-timestamp@test.com"),
          provider = OAuthProvider.GITHUB,
          providerId = "oauth-timestamp-001",
          accessToken = "token-oauth-timestamp",
        )

        val afterCreate = nowInstant()

        // Read back the OAuth link timestamps
        readonlyTx {
          OAuthLinksTable
            .select(OAuthLinksTable.created, OAuthLinksTable.updated)
            .where { OAuthLinksTable.providerId eq "oauth-timestamp-001" }
            .single()
            .let { row ->
              val created = row[OAuthLinksTable.created]
              val updated = row[OAuthLinksTable.updated]

              created.shouldNotBeNull()
              updated.shouldNotBeNull()

              created shouldBeGreaterThanOrEqualTo beforeCreate
              created shouldBeLessThanOrEqualTo afterCreate
            }
        }
      }
    }

    "updated timestamp should change on upsert" {
      withTestApp {
        val user =
          User.createOAuthUser(
            name = FullName("Upsert Timestamp User"),
            emailVal = Email("upsert-timestamp@test.com"),
            provider = OAuthProvider.GITHUB,
            providerId = "upsert-timestamp-001",
            accessToken = "token-upsert-timestamp",
          )

        val challengeMd5 = "upsert-timestamp-md5"

        // First insert
        val firstInsertTime = nowInstant()
        transaction {
          with(UserChallengeInfoTable) {
            upsert(conflictIndex = userChallengeInfoIndex) { row ->
              row[userRef] = user.userDbmsId
              row[md5] = challengeMd5
              row[updated] = firstInsertTime
              row[allCorrect] = false
              row[likeDislike] = 0
              row[answersJson] = "{}"
            }
          }
        }

        // Verify first timestamp
        readonlyTx {
          UserChallengeInfoTable
            .select(UserChallengeInfoTable.updated)
            .where { UserChallengeInfoTable.md5 eq challengeMd5 }
            .single()
            .let { row ->
              row[UserChallengeInfoTable.updated] shouldBe firstInsertTime
            }
        }

        // Update with new timestamp
        val updateTime = nowInstant()
        transaction {
          with(UserChallengeInfoTable) {
            upsert(conflictIndex = userChallengeInfoIndex) { row ->
              row[userRef] = user.userDbmsId
              row[md5] = challengeMd5
              row[updated] = updateTime
              row[allCorrect] = true
              row[likeDislike] = 1
              row[answersJson] = "{}"
            }
          }
        }

        // Verify updated timestamp changed
        readonlyTx {
          UserChallengeInfoTable
            .select(UserChallengeInfoTable.updated, UserChallengeInfoTable.allCorrect)
            .where { UserChallengeInfoTable.md5 eq challengeMd5 }
            .single()
            .let { row ->
              row[UserChallengeInfoTable.updated] shouldBe updateTime
              row[UserChallengeInfoTable.allCorrect] shouldBe true
            }
        }
      }
    }

    "answer history timestamp should persist through upsert" {
      withTestApp {
        val user =
          User.createOAuthUser(
            name = FullName("History Timestamp User"),
            emailVal = Email("history-timestamp@test.com"),
            provider = OAuthProvider.GITHUB,
            providerId = "history-timestamp-001",
            accessToken = "token-history-timestamp",
          )

        val historyMd5 = "history-timestamp-md5"
        val invocationText = "test_func(1, 2)"
        val timestamp = nowInstant()

        transaction {
          with(UserAnswerHistoryTable) {
            upsert(conflictIndex = userAnswerHistoryIndex) { row ->
              row[userRef] = user.userDbmsId
              row[md5] = historyMd5
              row[invocation] = invocationText
              row[created] = timestamp
              row[updated] = timestamp
              row[correct] = true
              row[incorrectAttempts] = 0
              row[historyJson] = "[]"
            }
          }
        }

        // Verify the timestamp round-trips correctly
        readonlyTx {
          UserAnswerHistoryTable
            .select(UserAnswerHistoryTable.created, UserAnswerHistoryTable.updated)
            .where { UserAnswerHistoryTable.md5 eq historyMd5 }
            .single()
            .let { row ->
              row[UserAnswerHistoryTable.created] shouldBe timestamp
              row[UserAnswerHistoryTable.updated] shouldBe timestamp
            }
        }
      }
    }

    "instantExpr should work with database timestamp comparisons" {
      withTestApp {
        val user =
          User.createOAuthUser(
            name = FullName("InstantExpr User"),
            emailVal = Email("instantexpr@test.com"),
            provider = OAuthProvider.GITHUB,
            providerId = "instantexpr-001",
            accessToken = "token-instantexpr",
          )

        val challengeMd5 = "instantexpr-md5"
        transaction {
          with(UserChallengeInfoTable) {
            upsert(conflictIndex = userChallengeInfoIndex) { row ->
              row[userRef] = user.userDbmsId
              row[md5] = challengeMd5
              row[updated] = nowInstant()
              row[allCorrect] = true
              row[likeDislike] = 0
              row[answersJson] = "{}"
            }
          }
        }

        // Use instantExpr to query with a raw SQL interval — same pattern as SessionActivites
        readonlyTx {
          val recentCount =
            UserChallengeInfoTable
              .select(UserChallengeInfoTable.md5)
              .where {
                UserChallengeInfoTable.created greater instantExpr("now() - interval '1 day'")
              }
              .count()

          recentCount shouldBeGreaterThan 0
        }
      }
    }

    "instant arithmetic should produce correct duration" {
      val earlier = nowInstant()
      // Small busy loop to ensure measurable time difference
      @Suppress("ControlFlowWithEmptyBody")
      while (nowInstant() == earlier) {
      }
      val later = nowInstant()

      val duration = later - earlier
      duration shouldBeGreaterThanOrEqualTo kotlin.time.Duration.ZERO
      duration shouldBeLessThanOrEqualTo 1.seconds
    }
  }
}
