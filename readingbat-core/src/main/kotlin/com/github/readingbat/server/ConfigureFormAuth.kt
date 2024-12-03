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

@file:Suppress("UnstableApiUsage")

package com.github.readingbat.server

import com.github.pambrose.common.util.isNotNull
import com.github.pambrose.common.util.isNull
import com.github.pambrose.common.util.sha256
import com.github.readingbat.common.AuthName
import com.github.readingbat.common.Constants.DBMS_DOWN
import com.github.readingbat.common.Constants.UNKNOWN
import com.github.readingbat.common.FormFields.EMAIL_PARAM
import com.github.readingbat.common.FormFields.PASSWORD_PARAM
import com.github.readingbat.common.User.Companion.queryUserByEmail
import com.github.readingbat.common.UserPrincipal
import com.github.readingbat.dsl.isDbmsEnabled
import com.google.common.util.concurrent.RateLimiter
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.UserPasswordCredential
import io.ktor.server.auth.form
import io.ktor.server.auth.session

internal object ConfigureFormAuth {
  private val logger = KotlinLogging.logger {}
  private val failedLoginLimiter = RateLimiter.create(1.0) // rate 2.0 is "2 permits per second"

  /**
   * Form-based authentication is an interceptor that reads attributes off a POST request in order to validate the user.
   * Only needed by whatever your login form is POSTing to.
   *
   * If validation fails, the user will be challenged, e.g. sent to a login page to authenticate.
   */
  fun AuthenticationConfig.configureFormAuth() {
    form(AuthName.FORM) {
      userParamName = EMAIL_PARAM
      passwordParamName = PASSWORD_PARAM

      challenge {
        // I don't think form auth supports multiple errors, but we're conservatively assuming there will be at
        // most one error, which we handle here. Worst case, we just send the user to log in with no context.

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
        // Do not move the elvis check to the end of the lambda because it can return a null value
        if (!isDbmsEnabled()) {
          logger.error { DBMS_DOWN }
          error("DBMS not enabled")
        } else {
          var principal: UserPrincipal? = null
          val user = queryUserByEmail(Email(cred.name))
          if (user.isNotNull()) {
            val salt = user.salt
            val digest = user.digest
            if (salt.isNotBlank() && digest.isNotBlank() && digest == cred.password.sha256(salt)) {
              logger.debug { "Found user ${cred.name} ${user.userId}" }
              principal = UserPrincipal(user.userId)
            }
          }

          logger.info {
            val str = if (principal.isNull()) "failure" else "success for $user ${user?.email ?: UNKNOWN}"
            "Login $str"
          }

          if (principal.isNull())
            failedLoginLimiter.acquire() // may block

          principal
        }
      }
    }

    /**
     * Let the user authenticate by their session (a cookie).
     *
     * This is related to the configureAuthCookie method by virtue of the common `PrincipalType` object.
     */
    @Suppress("unused")
    fun AuthenticationConfig.configureSessionAuth() {
      session<UserPrincipal>(AuthName.SESSION) {
        challenge {
          // What to do if the user isn't authenticated
          // Uncomment this to send user to login page
          // call.respondRedirect("${CommonRoutes.LOGIN}?no")
        }
        validate { principal: UserPrincipal ->
          // If you need to do additional validation on session data, you can do so here.
          principal
        }
      }
    }
  }
}
