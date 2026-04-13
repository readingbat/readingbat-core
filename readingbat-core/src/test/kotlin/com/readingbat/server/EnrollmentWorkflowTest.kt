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

import com.pambrose.common.email.Email
import com.readingbat.common.ClassCode
import com.readingbat.common.ClassCode.Companion.DISABLED_CLASS_CODE
import com.readingbat.common.ClassCodeRepository.fetchEnrollees
import com.readingbat.common.OAuthProvider
import com.readingbat.common.User
import com.readingbat.dsl.DataException
import com.readingbat.withTestApp
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class EnrollmentWorkflowTest : StringSpec() {
  init {
    "enrollInClass should update student enrolledClassCode" {
      withTestApp {
        val teacher =
          User.createOAuthUser(
            name = FullName("Teacher EnrollWF"),
            emailVal = Email("teacher-enrollwf@test.com"),
            provider = OAuthProvider.GITHUB,
            providerId = "teacher-enrollwf-001",
            accessToken = "token-teacher-enrollwf",
          )

        val classCode = ClassCode.newClassCode()
        teacher.addClassCode(classCode, "Enrollment Workflow Class")

        val student =
          User.createOAuthUser(
            name = FullName("Student EnrollWF"),
            emailVal = Email("student-enrollwf@test.com"),
            provider = OAuthProvider.GITHUB,
            providerId = "student-enrollwf-001",
            accessToken = "token-student-enrollwf",
          )

        student.enrolledClassCode shouldBe DISABLED_CLASS_CODE

        student.enrollInClass(classCode)

        student.enrolledClassCode shouldBe classCode
        student.isEnrolled(classCode) shouldBe true
      }
    }

    "withdrawFromClass should remove enrollment" {
      withTestApp {
        val teacher =
          User.createOAuthUser(
            name = FullName("Teacher Withdraw"),
            emailVal = Email("teacher-withdraw@test.com"),
            provider = OAuthProvider.GITHUB,
            providerId = "teacher-withdraw-001",
            accessToken = "token-teacher-withdraw",
          )

        val classCode = ClassCode.newClassCode()
        teacher.addClassCode(classCode, "Withdraw Test Class")

        val student =
          User.createOAuthUser(
            name = FullName("Student Withdraw"),
            emailVal = Email("student-withdraw@test.com"),
            provider = OAuthProvider.GITHUB,
            providerId = "student-withdraw-001",
            accessToken = "token-student-withdraw",
          )

        student.enrollInClass(classCode)
        student.isEnrolled(classCode) shouldBe true

        student.withdrawFromClass(classCode)

        student.isEnrolled(classCode) shouldBe false
        student.enrolledClassCode shouldBe DISABLED_CLASS_CODE
      }
    }

    "enrollInClass with invalid class code should throw DataException" {
      withTestApp {
        val student =
          User.createOAuthUser(
            name = FullName("Student Invalid"),
            emailVal = Email("student-invalid@test.com"),
            provider = OAuthProvider.GITHUB,
            providerId = "student-invalid-001",
            accessToken = "token-student-invalid",
          )

        val exception =
          shouldThrow<DataException> {
            student.enrollInClass(ClassCode("nonexistent-code"))
          }

        exception.message shouldContain "Invalid class code"
      }
    }

    "enrollInClass when already enrolled should throw DataException" {
      withTestApp {
        val teacher =
          User.createOAuthUser(
            name = FullName("Teacher DupEnroll"),
            emailVal = Email("teacher-dupenroll@test.com"),
            provider = OAuthProvider.GITHUB,
            providerId = "teacher-dupenroll-001",
            accessToken = "token-teacher-dupenroll",
          )

        val classCode = ClassCode.newClassCode()
        teacher.addClassCode(classCode, "Duplicate Enrollment Class")

        val student =
          User.createOAuthUser(
            name = FullName("Student DupEnroll"),
            emailVal = Email("student-dupenroll@test.com"),
            provider = OAuthProvider.GITHUB,
            providerId = "student-dupenroll-001",
            accessToken = "token-student-dupenroll",
          )

        student.enrollInClass(classCode)

        val exception =
          shouldThrow<DataException> {
            student.enrollInClass(classCode)
          }

        exception.message shouldContain "Already enrolled"
      }
    }

    "enrolling in a new class should remove from previous class" {
      withTestApp {
        val teacher =
          User.createOAuthUser(
            name = FullName("Teacher Switch"),
            emailVal = Email("teacher-switch@test.com"),
            provider = OAuthProvider.GITHUB,
            providerId = "teacher-switch-001",
            accessToken = "token-teacher-switch",
          )

        val classA = ClassCode.newClassCode()
        val classB = ClassCode.newClassCode()
        teacher.addClassCode(classA, "Class A Switch")
        teacher.addClassCode(classB, "Class B Switch")

        val student =
          User.createOAuthUser(
            name = FullName("Student Switch"),
            emailVal = Email("student-switch@test.com"),
            provider = OAuthProvider.GITHUB,
            providerId = "student-switch-001",
            accessToken = "token-student-switch",
          )

        student.enrollInClass(classA)
        student.isEnrolled(classA) shouldBe true

        // Withdraw from A, then enroll in B
        student.withdrawFromClass(classA)
        student.enrollInClass(classB)

        student.isEnrolled(classA) shouldBe false
        student.isEnrolled(classB) shouldBe true
        student.enrolledClassCode shouldBe classB
      }
    }

    "withdrawFromClass with disabled class code should throw DataException" {
      withTestApp {
        val student =
          User.createOAuthUser(
            name = FullName("Student NotEnrolled"),
            emailVal = Email("student-notenrolled@test.com"),
            provider = OAuthProvider.GITHUB,
            providerId = "student-notenrolled-001",
            accessToken = "token-student-notenrolled",
          )

        shouldThrow<DataException> {
          student.withdrawFromClass(DISABLED_CLASS_CODE)
        }
      }
    }

    "fetchEnrollees count should decrease after withdrawal" {
      withTestApp {
        val teacher =
          User.createOAuthUser(
            name = FullName("Teacher EnrolleeCount"),
            emailVal = Email("teacher-enrolleecount@test.com"),
            provider = OAuthProvider.GITHUB,
            providerId = "teacher-enrolleecount-001",
            accessToken = "token-teacher-enrolleecount",
          )

        val classCode = ClassCode.newClassCode()
        teacher.addClassCode(classCode, "Enrollee Count Class")

        val student =
          User.createOAuthUser(
            name = FullName("Student EnrolleeCount"),
            emailVal = Email("student-enrolleecount@test.com"),
            provider = OAuthProvider.GITHUB,
            providerId = "student-enrolleecount-001",
            accessToken = "token-student-enrolleecount",
          )

        student.enrollInClass(classCode)
        classCode.fetchEnrollees() shouldHaveSize 1

        student.withdrawFromClass(classCode)
        classCode.fetchEnrollees() shouldHaveSize 0
      }
    }
  }
}
