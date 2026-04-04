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

import com.pambrose.common.email.Email
import com.pambrose.common.exposed.upsert
import com.readingbat.TestData
import com.readingbat.common.Endpoints
import com.readingbat.common.User
import com.readingbat.kotest.TestDatabase
import com.readingbat.kotest.TestSupport.initTestProperties
import com.readingbat.kotest.TestSupport.testModule
import com.readingbat.server.FullName
import com.readingbat.server.UserChallengeInfoTable
import com.readingbat.server.userChallengeInfoIndex
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.server.testing.testApplication
import kotlinx.html.Entities
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.joda.time.DateTime
import org.joda.time.DateTimeZone

class LikeDislikePersistenceTest : StringSpec() {
  init {
    "likeDislike should default to 0 for challenges with no entry" {
      initTestProperties()
      TestDatabase.connectAndMigrate()

      TestData.readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            val user =
              User.createOAuthUser(
                name = FullName("User LikeDefault"),
                emailVal = Email("user-likedefault@test.com"),
                provider = "github",
                providerId = "user-likedefault-001",
                accessToken = "token-user-likedefault",
              )

            user.likeDislikes().shouldBeEmpty()
          }
        }
    }

    "like should persist and be readable" {
      initTestProperties()
      TestDatabase.connectAndMigrate()

      TestData.readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            val user =
              User.createOAuthUser(
                name = FullName("User LikePersist"),
                emailVal = Email("user-likepersist@test.com"),
                provider = "github",
                providerId = "user-likepersist-001",
                accessToken = "token-user-likepersist",
              )

            val challengeMd5 = "like-persist-md5"

            // Set like (value = 1)
            transaction {
              with(UserChallengeInfoTable) {
                upsert(conflictIndex = userChallengeInfoIndex) { row ->
                  row[userRef] = user.userDbmsId
                  row[md5] = challengeMd5
                  row[updated] = DateTime.now(DateTimeZone.UTC)
                  row[likeDislike] = 1
                }
              }
            }

            user.likeDislikes() shouldHaveSize 1
          }
        }
    }

    "dislike should persist and be readable" {
      initTestProperties()
      TestDatabase.connectAndMigrate()

      TestData.readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            val user =
              User.createOAuthUser(
                name = FullName("User DislikePersist"),
                emailVal = Email("user-dislikepersist@test.com"),
                provider = "github",
                providerId = "user-dislikepersist-001",
                accessToken = "token-user-dislikepersist",
              )

            val challengeMd5 = "dislike-persist-md5"

            // Set dislike (value = 2)
            transaction {
              with(UserChallengeInfoTable) {
                upsert(conflictIndex = userChallengeInfoIndex) { row ->
                  row[userRef] = user.userDbmsId
                  row[md5] = challengeMd5
                  row[updated] = DateTime.now(DateTimeZone.UTC)
                  row[likeDislike] = 2
                }
              }
            }

            user.likeDislikes() shouldHaveSize 1
          }
        }
    }

    "likeDislikeEmoji should return correct emoji for each value" {
      initTestProperties()
      TestDatabase.connectAndMigrate()

      TestData.readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            val user =
              User.createOAuthUser(
                name = FullName("User Emoji"),
                emailVal = Email("user-emoji@test.com"),
                provider = "github",
                providerId = "user-emoji-001",
                accessToken = "token-user-emoji",
              )

            user.likeDislikeEmoji(1) shouldBe Endpoints.THUMBS_UP
            user.likeDislikeEmoji(2) shouldBe Endpoints.THUMBS_DOWN
            user.likeDislikeEmoji(0) shouldBe Entities.nbsp.text
          }
        }
    }

    "updating like to dislike should overwrite the value" {
      initTestProperties()
      TestDatabase.connectAndMigrate()

      TestData.readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            val user =
              User.createOAuthUser(
                name = FullName("User LikeUpdate"),
                emailVal = Email("user-likeupdate@test.com"),
                provider = "github",
                providerId = "user-likeupdate-001",
                accessToken = "token-user-likeupdate",
              )

            val challengeMd5 = "like-update-md5"

            // Set like
            transaction {
              with(UserChallengeInfoTable) {
                upsert(conflictIndex = userChallengeInfoIndex) { row ->
                  row[userRef] = user.userDbmsId
                  row[md5] = challengeMd5
                  row[updated] = DateTime.now(DateTimeZone.UTC)
                  row[likeDislike] = 1
                }
              }
            }

            user.likeDislikes() shouldHaveSize 1

            // Update to dislike
            transaction {
              with(UserChallengeInfoTable) {
                upsert(conflictIndex = userChallengeInfoIndex) { row ->
                  row[userRef] = user.userDbmsId
                  row[md5] = challengeMd5
                  row[updated] = DateTime.now(DateTimeZone.UTC)
                  row[likeDislike] = 2
                }
              }
            }

            // Still 1 entry, but changed to dislike
            user.likeDislikes() shouldHaveSize 1
          }
        }
    }
  }
}
