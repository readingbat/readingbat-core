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
import com.readingbat.TestData
import com.readingbat.common.ClassCodeRepository.isNotValid
import com.readingbat.common.User.Companion.queryUserByEmail
import com.readingbat.common.User.Companion.userExists
import com.readingbat.kotest.TestDatabase
import com.readingbat.kotest.TestSupport.initTestProperties
import com.readingbat.kotest.TestSupport.testModule
import com.readingbat.server.FullName
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty
import io.ktor.server.testing.testApplication

class UserLifecycleTest : StringSpec() {
  init {
    "createOAuthUser should persist user with correct fields" {
      initTestProperties()
      TestDatabase.connectAndMigrate()

      TestData.readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            val user =
              User.createOAuthUser(
                name = FullName("Alice Smith"),
                emailVal = Email("alice-lifecycle@test.com"),
                provider = "github",
                providerId = "alice-lifecycle-001",
                accessToken = "token-alice-lifecycle",
                avatarUrlVal = "https://example.com/avatar.png",
              )

            user.existsInDbms shouldBe true
            user.userId.shouldNotBeEmpty()
            user.userDbmsId shouldBeGreaterThan 0

            // Verify user can be found by email in the database
            queryUserByEmail(Email("alice-lifecycle@test.com")).shouldNotBeNull()

            // Verify userExists works
            userExists(user.userId) shouldBe true
          }
        }
    }

    "userExists should return true for existing user and false for unknown" {
      initTestProperties()
      TestDatabase.connectAndMigrate()

      TestData.readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            val user =
              User.createOAuthUser(
                name = FullName("Bob Exists"),
                emailVal = Email("bob-exists@test.com"),
                provider = "github",
                providerId = "bob-exists-001",
                accessToken = "token-bob-exists",
              )

            userExists(user.userId) shouldBe true
            userExists("nonexistent-user-id-12345") shouldBe false
          }
        }
    }

    "queryUserByEmail should find existing user and return null for unknown" {
      initTestProperties()
      TestDatabase.connectAndMigrate()

      TestData.readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            User.createOAuthUser(
              name = FullName("Carol Query"),
              emailVal = Email("carol-query@test.com"),
              provider = "github",
              providerId = "carol-query-001",
              accessToken = "token-carol-query",
            )

            queryUserByEmail(Email("carol-query@test.com")).shouldNotBeNull()
            queryUserByEmail(Email("nonexistent@test.com")).shouldBeNull()
          }
        }
    }

    "isInDbms should return true after user creation" {
      initTestProperties()
      TestDatabase.connectAndMigrate()

      TestData.readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            val user =
              User.createOAuthUser(
                name = FullName("Dave InDbms"),
                emailVal = Email("dave-indbms@test.com"),
                provider = "github",
                providerId = "dave-indbms-001",
                accessToken = "token-dave-indbms",
              )

            user.isInDbms() shouldBe true
          }
        }
    }

    "deleteUser should remove user and cascade to related records" {
      initTestProperties()
      TestDatabase.connectAndMigrate()

      TestData.readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            val user =
              User.createOAuthUser(
                name = FullName("Eve Delete"),
                emailVal = Email("eve-delete@test.com"),
                provider = "github",
                providerId = "eve-delete-001",
                accessToken = "token-eve-delete",
              )

            val userId = user.userId
            userExists(userId) shouldBe true

            user.deleteUser()

            userExists(userId) shouldBe false
            queryUserByEmail(Email("eve-delete@test.com")).shouldBeNull()
          }
        }
    }

    "deleteUser should cascade to classes and unenroll students" {
      initTestProperties()
      TestDatabase.connectAndMigrate()

      TestData.readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            // Create a teacher with a class
            val teacher =
              User.createOAuthUser(
                name = FullName("Teacher Cascade"),
                emailVal = Email("teacher-cascade@test.com"),
                provider = "github",
                providerId = "teacher-cascade-001",
                accessToken = "token-teacher-cascade",
              )

            val classCode = ClassCode.newClassCode()
            teacher.addClassCode(classCode, "Cascade Test Class")

            // Create and enroll a student
            val student =
              User.createOAuthUser(
                name = FullName("Student Cascade"),
                emailVal = Email("student-cascade@test.com"),
                provider = "github",
                providerId = "student-cascade-001",
                accessToken = "token-student-cascade",
              )

            student.enrollInClass(classCode)
            student.isEnrolled(classCode) shouldBe true

            // Delete the teacher - should cascade to classes and unenroll students
            teacher.deleteUser()

            // Class should no longer be valid
            classCode.isNotValid() shouldBe true
          }
        }
    }
  }
}
