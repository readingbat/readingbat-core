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

package com.github.readingbat.server.routes

import com.github.pambrose.common.email.Email
import com.github.pambrose.common.util.randomId
import com.github.readingbat.common.AuthName
import com.github.readingbat.common.Endpoints.OAUTH_CALLBACK_GITHUB_ENDPOINT
import com.github.readingbat.common.Endpoints.OAUTH_CALLBACK_GOOGLE_ENDPOINT
import com.github.readingbat.common.Endpoints.OAUTH_LOGIN_GITHUB_ENDPOINT
import com.github.readingbat.common.Endpoints.OAUTH_LOGIN_GOOGLE_ENDPOINT
import com.github.readingbat.common.OAuthReturnUrl
import com.github.readingbat.common.User
import com.github.readingbat.common.User.Companion.queryUserByEmail
import com.github.readingbat.common.UserPrincipal
import com.github.readingbat.server.ConfigureOAuth
import com.github.readingbat.server.FullName
import com.github.readingbat.server.OAuthLinksTable
import com.github.readingbat.server.ServerUtils.safeRedirectPath
import com.github.readingbat.server.UsersTable
import com.pambrose.common.exposed.readonlyTx
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.server.auth.OAuthAccessTokenResponse
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

@Serializable
private data class GitHubUser(
  val login: String = "",
  val name: String? = null,
  val email: String? = null,
  val id: Long = 0,
  @SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable
private data class GitHubEmail(
  val email: String,
  val primary: Boolean = false,
  val verified: Boolean = false,
)

@Serializable
private data class GoogleUser(
  val id: String = "",
  val name: String? = null,
  val email: String? = null,
  @SerialName("given_name") val givenName: String? = null,
  @SerialName("family_name") val familyName: String? = null,
  val picture: String? = null,
)

private val json = Json { ignoreUnknownKeys = true }

fun Routing.oauthRoutes() {
  val logger = KotlinLogging.logger {}

  authenticate(AuthName.GITHUB_OAUTH) {
    get(OAUTH_LOGIN_GITHUB_ENDPOINT) {
      // Ktor redirects to GitHub automatically
    }

    get(OAUTH_CALLBACK_GITHUB_ENDPOINT) {
      val principal =
        call.principal<OAuthAccessTokenResponse.OAuth2>()
        ?: run {
          call.respondRedirect("/")
          return@get
        }

      val accessToken = principal.accessToken

      // Fetch user info from GitHub
      val userResponse =
        ConfigureOAuth.httpClient.get("https://api.github.com/user") {
        bearerAuth(accessToken)
      }
      val ghUser = json.decodeFromString<GitHubUser>(userResponse.body<String>())

      // Fetch primary email (handles private emails)
      val email =
        ghUser.email ?: run {
        val emailsResponse =
          ConfigureOAuth.httpClient.get("https://api.github.com/user/emails") {
          bearerAuth(accessToken)
        }
        val emails = json.decodeFromString<List<GitHubEmail>>(emailsResponse.body<String>())
        emails.firstOrNull { it.primary && it.verified }?.email
          ?: emails.firstOrNull { it.verified }?.email
          ?: ""
      }

      val name = ghUser.name ?: ghUser.login
      val providerId = ghUser.id.toString()
      val avatarUrl = ghUser.avatarUrl

      logger.info { "GitHub OAuth callback: name=$name email=$email providerId=$providerId" }

      val user = findOrCreateOAuthUser("github", providerId, Email(email), FullName(name), accessToken, avatarUrl)
      call.sessions.set(UserPrincipal(userId = user.userId))
      val returnUrl = safeRedirectPath(call.sessions.get<OAuthReturnUrl>()?.url ?: "/")
      call.sessions.clear<OAuthReturnUrl>()
      call.respondRedirect(returnUrl)
    }
  }

  authenticate(AuthName.GOOGLE_OAUTH) {
    get(OAUTH_LOGIN_GOOGLE_ENDPOINT) {
      // Ktor redirects to Google automatically
    }

    get(OAUTH_CALLBACK_GOOGLE_ENDPOINT) {
      val principal =
        call.principal<OAuthAccessTokenResponse.OAuth2>()
        ?: run {
          call.respondRedirect("/")
          return@get
        }

      val accessToken = principal.accessToken

      // Fetch user info from Google
      val userResponse =
        ConfigureOAuth.httpClient.get("https://www.googleapis.com/oauth2/v2/userinfo") {
        bearerAuth(accessToken)
      }
      val googleUser = json.decodeFromString<GoogleUser>(userResponse.body<String>())

      val email = googleUser.email ?: ""
      val name = googleUser.name ?: "${googleUser.givenName ?: ""} ${googleUser.familyName ?: ""}".trim()
      val providerId = googleUser.id
      val avatarUrl = googleUser.picture

      logger.info { "Google OAuth callback: name=$name email=$email providerId=$providerId" }

      val user = findOrCreateOAuthUser("google", providerId, Email(email), FullName(name), accessToken, avatarUrl)
      call.sessions.set(UserPrincipal(userId = user.userId))
      val returnUrl = safeRedirectPath(call.sessions.get<OAuthReturnUrl>()?.url ?: "/")
      call.sessions.clear<OAuthReturnUrl>()
      call.respondRedirect(returnUrl)
    }
  }
}

private val oauthLogger = KotlinLogging.logger {}

private fun findOrCreateOAuthUser(
  provider: String,
  providerId: String,
  email: Email,
  name: FullName,
  accessToken: String,
  avatarUrl: String? = null,
): User {
  // 1. Check if oauth_links entry exists for this provider+providerId
  val existingUserId =
    readonlyTx {
    with(OAuthLinksTable) {
      select(userRef)
        .where { (OAuthLinksTable.provider eq provider) and (OAuthLinksTable.providerId eq providerId) }
        .map { it[userRef] }
        .firstOrNull()
    }
  }

  if (existingUserId != null) {
    // Found existing link — look up the user
    val userId =
      readonlyTx {
      with(UsersTable) {
        select(UsersTable.userId)
          .where { UsersTable.id eq existingUserId }
          .map { it[UsersTable.userId] }
          .first()
      }
    }
    oauthLogger.info { "OAuth login: existing link for $provider/$providerId -> user $userId" }

    // Update access token and avatar URL
    transaction {
      OAuthLinksTable.update({
        (OAuthLinksTable.provider eq provider) and (OAuthLinksTable.providerId eq providerId)
      }) { row ->
        row[OAuthLinksTable.accessToken] = accessToken
        row[updated] = org.joda.time.DateTime.now(org.joda.time.DateTimeZone.UTC)
      }
      if (avatarUrl != null) {
        UsersTable.update({ UsersTable.id eq existingUserId }) { row ->
          row[UsersTable.avatarUrl] = avatarUrl
        }
      }
    }

    return User.Companion.run { userId.toUser() }
  }

  // 2. No link found — check if email matches an existing user
  if (email.isNotBlank()) {
    val existingUser = queryUserByEmail(email)
    if (existingUser != null) {
      val userProvider =
        readonlyTx {
        with(UsersTable) {
          select(authProvider)
            .where { UsersTable.id eq existingUser.userDbmsId }
            .map { it[authProvider] }
            .firstOrNull()
        }
      }

      // Auto-link any OAuth provider to existing user with matching email.
      // This is safe because both GitHub and Google only return verified emails.
      oauthLogger.info { "OAuth login: auto-linking $provider to existing user ${existingUser.email}" }
      transaction {
        // Link OAuth provider to existing user
        with(OAuthLinksTable) {
          insert { row ->
            row[userRef] = existingUser.userDbmsId
            row[OAuthLinksTable.provider] = provider
            row[OAuthLinksTable.providerId] = providerId
            row[providerEmail] = email.value
            row[OAuthLinksTable.accessToken] = accessToken
          }
        }
        // Set auth_provider if not already set, and update avatar
        if (userProvider == null || avatarUrl != null) {
          UsersTable.update({ UsersTable.id eq existingUser.userDbmsId }) { row ->
            if (userProvider == null) row[authProvider] = provider
            if (avatarUrl != null) row[UsersTable.avatarUrl] = avatarUrl
          }
        }
      }
      return existingUser
    }
  }

  // 3. No link, no matching email — create new user
  oauthLogger.info { "OAuth login: creating new user for $provider/$providerId email=$email" }
  return User.createOAuthUser(name, email, provider, providerId, accessToken, avatarUrl)
}
