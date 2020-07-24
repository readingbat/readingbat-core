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

import com.github.pambrose.common.util.isNotNull
import com.github.readingbat.misc.User
import com.github.readingbat.misc.User.Companion.toUser
import com.github.readingbat.misc.UserPrincipal
import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.auth.principal
import io.ktor.config.ApplicationConfigurationException
import io.ktor.sessions.get
import io.ktor.sessions.sessions
import io.ktor.sessions.set
import io.ktor.util.pipeline.PipelineContext
import mu.KLogging

typealias PipelineCall = PipelineContext<Unit, ApplicationCall>

internal object ServerUtils : KLogging() {
  fun Application.property(name: String, default: String = "", warn: Boolean = false) =
    try {
      environment.config.property(name).getString()
    } catch (e: ApplicationConfigurationException) {
      if (warn)
        logger.warn { "Missing $name value in application.conf" }
      default
    }

  private fun PipelineCall.fetchPrincipal(loginAttempt: Boolean): UserPrincipal? =
    if (loginAttempt) assignPrincipal() else call.sessions.get<UserPrincipal>()

  fun PipelineCall.fetchUser(loginAttempt: Boolean = false): User? = fetchPrincipal(loginAttempt)?.userId?.toUser()

  private fun PipelineCall.assignPrincipal() =
    call.principal<UserPrincipal>().apply { if (this.isNotNull()) call.sessions.set(this) }  // Set the cookie

  fun PipelineCall.queryParam(key: String, default: String = "") = call.request.queryParameters[key] ?: default

}

class RedirectException(val redirectUrl: String) : Exception()