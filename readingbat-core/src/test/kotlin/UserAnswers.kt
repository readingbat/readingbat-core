/*
 * Copyright © 2020 Paul Ambrose (pambrose@mac.com)
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

package com.github.readingbat

import com.github.readingbat.TestData.GROUP_NAME
import com.github.readingbat.TestData.module
import com.github.readingbat.TestData.readTestContent
import com.github.readingbat.TestSupport.answerFor
import com.github.readingbat.TestSupport.forEachAnswer
import com.github.readingbat.TestSupport.forEachFuncInfo
import com.github.readingbat.TestSupport.forEachGroup
import com.github.readingbat.TestSupport.forEachLanguage
import com.github.readingbat.TestSupport.javaChallenge
import com.github.readingbat.TestSupport.kotlinChallenge
import com.github.readingbat.TestSupport.pythonChallenge
import com.github.readingbat.TestSupport.shouldBe
import com.github.readingbat.TestSupport.shouldNotBe
import io.kotest.core.spec.style.StringSpec
import io.ktor.server.testing.*

class UserAnswers : StringSpec(
  {
    "Test user answers" {
      val testContent = readTestContent()

      withTestApplication({ module(testContent) }) {

        testContent.pythonChallenge(GROUP_NAME, "boolean_array_test") {
          answerFor(0) shouldNotBe "False, False"
          answerFor(0) shouldNotBe "[false, False]"
          answerFor(0) shouldNotBe "[true, False]"

          answerFor(0) shouldBe "[False, False]"
        }

        testContent.javaChallenge(GROUP_NAME, "StringArrayTest1") {
          answerFor(0) shouldNotBe "False, False"
          answerFor(0) shouldNotBe "[false, False]"
          answerFor(0) shouldNotBe "[true, False]"
          answerFor(0) shouldNotBe "[False, False]"
        }

        testContent.kotlinChallenge(GROUP_NAME, "StringArrayKtTest1") {
          answerFor(0) shouldNotBe "False, False"
          answerFor(0) shouldNotBe "[false, False]"
          answerFor(0) shouldNotBe "[true, False]"
          answerFor(0) shouldNotBe "[False, False]"
        }
      }
    }

    "Test correct answers" {
      val testContent = readTestContent()

      withTestApplication({ module(testContent) }) {
        testContent
          .forEachLanguage {
            forEachGroup {
              forEachFuncInfo {
                forEachAnswer { it shouldBe correctAnswers[it.index] }
              }
            }
          }
      }
    }
  })