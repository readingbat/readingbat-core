/*
 * Copyright © 2021 Paul Ambrose (pambrose@mac.com)
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
import com.github.readingbat.TestData.readTestContent
import com.github.readingbat.kotest.TestSupport.answerFor
import com.github.readingbat.kotest.TestSupport.forEachAnswer
import com.github.readingbat.kotest.TestSupport.forEachChallenge
import com.github.readingbat.kotest.TestSupport.forEachGroup
import com.github.readingbat.kotest.TestSupport.forEachLanguage
import com.github.readingbat.kotest.TestSupport.javaChallenge
import com.github.readingbat.kotest.TestSupport.kotlinChallenge
import com.github.readingbat.kotest.TestSupport.pythonChallenge
import com.github.readingbat.kotest.TestSupport.shouldHaveAnswer
import com.github.readingbat.kotest.TestSupport.shouldNotHaveAnswer
import com.github.readingbat.kotest.TestSupport.testModule
import io.kotest.core.spec.style.StringSpec
import io.ktor.server.testing.*

class UserAnswersTest : StringSpec(
  {
    "Test user answers" {
      testApplication {
        val testContent = readTestContent()

        application { testModule(testContent) }

        testContent.pythonChallenge(GROUP_NAME, "boolean_array_test") {
          answerFor(0) shouldNotHaveAnswer false
          answerFor(0) shouldNotHaveAnswer 2
          answerFor(0) shouldNotHaveAnswer "False, False"
          answerFor(0) shouldNotHaveAnswer "[false, False]"
          answerFor(0) shouldNotHaveAnswer "[true, False]"

          answerFor(0) shouldHaveAnswer "[False, False]"
        }

        testContent.javaChallenge(GROUP_NAME, "StringArrayTest1") {
          answerFor(0) shouldNotHaveAnswer "False, False"
          answerFor(0) shouldNotHaveAnswer "[false, False]"
          answerFor(0) shouldNotHaveAnswer "[true, False]"
          answerFor(0) shouldNotHaveAnswer "[False, False]"
        }

        testContent.kotlinChallenge(GROUP_NAME, "StringArrayKtTest1") {
          answerFor(0) shouldNotHaveAnswer "False, False"
          answerFor(0) shouldNotHaveAnswer "[false, False]"
          answerFor(0) shouldNotHaveAnswer "[true, False]"
          answerFor(0) shouldNotHaveAnswer "[False, False]"
        }
      }
    }

    "Test correct answers" {
      testApplication {
        val testContent = readTestContent()

        application { testModule(testContent) }

        testContent
          .forEachLanguage {
            forEachGroup {
              forEachChallenge {
                forEachAnswer {
                  it shouldHaveAnswer correctAnswers[it.index]
                }
              }
            }
          }
      }
    }
  })