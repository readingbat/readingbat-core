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

import com.readingbat.common.AuthName.AUTH_COOKIE
import com.readingbat.common.BrowserSession
import com.readingbat.common.OAuthReturnUrl
import com.readingbat.common.UserPrincipal
import io.ktor.server.sessions.SessionsConfig
import io.ktor.server.sessions.cookie
import kotlin.time.Duration.Companion.days

/**
 * Cookie and session configuration for the Ktor Sessions plugin.
 *
 * Configures three cookies: the browser session ID cookie, the authentication
 * principal cookie (with 14-day max age), and the OAuth return URL cookie
 * (with 10-minute max age for surviving OAuth round-trips). All cookies use
 * SameSite=Lax for CSRF protection and are marked httpOnly and secure in production.
 */
internal object ConfigureCookies {
  /** Configures the anonymous browser session ID cookie used for tracking sessions before login. */
  fun SessionsConfig.configureSessionIdCookie(production: Boolean) {
    cookie<BrowserSession>("readingbat_session_id") {
      // storage = RedisSessionStorage(redis = pool.resource)) {
      // storage = directorySessionStorage(File("server-sessions"), cached = true)) {
      cookie.path = "/" // CHALLENGE_ROOT + "/"
      cookie.httpOnly = true
      if (production) cookie.secure = true

      // CSRF protection in modern browsers. Make sure your important side-effect-y operations, like ordering,
      // uploads, and changing settings, use "unsafe" HTTP verbs like POST and PUT, not GET or HEAD.
      // https://developer.mozilla.org/en-US/docs/Web/HTTP/Cookies#SameSite_cookies
      cookie.extensions["SameSite"] = "lax"
    }
  }

  /** Configures the authentication cookie that persists the [UserPrincipal] across requests. */
  fun SessionsConfig.configureAuthCookie(production: Boolean) {
    cookie<UserPrincipal>(name = AUTH_COOKIE) {
      cookie.path = "/" // CHALLENGE_ROOT + "/"
      cookie.httpOnly = true
      if (production) cookie.secure = true

      cookie.maxAgeInSeconds = 14.days.inWholeSeconds

      // CSRF protection in modern browsers. Make sure your important side-effect-y operations, like ordering,
      // uploads, and changing settings, use "unsafe" HTTP verbs like POST and PUT, not GET or HEAD.
      // https://developer.mozilla.org/en-US/docs/Web/HTTP/Cookies#SameSite_cookies
      cookie.extensions["SameSite"] = "lax"
    }
  }

  /** Configures a short-lived cookie to preserve the return URL across the OAuth redirect flow. */
  fun SessionsConfig.configureOAuthReturnUrlCookie(production: Boolean) {
    cookie<OAuthReturnUrl>("readingbat_oauth_return") {
      cookie.path = "/"
      cookie.httpOnly = true
      if (production) cookie.secure = true
      cookie.maxAgeInSeconds = 600 // 10 min — survives OAuth round-trip
      cookie.extensions["SameSite"] = "lax"
    }
  }
}
