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

package com.github.readingbat.server

import com.github.readingbat.common.Property
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.submitForm
import io.ktor.http.Parameters
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object RecaptchaService {
  private val logger = KotlinLogging.logger {}
  private const val RECAPTCHA_VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify"

  private val httpClient =
    HttpClient(CIO) {
      install(ContentNegotiation) {
        json(
          Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
          },
        )
      }
    }

  @Serializable
  data class RecaptchaResponse(
    val success: Boolean,
    @SerialName("error-codes")
    val errorCodes: List<String> = emptyList(),
    val hostname: String? = null,
    @SerialName("challenge_ts")
    val challengeTs: String? = null,
  )

  fun isRecaptchaEnabled(): Boolean =
    Property.RECAPTCHA_ENABLED.getProperty(default = false, errorOnNonInit = false) &&
      Property.RECAPTCHA_SECRET_KEY.getPropertyOrNull(errorOnNonInit = false)?.isNotBlank() == true

  suspend fun verifyRecaptcha(recaptchaResponse: String, remoteIp: String? = null): Boolean {
    if (!isRecaptchaEnabled()) {
      logger.debug { "reCAPTCHA is disabled, skipping verification" }
      return true
    }

    val secretKey = Property.RECAPTCHA_SECRET_KEY.getPropertyOrNull(errorOnNonInit = false)
    if (secretKey.isNullOrBlank()) {
      logger.warn { "reCAPTCHA secret key is not configured" }
      return false
    }

    return try {
      val parameters =
        Parameters.build {
          append("secret", secretKey)
          append("response", recaptchaResponse)
          if (!remoteIp.isNullOrBlank()) {
            append("remoteip", remoteIp)
          }
        }

      logger.info { "Verifying reCAPTCHA with Google API" }
      val response: RecaptchaResponse =
        httpClient.submitForm(
          url = RECAPTCHA_VERIFY_URL,
          formParameters = parameters,
        ).body()

      if (response.success) {
        logger.info { "reCAPTCHA verification successful" }
        true
      } else {
        logger.warn { "reCAPTCHA verification failed: ${response.errorCodes.joinToString()}" }
        false
      }
    } catch (e: Exception) {
      logger.error(e) { "Error verifying reCAPTCHA" }
      false
    }
  }

  fun getSiteKey(): String? = Property.RECAPTCHA_SITE_KEY.getPropertyOrNull(errorOnNonInit = false)
}
