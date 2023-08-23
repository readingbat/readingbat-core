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

import com.github.readingbat.common.AuthName.AUTH_COOKIE
import com.github.readingbat.common.BrowserSession
import com.github.readingbat.common.UserPrincipal
import io.ktor.server.sessions.*
import kotlin.collections.set
import kotlin.time.Duration.Companion.days

internal object ConfigureCookies {

  fun SessionsConfig.configureSessionIdCookie() {
    cookie<BrowserSession>("readingbat_session_id") {
      //storage = RedisSessionStorage(redis = pool.resource)) {
      //storage = directorySessionStorage(File("server-sessions"), cached = true)) {
      cookie.path = "/" //CHALLENGE_ROOT + "/"
      cookie.httpOnly = true

      // CSRF protection in modern browsers. Make sure your important side-effect-y operations, like ordering,
      // uploads, and changing settings, use "unsafe" HTTP verbs like POST and PUT, not GET or HEAD.
      // https://developer.mozilla.org/en-US/docs/Web/HTTP/Cookies#SameSite_cookies
      cookie.extensions["SameSite"] = "lax"
    }
  }

  fun SessionsConfig.configureAuthCookie() {
    cookie<UserPrincipal>(name = AUTH_COOKIE) {
      cookie.path = "/" //CHALLENGE_ROOT + "/"
      cookie.httpOnly = true

      //if (production)
      //  cookie.secure = true

      cookie.maxAgeInSeconds = 14.days.inWholeSeconds

      // CSRF protection in modern browsers. Make sure your important side-effect-y operations, like ordering,
      // uploads, and changing settings, use "unsafe" HTTP verbs like POST and PUT, not GET or HEAD.
      // https://developer.mozilla.org/en-US/docs/Web/HTTP/Cookies#SameSite_cookies
      cookie.extensions["SameSite"] = "lax"
    }
  }
}