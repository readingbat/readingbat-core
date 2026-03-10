import TestData.readTestContent
import com.github.pambrose.common.email.Email
import com.github.readingbat.common.User
import com.github.readingbat.kotest.TestDatabase
import com.github.readingbat.kotest.TestSupport.initTestProperties
import com.github.readingbat.kotest.TestSupport.testModule
import com.github.readingbat.server.FullName
import com.github.readingbat.server.Invocation
import com.github.readingbat.server.UserAnswerHistoryTable
import com.github.readingbat.server.UserChallengeInfoTable
import com.github.readingbat.server.userAnswerHistoryIndex
import com.github.readingbat.server.userChallengeInfoIndex
import com.pambrose.common.exposed.upsert
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.joda.time.DateTime
import org.joda.time.DateTimeZone

class AnswerHistoryPersistenceTest : StringSpec() {
  init {
    "answerHistory should return default for missing entry" {
      initTestProperties()
      TestDatabase.connectAndMigrate()

      readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            val user =
              User.createOAuthUser(
                name = FullName("User HistDefault"),
                emailVal = Email("user-histdefault@test.com"),
                provider = "github",
                providerId = "user-histdefault-001",
                accessToken = "token-user-histdefault",
              )

            val history = user.answerHistory("nonexistent-md5", Invocation("someInvocation"))
            history.correct shouldBe false
            history.incorrectAttempts shouldBe 0
            history.answers.shouldBeEmpty()
          }
        }
    }

    "upsert and read back answer history" {
      initTestProperties()
      TestDatabase.connectAndMigrate()

      readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            val user =
              User.createOAuthUser(
                name = FullName("User HistPersist"),
                emailVal = Email("user-histpersist@test.com"),
                provider = "github",
                providerId = "user-histpersist-001",
                accessToken = "token-user-histpersist",
              )

            val md5 = "test-md5-persist"
            val invocation = Invocation("testInvoke(1)")

            // Write answer history
            transaction {
              with(UserAnswerHistoryTable) {
                upsert(conflictIndex = userAnswerHistoryIndex) { row ->
                  row[userRef] = user.userDbmsId
                  row[UserAnswerHistoryTable.md5] = md5
                  row[updated] = DateTime.now(DateTimeZone.UTC)
                  row[UserAnswerHistoryTable.invocation] = invocation.value
                  row[correct] = true
                  row[incorrectAttempts] = 2
                  row[historyJson] = Json.encodeToString(listOf("wrong1", "wrong2", "right"))
                }
              }
            }

            // Read it back
            val history = user.answerHistory(md5, invocation)
            history.correct shouldBe true
            history.incorrectAttempts shouldBe 2
            history.answers shouldContainExactly listOf("wrong1", "wrong2", "right")
          }
        }
    }

    "historyExists should detect persisted history entries" {
      initTestProperties()
      TestDatabase.connectAndMigrate()

      readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            val user =
              User.createOAuthUser(
                name = FullName("User HistExists"),
                emailVal = Email("user-histexists@test.com"),
                provider = "github",
                providerId = "user-histexists-001",
                accessToken = "token-user-histexists",
              )

            val md5 = "test-md5-exists"
            val invocation = Invocation("existsInvoke()")

            user.historyExists(md5, invocation) shouldBe false

            transaction {
              with(UserAnswerHistoryTable) {
                upsert(conflictIndex = userAnswerHistoryIndex) { row ->
                  row[userRef] = user.userDbmsId
                  row[UserAnswerHistoryTable.md5] = md5
                  row[updated] = DateTime.now(DateTimeZone.UTC)
                  row[UserAnswerHistoryTable.invocation] = invocation.value
                  row[correct] = false
                  row[incorrectAttempts] = 0
                  row[historyJson] = Json.encodeToString(emptyList<String>())
                }
              }
            }

            user.historyExists(md5, invocation) shouldBe true
          }
        }
    }

    "answerHistoryBulk should return multiple histories by md5" {
      initTestProperties()
      TestDatabase.connectAndMigrate()

      readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            val user =
              User.createOAuthUser(
                name = FullName("User BulkHist"),
                emailVal = Email("user-bulkhist@test.com"),
                provider = "github",
                providerId = "user-bulkhist-001",
                accessToken = "token-user-bulkhist",
              )

            val md5A = "bulk-md5-a"
            val md5B = "bulk-md5-b"
            val md5Missing = "bulk-md5-missing"

            // Insert two history entries
            transaction {
              with(UserAnswerHistoryTable) {
                upsert(conflictIndex = userAnswerHistoryIndex) { row ->
                  row[userRef] = user.userDbmsId
                  row[md5] = md5A
                  row[updated] = DateTime.now(DateTimeZone.UTC)
                  row[invocation] = "invokeA()"
                  row[correct] = true
                  row[incorrectAttempts] = 0
                  row[historyJson] = Json.encodeToString(listOf("answerA"))
                }
                upsert(conflictIndex = userAnswerHistoryIndex) { row ->
                  row[userRef] = user.userDbmsId
                  row[md5] = md5B
                  row[updated] = DateTime.now(DateTimeZone.UTC)
                  row[invocation] = "invokeB()"
                  row[correct] = false
                  row[incorrectAttempts] = 3
                  row[historyJson] = Json.encodeToString(listOf("wrong1", "wrong2", "wrong3"))
                }
              }
            }

            val bulk = user.answerHistoryBulk(listOf(md5A, md5B, md5Missing))
            bulk.size shouldBe 2
            bulk[md5A]!!.correct shouldBe true
            bulk[md5B]!!.incorrectAttempts shouldBe 3
            bulk[md5B]!!.answers shouldHaveSize 3
            bulk[md5Missing] shouldBe null
          }
        }
    }

    "upsert should update existing answer history on conflict" {
      initTestProperties()
      TestDatabase.connectAndMigrate()

      readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            val user =
              User.createOAuthUser(
                name = FullName("User UpsertHist"),
                emailVal = Email("user-upserThist@test.com"),
                provider = "github",
                providerId = "user-upserThist-001",
                accessToken = "token-user-upserThist",
              )

            val md5 = "upsert-md5"
            val invocation = Invocation("upsertInvoke()")

            // Initial insert
            transaction {
              with(UserAnswerHistoryTable) {
                upsert(conflictIndex = userAnswerHistoryIndex) { row ->
                  row[userRef] = user.userDbmsId
                  row[UserAnswerHistoryTable.md5] = md5
                  row[updated] = DateTime.now(DateTimeZone.UTC)
                  row[UserAnswerHistoryTable.invocation] = invocation.value
                  row[correct] = false
                  row[incorrectAttempts] = 1
                  row[historyJson] = Json.encodeToString(listOf("wrong"))
                }
              }
            }

            user.answerHistory(md5, invocation).incorrectAttempts shouldBe 1

            // Upsert with updated data
            transaction {
              with(UserAnswerHistoryTable) {
                upsert(conflictIndex = userAnswerHistoryIndex) { row ->
                  row[userRef] = user.userDbmsId
                  row[UserAnswerHistoryTable.md5] = md5
                  row[updated] = DateTime.now(DateTimeZone.UTC)
                  row[UserAnswerHistoryTable.invocation] = invocation.value
                  row[correct] = true
                  row[incorrectAttempts] = 1
                  row[historyJson] = Json.encodeToString(listOf("wrong", "right"))
                }
              }
            }

            val updated = user.answerHistory(md5, invocation)
            updated.correct shouldBe true
            updated.incorrectAttempts shouldBe 1
            updated.answers shouldContainExactly listOf("wrong", "right")
          }
        }
    }

    "challenge info upsert should track allCorrect and answersJson" {
      initTestProperties()
      TestDatabase.connectAndMigrate()

      readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            val user =
              User.createOAuthUser(
                name = FullName("User ChalInfo"),
                emailVal = Email("user-chalinfo@test.com"),
                provider = "github",
                providerId = "user-chalinfo-001",
                accessToken = "token-user-chalinfo",
              )

            val challengeMd5 = "challenge-info-md5"

            // Insert challenge info as incomplete
            transaction {
              with(UserChallengeInfoTable) {
                upsert(conflictIndex = userChallengeInfoIndex) { row ->
                  row[userRef] = user.userDbmsId
                  row[md5] = challengeMd5
                  row[updated] = DateTime.now(DateTimeZone.UTC)
                  row[allCorrect] = false
                  row[answersJson] = """{"invoke1":"wrong"}"""
                }
              }
            }

            user.correctAnswers().shouldBeEmpty()

            // Update to all correct
            transaction {
              with(UserChallengeInfoTable) {
                upsert(conflictIndex = userChallengeInfoIndex) { row ->
                  row[userRef] = user.userDbmsId
                  row[md5] = challengeMd5
                  row[updated] = DateTime.now(DateTimeZone.UTC)
                  row[allCorrect] = true
                  row[answersJson] = """{"invoke1":"right"}"""
                }
              }
            }

            user.correctAnswers() shouldHaveSize 1
          }
        }
    }

    "challenges and invocations should track user activity" {
      initTestProperties()
      TestDatabase.connectAndMigrate()

      readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            val user =
              User.createOAuthUser(
                name = FullName("User Activity"),
                emailVal = Email("user-activity@test.com"),
                provider = "github",
                providerId = "user-activity-001",
                accessToken = "token-user-activity",
              )

            user.challenges().shouldBeEmpty()
            user.invocations().shouldBeEmpty()

            // Add challenge info entry
            transaction {
              with(UserChallengeInfoTable) {
                upsert(conflictIndex = userChallengeInfoIndex) { row ->
                  row[userRef] = user.userDbmsId
                  row[md5] = "activity-challenge-md5"
                  row[updated] = DateTime.now(DateTimeZone.UTC)
                  row[allCorrect] = false
                  row[answersJson] = "{}"
                }
              }
            }

            // Add answer history entry
            transaction {
              with(UserAnswerHistoryTable) {
                upsert(conflictIndex = userAnswerHistoryIndex) { row ->
                  row[userRef] = user.userDbmsId
                  row[md5] = "activity-invocation-md5"
                  row[updated] = DateTime.now(DateTimeZone.UTC)
                  row[invocation] = "test()"
                  row[correct] = false
                  row[incorrectAttempts] = 0
                  row[historyJson] = "[]"
                }
              }
            }

            user.challenges() shouldHaveSize 1
            user.invocations() shouldHaveSize 1
          }
        }
    }
  }
}
