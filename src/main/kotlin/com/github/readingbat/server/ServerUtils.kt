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

import com.github.pambrose.common.util.Version.Companion.versionDesc
import com.github.pambrose.common.util.isNotNull
import com.github.pambrose.common.util.md5
import com.github.readingbat.misc.BrowserSession
import com.github.readingbat.misc.KeyConstants.KEY_SEP
import com.github.readingbat.misc.User
import com.github.readingbat.misc.User.Companion.toUser
import com.github.readingbat.misc.UserPrincipal
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.config.*
import io.ktor.sessions.*
import io.ktor.util.pipeline.*
import mu.KLogging

typealias PipelineCall = PipelineContext<Unit, ApplicationCall>

internal fun keyOf(vararg keys: Any) = keys.joinToString(KEY_SEP) { it.toString() }

internal fun md5Of(vararg keys: Any) = keys.joinToString(KEY_SEP) { it.toString() }.md5()

internal object ServerUtils : KLogging() {

  internal fun getVersionDesc(asJson: Boolean = false): String = ReadingBatServer::class.versionDesc(asJson)

  fun Application.property(name: String, default: String = "", warn: Boolean = false) =
    try {
      environment.config.property(name).getString()
    } catch (e: ApplicationConfigurationException) {
      if (warn)
        logger.warn { "Missing $name value in application.conf" }
      default
    }

  fun PipelineCall.queryParam(key: String, default: String = "") = call.request.queryParameters[key] ?: default

  fun PipelineCall.fetchUser(loginAttempt: Boolean = false): User? =
    fetchPrincipal(loginAttempt)?.userId?.toUser(call.sessions.get<BrowserSession>())

  private fun PipelineCall.fetchPrincipal(loginAttempt: Boolean): UserPrincipal? =
    if (loginAttempt) assignPrincipal() else call.sessions.get<UserPrincipal>()

  private fun PipelineCall.assignPrincipal(): UserPrincipal? =
    call.principal<UserPrincipal>().apply { if (isNotNull()) call.sessions.set(this) }  // Set the cookie
}

class RedirectException(val redirectUrl: String) : Exception()