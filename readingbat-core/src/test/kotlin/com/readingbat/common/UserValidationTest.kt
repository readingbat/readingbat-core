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
import com.readingbat.kotest.TestDatabase
import com.readingbat.kotest.TestSupport.initTestProperties
import com.readingbat.kotest.TestSupport.testModule
import com.readingbat.server.FullName
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.server.testing.testApplication

class UserValidationTest : StringSpec() {
  init {
    "null user is not valid" {
      val user: User? = null
      user.isValidUser() shouldBe false
    }

    "null user isNotValidUser returns true" {
      val user: User? = null
      user.isNotValidUser() shouldBe true
    }

    "OAuth user has existsInDbms set to true after creation" {
      initTestProperties()
      TestDatabase.connectAndMigrate()

      TestData.readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            val user =
              User.createOAuthUser(
                name = FullName("Test User"),
                emailVal = Email("validation-test@test.com"),
                provider = "github",
                providerId = "validation-test-001",
                accessToken = "token-validation",
              )

            user.existsInDbms shouldBe true
            user.isValidUser() shouldBe true
            user.isNotValidUser() shouldBe false
          }
        }
    }
  }
}
