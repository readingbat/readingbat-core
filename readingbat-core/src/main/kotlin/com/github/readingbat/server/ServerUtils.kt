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

import com.github.pambrose.common.redis.RedisUtils.withNonNullRedisPool
import com.github.pambrose.common.redis.RedisUtils.withRedisPool
import com.github.pambrose.common.redis.RedisUtils.withSuspendingRedisPool
import com.github.pambrose.common.response.redirectTo
import com.github.pambrose.common.response.respondWith
import com.github.pambrose.common.util.Version.Companion.versionDesc
import com.github.pambrose.common.util.isNotNull
import com.github.readingbat.common.Metrics
import com.github.readingbat.common.User
import com.github.readingbat.common.User.Companion.toUser
import com.github.readingbat.common.UserPrincipal
import com.github.readingbat.common.browserSession
import com.github.readingbat.common.isNotAdminUser
import com.github.readingbat.common.isNotValidUser
import com.github.readingbat.common.userPrincipal
import com.github.readingbat.dsl.InvalidRequestException
import com.github.readingbat.dsl.LanguageType
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.dsl.RedisUnavailableException
import com.github.readingbat.dsl.isProduction
import com.github.readingbat.dsl.isRedisEnabled
import com.github.readingbat.server.Email.Companion.UNKNOWN_EMAIL
import com.github.readingbat.server.ReadingBatServer.redisPool
import com.github.readingbat.server.ws.PubSubCommandsWs.publishLog
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpMethod
import io.ktor.http.formUrlEncode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import io.ktor.server.request.receiveParameters
import io.ktor.server.request.uri
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.RoutingHandler
import io.ktor.server.routing.route
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.utils.io.KtorDsl
import kotlinx.coroutines.runBlocking
import redis.clients.jedis.Jedis

@Suppress("unused")
internal object ServerUtils {
  private val logger = KotlinLogging.logger {}

  fun getVersionDesc(asJson: Boolean = false): String = ReadingBatServer::class.versionDesc(asJson)

  fun RoutingContext.queryParam(key: String, default: String = "") = call.request.queryParameters[key] ?: default

  fun RoutingContext.fetchUser(loginAttempt: Boolean = false): User? =
    fetchPrincipal(loginAttempt)?.userId?.toUser(call.browserSession)

  private fun RoutingContext.fetchPrincipal(loginAttempt: Boolean): UserPrincipal? =
    if (loginAttempt) assignPrincipal() else call.userPrincipal

  private fun RoutingContext.assignPrincipal(): UserPrincipal? =
    call.principal<UserPrincipal>().apply { if (isNotNull()) call.sessions.set(this) }  // Set the cookie

  fun WebSocketServerSession.fetchUser(): User? =
    call.userPrincipal?.userId?.toUser(call.browserSession)

  suspend fun ApplicationCall.paramMap() = receiveParameters().entries().associate { it.key to it.value[0] }

  fun ApplicationCall.fetchUser(): User? = userPrincipal?.userId?.toUser(browserSession)

  fun ApplicationCall.fetchUserDbmsIdFromCache() =
    userPrincipal?.userId?.let { User.fetchUserDbmsIdFromCache(it) } ?: -1L

  fun ApplicationCall.fetchEmailFromCache() =
    userPrincipal?.userId?.let { User.fetchEmailFromCache(it) } ?: UNKNOWN_EMAIL

  suspend fun RoutingContext.respondWithRedirect(block: () -> String) =
    try {
      // Do this outside of respondWith{} so that exceptions will be caught
      val html = block.invoke()
      respondWith { html }
    } catch (e: RedirectException) {
      redirectTo { e.redirectUrl }
    }

  suspend fun RoutingContext.respondWithSuspendingRedirect(block: suspend () -> String) =
    try {
      val html = block.invoke()
      respondWith { html }
    } catch (e: RedirectException) {
      redirectTo { e.redirectUrl }
    }

  suspend fun RoutingContext.respondWithRedisCheck(block: (Jedis) -> String) =
    try {
      val html =
        redisPool?.withRedisPool { redis ->
          block.invoke(redis ?: throw RedisUnavailableException(call.request.uri))
        } ?: throw RedisUnavailableException(call.request.uri)
      respondWith { html }
    } catch (e: RedirectException) {
      redirectTo { e.redirectUrl }
    }

  suspend fun RoutingContext.respondWithSuspendingRedisCheck(block: suspend (Jedis) -> String) =
    try {
      val html =
        redisPool?.withSuspendingRedisPool { redis ->
          block.invoke(redis ?: throw RedisUnavailableException(call.request.uri))
        } ?: throw RedisUnavailableException(call.request.uri)
      respondWith { html }
    } catch (e: RedirectException) {
      redirectTo { e.redirectUrl }
    }

  fun authenticateAdminUser(user: User?, block: () -> String): String =
    when {
      isProduction() -> {
        when {
          user.isNotValidUser() -> throw InvalidRequestException("Must be logged in for this function")
          user.isNotAdminUser() -> throw InvalidRequestException("Must be system admin for this function")
          else -> block.invoke()
        }
      }

      else -> block.invoke()
    }

  @KtorDsl
  fun Route.get(path: String, metrics: Metrics, body: RoutingHandler) =
    route(path, HttpMethod.Get) {
      runBlocking {
        metrics.measureEndpointRequest(path) { handle(body) }
      }
    }

  @KtorDsl
  fun Route.post(path: String, metrics: Metrics, body: RoutingHandler) =
    route(path, HttpMethod.Post) {
      runBlocking {
        metrics.measureEndpointRequest(path) { handle(body) }
      }
    }

  fun RoutingContext.defaultLanguageTab(content: ReadingBatContent, user: User?): String {
    val langRoot = firstNonEmptyLanguageType(content, user?.defaultLanguage).contentRoot
    val params = call.parameters.formUrlEncode()
    return "$langRoot${if (params.isNotEmpty()) "?$params" else ""}"
  }

  fun firstNonEmptyLanguageType(content: ReadingBatContent, defaultLanguage: LanguageType? = null) =
    LanguageType.languageTypes(defaultLanguage)
      .asSequence()
      .filter { content[it].isNotEmpty() }
      .firstOrNull() ?: error("Missing non-empty language")

  fun logToRedis(msg: String, logId: String) {
    if (logId.isNotEmpty() && isRedisEnabled()) {
      redisPool?.withNonNullRedisPool { redis ->
        redis.publishLog(msg, logId)
      }
    }
  }

  fun Int.rows(cols: Int) = if (this % cols == 0) this / cols else (this / cols) + 1
}

class RedirectException(val redirectUrl: String) : Exception()
