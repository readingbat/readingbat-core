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
import com.readingbat.common.ClassCode.Companion.DISABLED_CLASS_CODE
import com.readingbat.common.ClassCodeRepository.deleteClassCode
import com.readingbat.common.ClassCodeRepository.fetchClassDesc
import com.readingbat.common.ClassCodeRepository.fetchClassTeacherId
import com.readingbat.common.ClassCodeRepository.fetchEnrollees
import com.readingbat.common.ClassCodeRepository.isNotValid
import com.readingbat.common.ClassCodeRepository.isValid
import com.readingbat.server.FullName
import com.readingbat.withTestApp
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class ClassManagementTest : StringSpec() {
  init {
    "addClassCode should create a class and classCount should reflect it" {
      withTestApp {
        val teacher =
          User.createOAuthUser(
            name = FullName("Teacher ClassMgmt"),
            emailVal = Email("teacher-classmgmt@test.com"),
            provider = OAuthProvider.GITHUB,
            providerId = "teacher-classmgmt-001",
          )

        teacher.classCount() shouldBe 0

        val classCode = ClassCode.newClassCode()
        teacher.addClassCode(classCode, "Intro to Java")

        teacher.classCount() shouldBe 1
        teacher.classCodes() shouldContain classCode
      }
    }

    "classCodes should list all classes created by teacher" {
      withTestApp {
        val teacher =
          User.createOAuthUser(
            name = FullName("Teacher MultiClass"),
            emailVal = Email("teacher-multiclass@test.com"),
            provider = OAuthProvider.GITHUB,
            providerId = "teacher-multiclass-001",
          )

        val classA = ClassCode.newClassCode()
        val classB = ClassCode.newClassCode()
        val classC = ClassCode.newClassCode()

        teacher.addClassCode(classA, "Class A")
        teacher.addClassCode(classB, "Class B")
        teacher.addClassCode(classC, "Class C")

        val codes = teacher.classCodes()
        codes shouldHaveSize 3
        codes shouldContain classA
        codes shouldContain classB
        codes shouldContain classC
      }
    }

    "isValid and isNotValid should reflect class existence" {
      withTestApp {
        val teacher =
          User.createOAuthUser(
            name = FullName("Teacher Valid"),
            emailVal = Email("teacher-valid@test.com"),
            provider = OAuthProvider.GITHUB,
            providerId = "teacher-valid-001",
          )

        val classCode = ClassCode.newClassCode()
        teacher.addClassCode(classCode, "Valid Class")

        classCode.isValid() shouldBe true
        classCode.isNotValid() shouldBe false

        ClassCode("nonexistent-class-code").isValid() shouldBe false
      }
    }

    "fetchClassDesc should return the class description" {
      withTestApp {
        val teacher =
          User.createOAuthUser(
            name = FullName("Teacher Desc"),
            emailVal = Email("teacher-desc@test.com"),
            provider = OAuthProvider.GITHUB,
            providerId = "teacher-desc-001",
          )

        val classCode = ClassCode.newClassCode()
        teacher.addClassCode(classCode, "Advanced Python")

        classCode.fetchClassDesc() shouldBe "Advanced Python"
        classCode.fetchClassDesc(quoted = true) shouldBe "\"Advanced Python\""
      }
    }

    "fetchClassTeacherId should return the teacher userId" {
      withTestApp {
        val teacher =
          User.createOAuthUser(
            name = FullName("Teacher TeacherId"),
            emailVal = Email("teacher-teacherid@test.com"),
            provider = OAuthProvider.GITHUB,
            providerId = "teacher-teacherid-001",
          )

        val classCode = ClassCode.newClassCode()
        teacher.addClassCode(classCode, "TeacherId Test Class")

        classCode.fetchClassTeacherId() shouldBe teacher.userId
      }
    }

    "fetchEnrollees should list enrolled students" {
      withTestApp {
        val teacher =
          User.createOAuthUser(
            name = FullName("Teacher Enrollees"),
            emailVal = Email("teacher-enrollees@test.com"),
            provider = OAuthProvider.GITHUB,
            providerId = "teacher-enrollees-001",
          )

        val classCode = ClassCode.newClassCode()
        teacher.addClassCode(classCode, "Enrollees Test")

        classCode.fetchEnrollees().shouldBeEmpty()

        val student1 =
          User.createOAuthUser(
            name = FullName("Student One"),
            emailVal = Email("student-one-enrollees@test.com"),
            provider = OAuthProvider.GITHUB,
            providerId = "student-one-enrollees-001",
          )
        val student2 =
          User.createOAuthUser(
            name = FullName("Student Two"),
            emailVal = Email("student-two-enrollees@test.com"),
            provider = OAuthProvider.GITHUB,
            providerId = "student-two-enrollees-001",
          )

        student1.enrollInClass(classCode)
        student2.enrollInClass(classCode)

        val enrollees = classCode.fetchEnrollees()
        enrollees shouldHaveSize 2
      }
    }

    "isUniqueClassDesc should detect duplicate descriptions" {
      withTestApp {
        val teacher =
          User.createOAuthUser(
            name = FullName("Teacher Unique"),
            emailVal = Email("teacher-unique@test.com"),
            provider = OAuthProvider.GITHUB,
            providerId = "teacher-unique-001",
          )

        teacher.isUniqueClassDesc("Unique Class Name") shouldBe true

        val classCode = ClassCode.newClassCode()
        teacher.addClassCode(classCode, "Unique Class Name")

        teacher.isUniqueClassDesc("Unique Class Name") shouldBe false
        teacher.isUniqueClassDesc("Different Class Name") shouldBe true
      }
    }

    "deleteClassCode should remove the class" {
      withTestApp {
        val teacher =
          User.createOAuthUser(
            name = FullName("Teacher DeleteClass"),
            emailVal = Email("teacher-deleteclass@test.com"),
            provider = OAuthProvider.GITHUB,
            providerId = "teacher-deleteclass-001",
          )

        val classCode = ClassCode.newClassCode()
        teacher.addClassCode(classCode, "Doomed Class")

        classCode.isValid() shouldBe true

        transaction {
          classCode.deleteClassCode()
        }

        classCode.isValid() shouldBe false
      }
    }

    "DISABLED_CLASS_CODE fetchEnrollees should return empty list" {
      withTestApp {
        DISABLED_CLASS_CODE.fetchEnrollees().shouldBeEmpty()
      }
    }
  }
}
