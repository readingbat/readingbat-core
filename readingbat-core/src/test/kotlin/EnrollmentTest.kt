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
import com.github.readingbat.common.ClassCode
import com.github.readingbat.common.User
import com.github.readingbat.kotest.TestDatabase
import com.github.readingbat.kotest.TestSupport.initTestProperties
import com.github.readingbat.kotest.TestSupport.testModule
import com.github.readingbat.server.FullName
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.server.testing.testApplication

class EnrollmentTest : StringSpec() {
  init {
    "isEnrolled should only match the enrolled class" {
      initTestProperties()
      TestDatabase.connectAndMigrate()

      readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            // Create a teacher who owns both classes
            val teacher =
              User.createOAuthUser(
                name = FullName("Teacher"),
                emailVal = Email("teacher@test.com"),
                provider = "github",
                providerId = "teacher-001",
                accessToken = "token-teacher",
              )

            // Create two distinct classes
            val classA = ClassCode.newClassCode()
            val classB = ClassCode.newClassCode()
            teacher.addClassCode(classA, "Class A")
            teacher.addClassCode(classB, "Class B")

            // Create a student
            val student =
              User.createOAuthUser(
                name = FullName("Student"),
                emailVal = Email("student@test.com"),
                provider = "github",
                providerId = "student-001",
                accessToken = "token-student",
              )

            // Enroll student in class A only
            student.enrollInClass(classA)

            // Verify enrollment: student is in classA but NOT in classB
            student.isEnrolled(classA) shouldBe true
            student.isEnrolled(classB) shouldBe false
          }
        }
    }
  }
}
