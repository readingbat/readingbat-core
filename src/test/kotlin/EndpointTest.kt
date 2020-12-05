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
import com.github.readingbat.TestSupport.getUrl
import com.github.readingbat.TestSupport.module
import com.github.readingbat.TestSupport.testAllChallenges
import com.github.readingbat.common.Constants.CHALLENGE_NOT_FOUND
import com.github.readingbat.common.Endpoints.ABOUT_ENDPOINT
import com.github.readingbat.common.Endpoints.HELP_ENDPOINT
import com.github.readingbat.common.Endpoints.PRIVACY_ENDPOINT
import com.github.readingbat.dsl.LanguageType.Python
import com.github.readingbat.posts.AnswerStatus
import io.kotest.assertions.ktor.shouldHaveStatus
import io.kotest.core.spec.style.StringSpec
import io.kotest.inspectors.forAll
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
      withTestApplication({ module(true, testContent) }) {

        getUrl("/") { response shouldHaveStatus Found }

        getUrl(ABOUT_ENDPOINT) { response shouldHaveStatus OK }
        getUrl(HELP_ENDPOINT) { response shouldHaveStatus OK }
        getUrl(PRIVACY_ENDPOINT) { response shouldHaveStatus OK }

        getUrl(pathOf(Python.contentRoot,
                      GROUP_NAME,
                      "boolean_array_test2")) { response.content.shouldContain(CHALLENGE_NOT_FOUND) }

        testContent.languages
          .forAll { lang ->

            getUrl(lang.languageType.contentRoot) { response shouldHaveStatus OK }

            lang.challengeGroups.forEach { challengeGroup ->
              challengeGroup.challenges.forEach { challenge ->
                getUrl(pathOf(lang.languageType.contentRoot,
                              challengeGroup.groupName,
                              challenge.challengeName)) { response.content.shouldNotContain(CHALLENGE_NOT_FOUND) }
              }
            }
          }
      }
    }

    "Test all challenges" {
      val testContent = readTestContent()
      withTestApplication({ module(true, testContent) }) {

        testContent.languages
          .forEach { lang ->

            testAllChallenges(lang, "")
              .forAll {
                it.first shouldBe AnswerStatus.NOT_ANSWERED
                it.second.shouldBeBlank()
              }

            testAllChallenges(lang, "[wrong]")
              .forAll {
                it.first shouldBe AnswerStatus.INCORRECT
              }
          }
      }
    }

  })