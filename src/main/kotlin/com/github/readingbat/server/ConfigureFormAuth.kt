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

package com.github.readingbat.server

import com.github.pambrose.common.redis.RedisUtils.withRedisPool
import com.github.pambrose.common.util.sha256
import com.github.readingbat.misc.AuthName
import com.github.readingbat.misc.FormFields
import com.github.readingbat.misc.UserId.Companion.lookupUserId
import com.github.readingbat.misc.UserId.Companion.lookupUsername
import com.github.readingbat.misc.UserPrincipal
import com.google.common.util.concurrent.RateLimiter
import io.ktor.auth.Authentication
import io.ktor.auth.UserPasswordCredential
import io.ktor.auth.form
import io.ktor.auth.session
import mu.KLogging

internal object ConfigureFormAuth : KLogging() {

  private val failedLoginLimiter = RateLimiter.create(1.0) // rate 2.0 is "2 permits per second"

  /**
   * Form-based authentication is a interceptor that reads attributes off a POST request in order to validate the user.
   * Only needed by whatever your login form is POSTing to.
   *
   * If validation fails, the user will be challenged, e.g. sent to a login page to authenticate.
   */
  fun Authentication.Configuration.configureFormAuth() {
    form(AuthName.FORM) {
      userParamName = FormFields.USERNAME
      passwordParamName = FormFields.PASSWORD

      challenge {
        // I don't think form auth supports multiple errors, but we're conservatively assuming there will be at
        // most one error, which we handle here. Worst case, we just send the user to login with no context.

        // val errors: List<AuthenticationFailedCause> = call.authentication.allFailures
        // logger.info { "Inside challenge: $errors" }

        // In apps that require a valid login, you would redirect the user to a login page from here
        // However, we allow non-logged in users, so we do nothing here.
        /*
          when (errors.singleOrNull()) {
            AuthenticationFailedCause.InvalidCredentials -> call.respondRedirect("$LOGIN?invalid")
            AuthenticationFailedCause.NoCredentials -> call.respondRedirect("$LOGIN?no")
            else -> call.respondRedirect(LOGIN)
          }
        */
      }

      validate { cred: UserPasswordCredential ->
        var principal: UserPrincipal? = null

        withRedisPool { redis ->
          val userId = lookupUsername(cred.name, redis)
          if (userId != null) {
            val (salt, digest) = lookupUserId(userId, redis)
            if (salt.isNotEmpty() && digest.isNotEmpty() && digest == cred.password.sha256(salt)) {
              logger.info { "Found user ${cred.name} ${userId.id}" }
              principal = UserPrincipal(cred.name)
            }
          }
        }

        logger.info { "Login ${if (principal == null) "failure" else "success"}" }

        if (principal == null)
          failedLoginLimiter.acquire() // may wait

        principal
      }
    }

    /**
     * Let the user authenticate by their session (a cookie).
     *
     * This is related to the configureAuthCookie method by virtue of the common `PrincipalType` object.
     */
    fun Authentication.Configuration.configureSessionAuth() {
      session<UserPrincipal>(AuthName.SESSION) {
        challenge {
          // What to do if the user isn't authenticated
          // Uncomment this to send user to login page
          //call.respondRedirect("${CommonRoutes.LOGIN}?no")
        }
        validate { principal: UserPrincipal ->
          // If you need to do additional validation on session data, you can do so here.
          principal
        }
      }
    }
  }
}