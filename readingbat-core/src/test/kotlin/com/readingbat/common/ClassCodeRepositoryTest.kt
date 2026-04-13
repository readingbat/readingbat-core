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
import com.readingbat.common.ClassCodeRepository.addEnrollee
import com.readingbat.common.ClassCodeRepository.deleteClassCode
import com.readingbat.common.ClassCodeRepository.fetchClassDesc
import com.readingbat.common.ClassCodeRepository.fetchClassTeacherId
import com.readingbat.common.ClassCodeRepository.fetchEnrollees
import com.readingbat.common.ClassCodeRepository.isNotValid
import com.readingbat.common.ClassCodeRepository.isValid
import com.readingbat.common.ClassCodeRepository.removeEnrollee
import com.readingbat.common.ClassCodeRepository.toDisplayString
import com.readingbat.server.FullName
import com.readingbat.withTestApp
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeEmpty
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class ClassCodeRepositoryTest : StringSpec() {
  init {
    "ClassCode can be instantiated without database" {
      val classCode = ClassCode("test-code")
      classCode.classCode shouldBe "test-code"
      classCode.isEnabled shouldBe true
      classCode.isNotEnabled shouldBe false
      classCode.displayedValue shouldBe "test-code"
    }

    "DISABLED_CLASS_CODE properties work without database" {
      DISABLED_CLASS_CODE.isNotEnabled shouldBe true
      DISABLED_CLASS_CODE.isEnabled shouldBe false
      DISABLED_CLASS_CODE.displayedValue shouldBe ""
    }

    "isValid returns false for nonexistent class code" {
      withTestApp {
        ClassCode("nonexistent-repo-test").isValid() shouldBe false
        ClassCode("nonexistent-repo-test").isNotValid() shouldBe true
      }
    }

    "isValid returns true for existing class code" {
      withTestApp {
        val teacher =
          User.createOAuthUser(
            name = FullName("Teacher RepoValid"),
            emailVal = Email("teacher-repovalid@test.com"),
            provider = OAuthProvider.GITHUB,
            providerId = "teacher-repovalid-001",
          )

        val classCode = ClassCode.newClassCode()
        teacher.addClassCode(classCode, "Repo Valid Class")

        classCode.isValid() shouldBe true
        classCode.isNotValid() shouldBe false
      }
    }

    "fetchClassDesc returns description via repository extension" {
      withTestApp {
        val teacher =
          User.createOAuthUser(
            name = FullName("Teacher RepoDesc"),
            emailVal = Email("teacher-repodesc@test.com"),
            provider = OAuthProvider.GITHUB,
            providerId = "teacher-repodesc-001",
          )

        val classCode = ClassCode.newClassCode()
        teacher.addClassCode(classCode, "Repository Test Class")

        classCode.fetchClassDesc() shouldBe "Repository Test Class"
        classCode.fetchClassDesc(quoted = true) shouldBe "\"Repository Test Class\""
      }
    }

    "toDisplayString returns formatted description with class code" {
      withTestApp {
        val teacher =
          User.createOAuthUser(
            name = FullName("Teacher RepoDisplay"),
            emailVal = Email("teacher-repodisplay@test.com"),
            provider = OAuthProvider.GITHUB,
            providerId = "teacher-repodisplay-001",
          )

        val classCode = ClassCode.newClassCode()
        teacher.addClassCode(classCode, "Display Test")

        val display = classCode.toDisplayString()
        display shouldContain "Display Test"
        display shouldContain classCode.classCode
      }
    }

    "fetchClassTeacherId returns teacher userId" {
      withTestApp {
        val teacher =
          User.createOAuthUser(
            name = FullName("Teacher RepoTeacher"),
            emailVal = Email("teacher-repoteacher@test.com"),
            provider = OAuthProvider.GITHUB,
            providerId = "teacher-repoteacher-001",
          )

        val classCode = ClassCode.newClassCode()
        teacher.addClassCode(classCode, "Teacher Id Test")

        classCode.fetchClassTeacherId() shouldBe teacher.userId
        classCode.fetchClassTeacherId().shouldNotBeEmpty()
      }
    }

    "addEnrollee and removeEnrollee work via repository extensions" {
      withTestApp {
        val teacher =
          User.createOAuthUser(
            name = FullName("Teacher RepoEnrollee"),
            emailVal = Email("teacher-repoenrollee@test.com"),
            provider = OAuthProvider.GITHUB,
            providerId = "teacher-repoenrollee-001",
          )

        val classCode = ClassCode.newClassCode()
        teacher.addClassCode(classCode, "Enrollee Repo Test")

        val student =
          User.createOAuthUser(
            name = FullName("Student RepoEnrollee"),
            emailVal = Email("student-repoenrollee@test.com"),
            provider = OAuthProvider.GITHUB,
            providerId = "student-repoenrollee-001",
          )

        classCode.fetchEnrollees().shouldBeEmpty()

        transaction {
          classCode.addEnrollee(student)
        }

        classCode.fetchEnrollees() shouldHaveSize 1

        transaction {
          classCode.removeEnrollee(student)
        }

        classCode.fetchEnrollees().shouldBeEmpty()
      }
    }

    "deleteClassCode removes the class via repository extension" {
      withTestApp {
        val teacher =
          User.createOAuthUser(
            name = FullName("Teacher RepoDelete"),
            emailVal = Email("teacher-repodelete@test.com"),
            provider = OAuthProvider.GITHUB,
            providerId = "teacher-repodelete-001",
          )

        val classCode = ClassCode.newClassCode()
        teacher.addClassCode(classCode, "Delete Repo Test")

        classCode.isValid() shouldBe true

        transaction {
          classCode.deleteClassCode()
        }

        classCode.isValid() shouldBe false
      }
    }

    "DISABLED_CLASS_CODE fetchEnrollees returns empty list" {
      withTestApp {
        DISABLED_CLASS_CODE.fetchEnrollees().shouldBeEmpty()
      }
    }
  }
}
