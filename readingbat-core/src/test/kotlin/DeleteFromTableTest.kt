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
import com.github.readingbat.common.Endpoints.CLEAR_CHALLENGE_ANSWERS_ENDPOINT
import com.github.readingbat.common.FormFields.CHALLENGE_ANSWERS_PARAM
import com.github.readingbat.common.FormFields.CHALLENGE_NAME_PARAM
import com.github.readingbat.common.FormFields.CORRECT_ANSWERS_PARAM
import com.github.readingbat.common.FormFields.GROUP_NAME_PARAM
import com.github.readingbat.common.FormFields.LANGUAGE_NAME_PARAM
import com.github.readingbat.common.KeyConstants.AUTH_KEY
import com.github.readingbat.common.KeyConstants.KEY_SEP
import com.github.readingbat.common.Property
import com.github.readingbat.kotest.TestSupport.initTestProperties
import com.github.readingbat.kotest.TestSupport.testModule
import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.core.spec.style.StringSpec
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType.Application.FormUrlEncoded
import io.ktor.http.HttpHeaders.ContentType
import io.ktor.http.HttpStatusCode.Companion.Found
import io.ktor.http.formUrlEncode
import io.ktor.server.testing.testApplication

class DeleteFromTableTest : StringSpec() {
  init {
    afterEach {
      Property.IS_PRODUCTION.setProperty("false")
    }

    "Both delete paths redirect correctly when both keys are provided" {
      initTestProperties()

      readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            val correctKey =
              "correct${KEY_SEP}${AUTH_KEY}${KEY_SEP}some-user${KEY_SEP}md5-1"
            val challengeKey =
              "challenge${KEY_SEP}${AUTH_KEY}${KEY_SEP}some-user${KEY_SEP}md5-2"
            val response =
              client.config { followRedirects = false }
                .post(CLEAR_CHALLENGE_ANSWERS_ENDPOINT) {
                  header(ContentType, FormUrlEncoded.toString())
                  setBody(
                    listOf(
                      LANGUAGE_NAME_PARAM to "Java",
                      GROUP_NAME_PARAM to GROUP_NAME,
                      CHALLENGE_NAME_PARAM to "StringArrayTest1",
                      CORRECT_ANSWERS_PARAM to correctKey,
                      CHALLENGE_ANSWERS_PARAM to challengeKey,
                    ).formUrlEncode(),
                  )
                }
            response shouldHaveStatus Found
          }
        }
    }

    "Delete paths handle empty keys gracefully" {
      initTestProperties()

      readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            val response =
              client.config { followRedirects = false }
                .post(CLEAR_CHALLENGE_ANSWERS_ENDPOINT) {
                  header(ContentType, FormUrlEncoded.toString())
                  setBody(
                    listOf(
                      LANGUAGE_NAME_PARAM to "Java",
                      GROUP_NAME_PARAM to GROUP_NAME,
                      CHALLENGE_NAME_PARAM to "StringArrayTest1",
                      CORRECT_ANSWERS_PARAM to "",
                      CHALLENGE_ANSWERS_PARAM to "",
                    ).formUrlEncode(),
                  )
                }
            response shouldHaveStatus Found
          }
        }
    }

    "Delete paths handle malformed keys without crashing" {
      initTestProperties()

      readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            val response =
              client.config { followRedirects = false }
                .post(CLEAR_CHALLENGE_ANSWERS_ENDPOINT) {
                  header(ContentType, FormUrlEncoded.toString())
                  setBody(
                    listOf(
                      LANGUAGE_NAME_PARAM to "Java",
                      GROUP_NAME_PARAM to GROUP_NAME,
                      CHALLENGE_NAME_PARAM to "StringArrayTest1",
                      CORRECT_ANSWERS_PARAM to "malformed-key-no-separators",
                      CHALLENGE_ANSWERS_PARAM to "also-malformed",
                    ).formUrlEncode(),
                  )
                }
            response shouldHaveStatus Found
          }
        }
    }
  }
}
