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

import TestData.readTestContent
import com.github.pambrose.common.email.Email
import com.github.readingbat.common.User
import com.github.readingbat.kotest.TestDatabase
import com.github.readingbat.kotest.TestSupport.initTestProperties
import com.github.readingbat.kotest.TestSupport.testModule
import com.github.readingbat.server.ChallengeProgressService
import com.github.readingbat.server.FullName
import com.github.readingbat.server.UserChallengeInfoTable
import com.github.readingbat.server.userChallengeInfoIndex
import com.pambrose.common.exposed.upsert
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.joda.time.DateTime
import org.joda.time.DateTimeZone.UTC

class ChallengeProgressServiceTest : StringSpec() {
  init {
    "isCorrect returns false when user is null" {
      initTestProperties()
      TestDatabase.connectAndMigrate()

      readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            ChallengeProgressService.isCorrect(null, "some-md5") shouldBe false
          }
        }
    }

    "isCorrect returns false for unknown challenge md5" {
      initTestProperties()
      TestDatabase.connectAndMigrate()

      readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            val user =
              User.createOAuthUser(
                name = FullName("Progress Unknown"),
                emailVal = Email("progress-unknown@test.com"),
                provider = "github",
                providerId = "progress-unknown-001",
                accessToken = "token-progress-unknown",
              )

            ChallengeProgressService.isCorrect(user, "nonexistent-md5") shouldBe false
          }
        }
    }

    "isCorrect returns true when allCorrect is set" {
      initTestProperties()
      TestDatabase.connectAndMigrate()

      readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            val user =
              User.createOAuthUser(
                name = FullName("Progress Correct"),
                emailVal = Email("progress-correct@test.com"),
                provider = "github",
                providerId = "progress-correct-001",
                accessToken = "token-progress-correct",
              )

            val md5 = "test-challenge-md5-correct"

            transaction {
              with(UserChallengeInfoTable) {
                upsert(conflictIndex = userChallengeInfoIndex) { row ->
                  row[userRef] = user.userDbmsId
                  row[UserChallengeInfoTable.md5] = md5
                  row[created] = DateTime.now(UTC)
                  row[updated] = DateTime.now(UTC)
                  row[allCorrect] = true
                  row[likeDislike] = 0
                  row[answersJson] = "{}"
                }
              }
            }

            ChallengeProgressService.isCorrect(user, md5) shouldBe true
          }
        }
    }

    "isCorrect returns false when allCorrect is false" {
      initTestProperties()
      TestDatabase.connectAndMigrate()

      readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            val user =
              User.createOAuthUser(
                name = FullName("Progress Incorrect"),
                emailVal = Email("progress-incorrect@test.com"),
                provider = "github",
                providerId = "progress-incorrect-001",
                accessToken = "token-progress-incorrect",
              )

            val md5 = "test-challenge-md5-incorrect"

            transaction {
              with(UserChallengeInfoTable) {
                upsert(conflictIndex = userChallengeInfoIndex) { row ->
                  row[userRef] = user.userDbmsId
                  row[UserChallengeInfoTable.md5] = md5
                  row[created] = DateTime.now(UTC)
                  row[updated] = DateTime.now(UTC)
                  row[allCorrect] = false
                  row[likeDislike] = 0
                  row[answersJson] = "{}"
                }
              }
            }

            ChallengeProgressService.isCorrect(user, md5) shouldBe false
          }
        }
    }
  }
}
