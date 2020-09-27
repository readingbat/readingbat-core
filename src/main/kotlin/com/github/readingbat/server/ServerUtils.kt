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
import com.github.pambrose.common.redis.RedisUtils.withSuspendingRedisPool
import com.github.pambrose.common.response.redirectTo
import com.github.pambrose.common.response.respondWith
import com.github.pambrose.common.util.Version.Companion.versionDesc
import com.github.pambrose.common.util.isNotNull
import com.github.readingbat.common.*
import com.github.readingbat.common.Constants.UNKNOWN
import com.github.readingbat.common.User.Companion.toUser
import com.github.readingbat.dsl.*
import com.github.readingbat.server.ReadingBatServer.redisPool
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.routing.*
import io.ktor.sessions.*
import io.ktor.util.pipeline.*
import io.ktor.websocket.*
import kotlinx.coroutines.runBlocking
import mu.KLogging
import redis.clients.jedis.Jedis

typealias PipelineCall = PipelineContext<Unit, ApplicationCall>

internal object ServerUtils : KLogging() {

  internal fun getVersionDesc(asJson: Boolean = false): String = ReadingBatServer::class.versionDesc(asJson)

  fun PipelineCall.queryParam(key: String, default: String = "") = call.request.queryParameters[key] ?: default

  fun PipelineCall.fetchUser(loginAttempt: Boolean = false): User? =
    fetchPrincipal(loginAttempt)?.userId?.toUser(call.browserSession)

  private fun PipelineCall.fetchPrincipal(loginAttempt: Boolean): UserPrincipal? =
    if (loginAttempt) assignPrincipal() else call.userPrincipal

  private fun PipelineCall.assignPrincipal(): UserPrincipal? =
    call.principal<UserPrincipal>().apply { if (isNotNull()) call.sessions.set(this) }  // Set the cookie

  fun WebSocketServerSession.fetchUser(): User? = call.userPrincipal?.userId?.toUser(call.browserSession)

  // Calls for ApplicationCall
  fun ApplicationCall.fetchEmail() = fetchUser()?.email?.value ?: UNKNOWN
  /*
  redisPool?.withRedisPool { redis ->
      if (redis.isNull())
        UNKNOWN
      else
      fetchUser()?.email(redis)?.value ?: UNKNOWN
    } ?: UNKNOWN

   */

  fun ApplicationCall.fetchUser(): User? = userPrincipal?.userId?.toUser(browserSession)

  suspend fun PipelineCall.respondWithRedisCheck(block: (redis: Jedis) -> String) =
    try {
      val html =
        redisPool?.withRedisPool { redis ->
          block.invoke(redis ?: throw RedisUnavailableException(call.request.uri))
        } ?: throw RedisUnavailableException(call.request.uri)
      respondWith { html }
    } catch (e: RedirectException) {
      redirectTo { e.redirectUrl }
    }

  suspend fun PipelineCall.respondWithSuspendingRedisCheck(block: suspend (redis: Jedis) -> String) =
    try {
      val html =
        redisPool?.withSuspendingRedisPool { redis ->
          block.invoke(redis ?: throw RedisUnavailableException(call.request.uri))
        } ?: throw RedisUnavailableException(call.request.uri)
      respondWith { html }
    } catch (e: RedirectException) {
      redirectTo { e.redirectUrl }
    }

  fun authenticateAdminUser(user: User?, redis: Jedis, block: () -> String): String =
    when {
      isProduction() -> {
        when {
          user.isNotValidUser(redis) -> throw InvalidRequestException("Must be logged in for this function")
          user.isNotAdminUser(redis) -> throw InvalidRequestException("Must be system admin for this function")
          else -> block.invoke()
        }
      }
      else -> block.invoke()
    }

  @ContextDsl
  fun Route.get(path: String, metrics: Metrics, body: PipelineInterceptor<Unit, ApplicationCall>) =
    route(path, HttpMethod.Get) {
      runBlocking {
        metrics.measureEndpointRequest(path) { handle(body) }
      }
    }

  fun PipelineCall.defaultLanguageTab(content: ReadingBatContent, user: User?): String {
    val langRoot = firstNonEmptyLanguageType(content, user?.defaultLanguage).contentRoot
    val params = call.parameters.formUrlEncode()
    return "$langRoot${if (params.isNotEmpty()) "?$params" else ""}"
  }


  fun firstNonEmptyLanguageType(content: ReadingBatContent, defaultLanguage: LanguageType? = null) =
    LanguageType.languageTypes(defaultLanguage)
      .asSequence()
      .filter { content[it].isNotEmpty() }
      .firstOrNull() ?: throw InvalidConfigurationException("Missing non-empty language")

  fun Int.rows(cols: Int) = if (this % cols == 0) this / cols else (this / cols) + 1
}

class RedirectException(val redirectUrl: String) : Exception()