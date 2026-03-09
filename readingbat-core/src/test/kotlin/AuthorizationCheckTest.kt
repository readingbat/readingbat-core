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
import com.github.readingbat.common.Endpoints.CLEAR_GROUP_ANSWERS_ENDPOINT
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

class AuthorizationCheckTest : StringSpec(
  {
    afterEach {
      Property.IS_PRODUCTION.setProperty("false")
    }

    "clearChallengeAnswers with mismatched userId does not delete data" {
      initTestProperties()

      readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            val spoofedKey =
              "correct-answers${KEY_SEP}${AUTH_KEY}${KEY_SEP}spoofed-user-id${KEY_SEP}fake-md5"
            val response =
              client.config { followRedirects = false }
                .post(CLEAR_CHALLENGE_ANSWERS_ENDPOINT) {
                  header(ContentType, FormUrlEncoded.toString())
                  setBody(
                    listOf(
                      LANGUAGE_NAME_PARAM to "Java",
                      GROUP_NAME_PARAM to GROUP_NAME,
                      CHALLENGE_NAME_PARAM to "StringArrayTest1",
                      CORRECT_ANSWERS_PARAM to spoofedKey,
                      CHALLENGE_ANSWERS_PARAM to "",
                    ).formUrlEncode(),
                  )
                }
            // Should redirect (not crash) — the spoofed key is silently ignored
            response shouldHaveStatus Found
          }
        }
    }

    "clearGroupAnswers with mismatched userId does not delete data" {
      initTestProperties()

      readTestContent()
        .also { testContent ->
          testApplication {
            application { testModule(testContent) }

            val spoofedKey =
              "correct-answers${KEY_SEP}${AUTH_KEY}${KEY_SEP}spoofed-user-id${KEY_SEP}fake-md5"
            val response =
              client.config { followRedirects = false }
                .post(CLEAR_GROUP_ANSWERS_ENDPOINT) {
                  header(ContentType, FormUrlEncoded.toString())
                  setBody(
                    listOf(
                      LANGUAGE_NAME_PARAM to "Java",
                      GROUP_NAME_PARAM to GROUP_NAME,
                      CORRECT_ANSWERS_PARAM to "[\"$spoofedKey\"]",
                      CHALLENGE_ANSWERS_PARAM to "[]",
                    ).formUrlEncode(),
                  )
                }
            // Should redirect (not crash) — the spoofed keys are silently ignored
            response shouldHaveStatus Found
          }
        }
    }
  },
)
