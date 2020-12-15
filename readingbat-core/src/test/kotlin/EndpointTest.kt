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

import com.github.pambrose.common.util.pathOf
import com.github.readingbat.TestData.GROUP_NAME
import com.github.readingbat.TestData.readTestContent
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
import com.github.readingbat.kotest.TestSupport.getUrl
import com.github.readingbat.kotest.TestSupport.testModule
import com.github.readingbat.posts.AnswerStatus.CORRECT
import com.github.readingbat.posts.AnswerStatus.INCORRECT
import com.github.readingbat.posts.AnswerStatus.NOT_ANSWERED
import io.kotest.assertions.ktor.shouldHaveStatus
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeBlank
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.http.HttpStatusCode.Companion.Found
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.testing.*

class EndpointTest : StringSpec(
  {
    "Simple endpoint tests" {
      val testContent = readTestContent()
      withTestApplication({ testModule(testContent) }) {

        getUrl("/") { response shouldHaveStatus Found }

        getUrl(ABOUT_ENDPOINT) { response shouldHaveStatus OK }
        getUrl(HELP_ENDPOINT) { response shouldHaveStatus OK }
        getUrl(PRIVACY_ENDPOINT) { response shouldHaveStatus OK }

        getUrl(pathOf(Python.contentRoot, GROUP_NAME, "boolean_array_test2")) {
          response.content shouldContain CHALLENGE_NOT_FOUND
        }

        testContent.forEachLanguage {
          getUrl(contentRoot) { response shouldHaveStatus OK }
        }

        testContent.forEachLanguage {
          forEachGroup {
            forEachChallenge {
              getUrl(pathOf(languageType.contentRoot, groupName, challengeName)) {
                response.content shouldNotContain CHALLENGE_NOT_FOUND
              }
            }
          }
        }
      }
    }

    "Test all challenges" {
      val testContent = readTestContent()
      withTestApplication({ testModule(testContent) }) {

        testContent.forEachLanguage {
          forEachGroup {
            forEachChallenge {
              repeat(10) {
                answerAllWith(this@withTestApplication, "") {
                  answerStatus shouldBe NOT_ANSWERED
                  hint.shouldBeBlank()
                }

                answerAllWith(this@withTestApplication, "wrong answer") {
                  answerStatus shouldBe INCORRECT
                }

                answerAllWithCorrectAnswer(this@withTestApplication) {
                  answerStatus shouldBe CORRECT
                  hint.shouldBeBlank()
                }
              }
            }
          }
        }
      }
    }
  })