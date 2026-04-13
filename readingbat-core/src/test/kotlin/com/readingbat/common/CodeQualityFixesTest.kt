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
import com.pambrose.common.exposed.readonlyTx
import com.pambrose.common.exposed.upsert
import com.readingbat.common.ClassCode.Companion.DISABLED_CLASS_CODE
import com.readingbat.common.ClassCodeRepository.fetchEnrollees
import com.readingbat.common.User.Companion.emailCache
import com.readingbat.common.User.Companion.fetchEmailFromCache
import com.readingbat.common.User.Companion.fetchUserDbmsIdFromCache
import com.readingbat.common.User.Companion.queryActiveTeachingClassCode
import com.readingbat.common.User.Companion.queryPreviousTeacherClassCode
import com.readingbat.common.User.Companion.userExists
import com.readingbat.common.User.Companion.userIdCache
import com.readingbat.posts.LikeDislike
import com.readingbat.server.FullName
import com.readingbat.server.UserAnswerHistoryTable
import com.readingbat.server.UserChallengeInfoTable
import com.readingbat.server.UsersTable
import com.readingbat.server.userAnswerHistoryIndex
import com.readingbat.server.userChallengeInfoIndex
import com.readingbat.withTestApp
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlinx.html.Entities
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class CodeQualityFixesTest : StringSpec() {
  init {
    // Issue 1: Deduplicated query helpers
    "queryActiveTeachingClassCode should return DISABLED_CLASS_CODE for null user" {
      withTestApp {
        queryActiveTeachingClassCode(null) shouldBe DISABLED_CLASS_CODE
      }
    }

    "queryPreviousTeacherClassCode should return DISABLED_CLASS_CODE for null user" {
      withTestApp {
        queryPreviousTeacherClassCode(null) shouldBe DISABLED_CLASS_CODE
      }
    }

    "deduplicated query helpers should both delegate to the same logic" {
      // Both functions share the same queryClassCode implementation
      // Verify they both handle null user identically
      withTestApp {
        queryActiveTeachingClassCode(null) shouldBe DISABLED_CLASS_CODE
        queryPreviousTeacherClassCode(null) shouldBe DISABLED_CLASS_CODE
      }
    }

    // Issue 3: LikeDislike enum
    "LikeDislike enum should have correct values" {
      LikeDislike.NONE.value shouldBe 0.toShort()
      LikeDislike.LIKE.value shouldBe 1.toShort()
      LikeDislike.DISLIKE.value shouldBe 2.toShort()
    }

    "likeDislikeEmoji should map LikeDislike values correctly" {
      withTestApp {
        val user =
          User.createOAuthUser(
            name = FullName("LikeDislike Enum User"),
            emailVal = Email("likedislike-enum@test.com"),
            provider = OAuthProvider.GITHUB,
            providerId = "likedislike-enum-001",
            accessToken = "token-likedislike-enum",
          )

        user.likeDislikeEmoji(LikeDislike.LIKE.value.toInt()) shouldBe Endpoints.THUMBS_UP
        user.likeDislikeEmoji(LikeDislike.DISLIKE.value.toInt()) shouldBe Endpoints.THUMBS_DOWN
        user.likeDislikeEmoji(LikeDislike.NONE.value.toInt()) shouldBe Entities.nbsp.text
      }
    }

    "likeDislikes query should use LikeDislike enum values" {
      withTestApp {
        val user =
          User.createOAuthUser(
            name = FullName("LikeDislike Query User"),
            emailVal = Email("likedislike-query@test.com"),
            provider = OAuthProvider.GITHUB,
            providerId = "likedislike-query-001",
            accessToken = "token-likedislike-query",
          )

        // Initially empty
        user.likeDislikes().shouldBeEmpty()

        // Insert a like using enum value
        transaction {
          with(UserChallengeInfoTable) {
            upsert(conflictIndex = userChallengeInfoIndex) { row ->
              row[userRef] = user.userDbmsId
              row[md5] = "likedislike-query-md5"
              row[updated] = nowInstant()
              row[likeDislike] = LikeDislike.LIKE.value
            }
          }
        }

        user.likeDislikes() shouldHaveSize 1
      }
    }

    // Issue 4: OAuthProvider enum
    "OAuthProvider should have correct provider names" {
      OAuthProvider.GITHUB.providerName shouldBe "github"
      OAuthProvider.GOOGLE.providerName shouldBe "google"
    }

    "createOAuthUser should accept OAuthProvider enum" {
      withTestApp {
        val user =
          User.createOAuthUser(
            name = FullName("OAuthProvider Enum User"),
            emailVal = Email("oauthprovider-enum@test.com"),
            provider = OAuthProvider.GITHUB,
            providerId = "oauthprovider-enum-001",
            accessToken = "token-oauthprovider-enum",
          )

        user.existsInDbms shouldBe true
        userExists(user.userId) shouldBe true
      }
    }

    // Issue 7: Bulk update in unenrollEnrolleesClassCode
    "bulk unenrollment should reset all enrollees in one operation" {
      withTestApp {
        val teacher =
          User.createOAuthUser(
            name = FullName("Bulk Teacher"),
            emailVal = Email("bulk-teacher@test.com"),
            provider = OAuthProvider.GITHUB,
            providerId = "bulk-teacher-001",
            accessToken = "token-bulk-teacher",
          )

        val classCode = ClassCode.newClassCode()
        teacher.addClassCode(classCode, "Bulk Unenroll Class")

        // Enroll multiple students
        val students =
          (1..3).map { i ->
            User.createOAuthUser(
              name = FullName("Bulk Student $i"),
              emailVal = Email("bulk-student-$i@test.com"),
              provider = OAuthProvider.GITHUB,
              providerId = "bulk-student-$i-001",
              accessToken = "token-bulk-student-$i",
            ).also { it.enrollInClass(classCode) }
          }

        // Verify all enrolled
        classCode.fetchEnrollees() shouldHaveSize 3
        students.forEach { it.isEnrolled(classCode) shouldBe true }

        // Bulk unenroll
        teacher.unenrollEnrolleesClassCode(classCode, students)

        // Verify all enrollees have DISABLED_CLASS_CODE
        students.forEach { student ->
          readonlyTx {
            UsersTable
              .select(UsersTable.enrolledClassCode)
              .where { UsersTable.id eq student.userDbmsId }
              .single()
              .let { row ->
                row[UsersTable.enrolledClassCode] shouldBe DISABLED_CLASS_CODE.classCode
              }
          }
        }
      }
    }

    "bulk unenrollment with empty list should be a no-op" {
      withTestApp {
        val teacher =
          User.createOAuthUser(
            name = FullName("Empty Bulk Teacher"),
            emailVal = Email("empty-bulk-teacher@test.com"),
            provider = OAuthProvider.GITHUB,
            providerId = "empty-bulk-teacher-001",
            accessToken = "token-empty-bulk-teacher",
          )

        val classCode = ClassCode.newClassCode()
        teacher.addClassCode(classCode, "Empty Bulk Class")

        // Should not throw with empty list
        teacher.unenrollEnrolleesClassCode(classCode, emptyList())
      }
    }

    // Issues 8+9: Cache invalidation and deleteUser COUNT queries
    "deleteUser should invalidate caches" {
      withTestApp {
        val user =
          User.createOAuthUser(
            name = FullName("Cache Invalidation User"),
            emailVal = Email("cache-invalidation@test.com"),
            provider = OAuthProvider.GITHUB,
            providerId = "cache-invalidation-001",
            accessToken = "token-cache-invalidation",
          )

        val userId = user.userId

        // Populate caches
        fetchUserDbmsIdFromCache(userId) shouldBeGreaterThan 0
        fetchEmailFromCache(userId) shouldBe Email("cache-invalidation@test.com")

        // Verify caches are populated
        userIdCache.containsKey(userId) shouldBe true
        emailCache.containsKey(userId) shouldBe true

        // Delete user
        user.deleteUser()

        // Verify caches are invalidated
        userIdCache.containsKey(userId) shouldBe false
        emailCache.containsKey(userId) shouldBe false
      }
    }

    "deleteUser with challenge data should execute COUNT queries without error" {
      withTestApp {
        val user =
          User.createOAuthUser(
            name = FullName("Delete Count User"),
            emailVal = Email("delete-count@test.com"),
            provider = OAuthProvider.GITHUB,
            providerId = "delete-count-001",
            accessToken = "token-delete-count",
          )

        // Add some challenge data so COUNT queries have rows to count
        transaction {
          with(UserChallengeInfoTable) {
            upsert(conflictIndex = userChallengeInfoIndex) { row ->
              row[userRef] = user.userDbmsId
              row[md5] = "delete-count-md5"
              row[updated] = nowInstant()
              row[allCorrect] = true
              row[likeDislike] = LikeDislike.LIKE.value
              row[answersJson] = "{}"
            }
          }
          with(UserAnswerHistoryTable) {
            upsert(conflictIndex = userAnswerHistoryIndex) { row ->
              row[userRef] = user.userDbmsId
              row[md5] = "delete-count-md5"
              row[invocation] = "test(1)"
              row[updated] = nowInstant()
              row[correct] = true
              row[incorrectAttempts] = 0
              row[historyJson] = "[]"
            }
          }
        }

        val userId = user.userId

        // deleteUser should succeed with COUNT queries and cascade deletes
        user.deleteUser()

        userExists(userId) shouldBe false
      }
    }

    "deleteUser should cascade and unenroll students via bulk update" {
      withTestApp {
        val teacher =
          User.createOAuthUser(
            name = FullName("Cascade Teacher"),
            emailVal = Email("cascade-teacher@test.com"),
            provider = OAuthProvider.GITHUB,
            providerId = "cascade-teacher-001",
            accessToken = "token-cascade-teacher",
          )

        val classCode = ClassCode.newClassCode()
        teacher.addClassCode(classCode, "Cascade Class")

        val student1 =
          User.createOAuthUser(
            name = FullName("Cascade Student 1"),
            emailVal = Email("cascade-student-1@test.com"),
            provider = OAuthProvider.GITHUB,
            providerId = "cascade-student-1-001",
            accessToken = "token-cascade-student-1",
          )
        val student2 =
          User.createOAuthUser(
            name = FullName("Cascade Student 2"),
            emailVal = Email("cascade-student-2@test.com"),
            provider = OAuthProvider.GITHUB,
            providerId = "cascade-student-2-001",
            accessToken = "token-cascade-student-2",
          )

        student1.enrollInClass(classCode)
        student2.enrollInClass(classCode)

        // Delete teacher — should bulk unenroll both students
        teacher.deleteUser()

        // Students should still exist but with disabled class code
        userExists(student1.userId) shouldBe true
        userExists(student2.userId) shouldBe true

        readonlyTx {
          UsersTable
            .select(UsersTable.enrolledClassCode)
            .where { UsersTable.id eq student1.userDbmsId }
            .single()[UsersTable.enrolledClassCode] shouldBe DISABLED_CLASS_CODE.classCode
        }
        readonlyTx {
          UsersTable
            .select(UsersTable.enrolledClassCode)
            .where { UsersTable.id eq student2.userDbmsId }
            .single()[UsersTable.enrolledClassCode] shouldBe DISABLED_CLASS_CODE.classCode
        }
      }
    }

    // Issue 5: withTestApp helper works correctly (meta-test)
    "withTestApp should initialize database and application" {
      withTestApp {
        // If we get here, TestDatabase is connected, Flyway ran, and the Ktor app is configured
        val user =
          User.createOAuthUser(
            name = FullName("WithTestApp User"),
            emailVal = Email("withtestapp@test.com"),
            provider = OAuthProvider.GITHUB,
            providerId = "withtestapp-001",
            accessToken = "token-withtestapp",
          )
        user.existsInDbms shouldBe true
      }
    }
  }
}
