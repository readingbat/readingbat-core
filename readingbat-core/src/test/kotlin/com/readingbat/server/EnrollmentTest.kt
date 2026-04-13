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
import com.readingbat.common.OAuthProvider
import com.readingbat.common.User
import com.readingbat.withTestApp
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class EnrollmentTest : StringSpec() {
  init {
    "isEnrolled should only match the enrolled class" {
      withTestApp {
        // Create a teacher who owns both classes
        val teacher =
          User.createOAuthUser(
            name = FullName("Teacher"),
            emailVal = Email("teacher@test.com"),
            provider = OAuthProvider.GITHUB,
            providerId = "teacher-001",
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
            provider = OAuthProvider.GITHUB,
            providerId = "student-001",
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
