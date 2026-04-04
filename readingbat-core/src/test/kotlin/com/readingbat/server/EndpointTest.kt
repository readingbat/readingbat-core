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

import com.pambrose.common.util.pathOf
import com.readingbat.TestData
import com.readingbat.common.Constants.CHALLENGE_NOT_FOUND
import com.readingbat.common.Endpoints.ABOUT_ENDPOINT
import com.readingbat.common.Endpoints.HELP_ENDPOINT
import com.readingbat.common.Endpoints.PRIVACY_POLICY_ENDPOINT
import com.readingbat.common.Endpoints.TOS_ENDPOINT
import com.readingbat.dsl.LanguageType.Python
import com.readingbat.kotest.TestSupport.answerAllWith
import com.readingbat.kotest.TestSupport.answerAllWithCorrectAnswer
import com.readingbat.kotest.TestSupport.forEachChallenge
import com.readingbat.kotest.TestSupport.forEachGroup
import com.readingbat.kotest.TestSupport.forEachLanguage
import com.readingbat.kotest.TestSupport.initTestProperties
import com.readingbat.kotest.TestSupport.testModule
import com.readingbat.posts.AnswerStatus
import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeBlank
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.testing.testApplication

class EndpointTest : StringSpec() {
  init {
    "Simple endpoint tests" {
      initTestProperties()
      TestData.readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            client.apply {
              get("/").also { it shouldHaveStatus OK }
              get(ABOUT_ENDPOINT).also { it shouldHaveStatus OK }
              get(HELP_ENDPOINT).also { it shouldHaveStatus OK }
              get(PRIVACY_POLICY_ENDPOINT).also { it shouldHaveStatus OK }
              get(TOS_ENDPOINT).also { it shouldHaveStatus OK }

              get(pathOf(Python.contentRoot, TestData.GROUP_NAME, "boolean_array_test_WRONG_NAME")).also {
                it.bodyAsText() shouldContain CHALLENGE_NOT_FOUND
              }
            }

            testContent.forEachLanguage {
              client.get(contentRoot).also { it shouldHaveStatus OK }
            }

            testContent.forEachLanguage {
              forEachGroup {
                forEachChallenge {
                  client.get(pathOf(languageType.contentRoot, groupName, challengeName)).also {
                    it.bodyAsText() shouldNotContain CHALLENGE_NOT_FOUND
                  }
                }
              }
            }
          }
        }
    }

    "Test all challenges" {
      initTestProperties()
      TestData.readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            testContent.forEachLanguage {
              forEachGroup {
                forEachChallenge {
                  repeat(10) {
                    answerAllWith(this@testApplication, "") {
                      answerStatus shouldBe AnswerStatus.NOT_ANSWERED
                      hint.shouldBeBlank()
                    }

                    answerAllWith(this@testApplication, "wrong answer") {
                      answerStatus shouldBe AnswerStatus.INCORRECT
                    }

                    answerAllWithCorrectAnswer(this@testApplication) {
                      answerStatus shouldBe AnswerStatus.CORRECT
                      hint.shouldBeBlank()
                    }
                  }
                }
              }
            }
          }
        }
    }
  }
}
