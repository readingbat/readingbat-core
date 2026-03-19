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

import com.github.pambrose.common.email.Email.Companion.UNKNOWN_EMAIL
import com.github.pambrose.common.response.redirectTo
import com.github.pambrose.common.response.respondWith
import com.github.pambrose.common.util.Version.Companion.versionDesc
import com.github.readingbat.common.Metrics
import com.github.readingbat.common.User
import com.github.readingbat.common.User.Companion.toUser
import com.github.readingbat.common.browserSession
import com.github.readingbat.common.isNotAdminUser
import com.github.readingbat.common.isNotValidUser
import com.github.readingbat.common.userPrincipal
import com.github.readingbat.dsl.InvalidRequestException
import com.github.readingbat.dsl.LanguageType
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.server.ws.PubSubCommandsWs.publishLog
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpMethod
import io.ktor.http.formUrlEncode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveParameters
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.RoutingHandler
import io.ktor.server.routing.route
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.utils.io.KtorDsl

@Suppress("unused")
internal object ServerUtils {
  private val logger = KotlinLogging.logger {}

  fun getVersionDesc(asJson: Boolean = false): String = ReadingBatServer::class.versionDesc(asJson)

  fun RoutingContext.queryParam(key: String, default: String = "") = call.request.queryParameters[key] ?: default

  fun safeRedirectPath(path: String): String =
    if (path.startsWith("/") && !path.startsWith("//")) path else "/"

  fun RoutingContext.fetchUser(): User? =
    call.userPrincipal?.userId?.toUser(call.browserSession)

  fun WebSocketServerSession.fetchUser(): User? =
    call.userPrincipal?.userId?.toUser(call.browserSession)

  suspend fun ApplicationCall.paramMap() = receiveParameters().entries().associate { it.key to it.value[0] }

  fun ApplicationCall.fetchUser(): User? = userPrincipal?.userId?.toUser(browserSession)

  fun ApplicationCall.fetchUserDbmsIdFromCache() =
    userPrincipal?.userId?.let { User.fetchUserDbmsIdFromCache(it) } ?: -1L

  fun ApplicationCall.fetchEmailFromCache() =
    userPrincipal?.userId?.let { User.fetchEmailFromCache(it) } ?: UNKNOWN_EMAIL

  suspend fun RoutingContext.respondWithPageResult(block: suspend () -> PageResult) =
    when (val result = block()) {
      is PageResult.Html -> respondWith { result.content }
      is PageResult.Redirect -> redirectTo { result.url }
    }

  fun authenticateAdminUser(user: User?, block: () -> String): String {
    if (user.isNotValidUser()) throw InvalidRequestException("Must be logged in for this function")
    if (user.isNotAdminUser()) throw InvalidRequestException("Must be system admin for this function")
    return block()
  }

  @KtorDsl
  fun Route.get(path: String, metrics: Metrics, body: RoutingHandler) =
    route(path, HttpMethod.Get) {
      handle {
        metrics.measureEndpointRequest(path) { body() }
      }
    }

  @KtorDsl
  fun Route.post(path: String, metrics: Metrics, body: RoutingHandler) =
    route(path, HttpMethod.Post) {
      handle {
        metrics.measureEndpointRequest(path) { body() }
      }
    }

  fun RoutingContext.defaultLanguageTab(content: ReadingBatContent, user: User?): String {
    val langRoot = firstNonEmptyLanguageType(content, user?.defaultLanguage).contentRoot
    val params = call.parameters.formUrlEncode()
    return "$langRoot${if (params.isNotEmpty()) "?$params" else ""}"
  }

  fun firstNonEmptyLanguageType(content: ReadingBatContent, defaultLanguage: LanguageType? = null) =
    LanguageType.languageTypes(defaultLanguage)
      .firstOrNull { content[it].isNotEmpty() } ?: error("Missing non-empty language")

  fun logToShim(msg: String, logId: String) {
    if (logId.isNotEmpty()) {
      publishLog(msg, logId)
    }
  }

  fun Int.rows(cols: Int) = if (this % cols == 0) this / cols else (this / cols) + 1
}

sealed class PageResult {
  data class Html(val content: String) : PageResult()

  data class Redirect(val url: String) : PageResult()
}
