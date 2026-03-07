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

import com.github.readingbat.common.AuthName
import com.github.readingbat.common.Endpoints.OAUTH_CALLBACK_GITHUB_ENDPOINT
import com.github.readingbat.common.Endpoints.OAUTH_CALLBACK_GOOGLE_ENDPOINT
import com.github.readingbat.common.Property
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.OAuthServerSettings
import io.ktor.server.auth.oauth
import kotlinx.serialization.json.Json

internal object ConfigureOAuth {
  private val logger = KotlinLogging.logger {}

  val httpClient =
    HttpClient(CIO) {
      install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
      }
    }

  fun AuthenticationConfig.configureGitHubOAuth() {
    val clientId = Property.GITHUB_OAUTH_CLIENT_ID.getProperty("", errorOnNonInit = false)
    val clientSecret = Property.GITHUB_OAUTH_CLIENT_SECRET.getProperty("", errorOnNonInit = false)

    if (clientId.isBlank() || clientSecret.isBlank()) {
      logger.warn { "GitHub OAuth not configured — missing client ID or secret" }
      return
    }

    oauth(AuthName.GITHUB_OAUTH) {
      urlProvider = { resolveCallbackUrl(OAUTH_CALLBACK_GITHUB_ENDPOINT) }
      providerLookup = {
        OAuthServerSettings.OAuth2ServerSettings(
          name = "github",
          authorizeUrl = "https://github.com/login/oauth/authorize",
          accessTokenUrl = "https://github.com/login/oauth/access_token",
          requestMethod = HttpMethod.Post,
          clientId = clientId,
          clientSecret = clientSecret,
          defaultScopes = listOf("user:email"),
        )
      }
      client = httpClient
    }
    logger.info { "GitHub OAuth configured" }
  }

  fun AuthenticationConfig.configureGoogleOAuth() {
    val clientId = Property.GOOGLE_OAUTH_CLIENT_ID.getProperty("", errorOnNonInit = false)
    val clientSecret = Property.GOOGLE_OAUTH_CLIENT_SECRET.getProperty("", errorOnNonInit = false)

    if (clientId.isBlank() || clientSecret.isBlank()) {
      logger.warn { "Google OAuth not configured — missing client ID or secret" }
      return
    }

    oauth(AuthName.GOOGLE_OAUTH) {
      urlProvider = { resolveCallbackUrl(OAUTH_CALLBACK_GOOGLE_ENDPOINT) }
      providerLookup = {
        OAuthServerSettings.OAuth2ServerSettings(
          name = "google",
          authorizeUrl = "https://accounts.google.com/o/oauth2/auth",
          accessTokenUrl = "https://oauth2.googleapis.com/token",
          requestMethod = HttpMethod.Post,
          clientId = clientId,
          clientSecret = clientSecret,
          defaultScopes = listOf("openid", "profile", "email"),
        )
      }
      client = httpClient
    }
    logger.info { "Google OAuth configured" }
  }

  private fun resolveCallbackUrl(path: String): String {
    val prefix = Property.OAUTH_CALLBACK_URL_PREFIX.getProperty("http://localhost:8080", errorOnNonInit = false)
    return "$prefix$path"
  }
}
