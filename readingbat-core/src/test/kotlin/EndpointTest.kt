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

import TestData.GROUP_NAME
import TestData.readTestContent
import com.github.pambrose.common.util.pathOf
import com.github.readingbat.common.Constants.CHALLENGE_NOT_FOUND
import com.github.readingbat.common.Endpoints.ABOUT_ENDPOINT
import com.github.readingbat.common.Endpoints.HELP_ENDPOINT
import com.github.readingbat.common.Endpoints.PRIVACY_ENDPOINT
import com.github.readingbat.dsl.LanguageType.Python
import com.github.readingbat.kotest.TestSupport.answerAllWith
import com.github.readingbat.kotest.TestSupport.answerAllWithCorrectAnswer
import com.github.readingbat.kotest.TestSupport.forEachChallenge
import com.github.readingbat.kotest.TestSupport.forEachGroup
import com.github.readingbat.kotest.TestSupport.forEachLanguage
import com.github.readingbat.kotest.TestSupport.testModule
import com.github.readingbat.posts.AnswerStatus.CORRECT
import com.github.readingbat.posts.AnswerStatus.INCORRECT
import com.github.readingbat.posts.AnswerStatus.NOT_ANSWERED
import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeBlank
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.testing.*

class EndpointTest : StringSpec(
  {
    "Simple endpoint tests" {
      readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            client.apply {
              get("/").also { it shouldHaveStatus OK }
              get(ABOUT_ENDPOINT).also { it shouldHaveStatus OK }
              get(HELP_ENDPOINT).also { it shouldHaveStatus OK }
              get(PRIVACY_ENDPOINT).also { it shouldHaveStatus OK }

              get(pathOf(Python.contentRoot, GROUP_NAME, "boolean_array_test_WRONG_NAME")).also {
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
      readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            testContent.forEachLanguage {
              forEachGroup {
                forEachChallenge {
                  repeat(10) {
                    answerAllWith(this@testApplication, "") {
                      answerStatus shouldBe NOT_ANSWERED
                      hint.shouldBeBlank()
                    }

                    answerAllWith(this@testApplication, "wrong answer") {
                      answerStatus shouldBe INCORRECT
                    }

                    answerAllWithCorrectAnswer(this@testApplication) {
                      answerStatus shouldBe CORRECT
                      hint.shouldBeBlank()
                    }
                  }
                }
              }
            }
          }
        }
    }
  },
)
