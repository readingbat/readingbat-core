import TestData.readTestContent
import com.github.pambrose.common.email.Email
import com.github.readingbat.common.User
import com.github.readingbat.kotest.TestDatabase
import com.github.readingbat.kotest.TestSupport.initTestProperties
import com.github.readingbat.kotest.TestSupport.testModule
import com.github.readingbat.server.FullName
import com.github.readingbat.server.UserChallengeInfoTable
import com.github.readingbat.server.userChallengeInfoIndex
import com.pambrose.common.exposed.upsert
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.joda.time.DateTime
import org.joda.time.DateTimeZone

class LikeDislikePersistenceTest : StringSpec() {
  init {
    "likeDislike should default to 0 for challenges with no entry" {
      initTestProperties()
      TestDatabase.connectAndMigrate()

      readTestContent()
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

      readTestContent()
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

      readTestContent()
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

      readTestContent()
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

            user.likeDislikeEmoji(1) shouldBe com.github.readingbat.common.Endpoints.THUMBS_UP
            user.likeDislikeEmoji(2) shouldBe com.github.readingbat.common.Endpoints.THUMBS_DOWN
            user.likeDislikeEmoji(0) shouldBe kotlinx.html.Entities.nbsp.text
          }
        }
    }

    "updating like to dislike should overwrite the value" {
      initTestProperties()
      TestDatabase.connectAndMigrate()

      readTestContent()
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
