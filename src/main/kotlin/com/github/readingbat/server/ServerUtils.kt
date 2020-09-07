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
import com.github.pambrose.common.util.isNull
import com.github.pambrose.common.util.md5
import com.github.readingbat.common.*
import com.github.readingbat.common.Constants.DBMS_DOWN
import com.github.readingbat.common.KeyConstants.KEY_SEP
import com.github.readingbat.common.User.Companion.toUser
import com.github.readingbat.dsl.InvalidConfigurationException
import com.github.readingbat.dsl.LanguageType
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.dsl.isProduction
import com.github.readingbat.pages.DbmsDownPage.dbmsDownPage
import com.github.readingbat.server.ReadingBatServer.redisPool
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.routing.*
import io.ktor.sessions.*
import io.ktor.util.pipeline.*
import io.ktor.websocket.*
import kotlinx.coroutines.runBlocking
import mu.KLogging
import redis.clients.jedis.Jedis

typealias PipelineCall = PipelineContext<Unit, ApplicationCall>

internal fun keyOf(vararg keys: Any) = keys.joinToString(KEY_SEP) { it.toString() }

internal fun md5Of(vararg keys: Any) = keys.joinToString(KEY_SEP) { it.toString() }.md5()

internal object ServerUtils : KLogging() {

  internal fun getVersionDesc(asJson: Boolean = false): String = ReadingBatServer::class.versionDesc(asJson)

  fun PipelineCall.queryParam(key: String, default: String = "") = call.request.queryParameters[key] ?: default

  // Calls for PipelineCall
  fun PipelineCall.fetchEmail() =
    redisPool.withRedisPool { redis ->
      if (redis.isNull())
        "Unknown"
      else
        fetchUser()?.email(redis)?.value ?: "Unknown"
    }

  fun PipelineCall.fetchUser(loginAttempt: Boolean = false): User? =
    fetchPrincipal(loginAttempt)?.userId?.toUser(call.browserSession)

  private fun PipelineCall.fetchPrincipal(loginAttempt: Boolean): UserPrincipal? =
    if (loginAttempt) assignPrincipal() else call.userPrincipal

  private fun PipelineCall.assignPrincipal(): UserPrincipal? =
    call.principal<UserPrincipal>().apply { if (isNotNull()) call.sessions.set(this) }  // Set the cookie

  // Calls for WebSocketServerSession
  fun WebSocketServerSession.fetchEmail() =
    redisPool.withRedisPool { redis ->
      if (redis.isNull())
        "Unknown"
      else
        fetchUser()?.email(redis)?.value ?: "Unknown"
    }

  fun WebSocketServerSession.fetchUser(): User? = call.userPrincipal?.userId?.toUser(call.browserSession)

  // Calls for ApplicationCall
  fun ApplicationCall.fetchEmail() =
    redisPool.withRedisPool { redis ->
      if (redis.isNull())
        "Unknown"
      else
        fetchUser()?.email(redis)?.value ?: "Unknown"
    }

  fun ApplicationCall.fetchUser(): User? = userPrincipal?.userId?.toUser(browserSession)

  suspend fun PipelineCall.respondWithDbmsCheck(content: ReadingBatContent, block: (redis: Jedis) -> String) =
    try {
      val html =
        redisPool.withRedisPool { redis ->
          if (redis.isNull())
            dbmsDownPage(content)
          else
            block.invoke(redis)
        }
      respondWith { html }
    } catch (e: RedirectException) {
      redirectTo { e.redirectUrl }
    }

  suspend fun PipelineCall.respondWithSuspendingDbmsCheck(content: ReadingBatContent,
                                                          block: suspend (redis: Jedis) -> String) =
    try {
      val html =
        redisPool.withSuspendingRedisPool { redis ->
          if (redis.isNull())
            dbmsDownPage(content)
          else
            block.invoke(redis)
        }
      respondWith { html }
    } catch (e: RedirectException) {
      redirectTo { e.redirectUrl }
    }

  suspend fun PipelineCall.authenticatedAction(block: () -> String) =
    when {
      isProduction() ->
        redisPool.withSuspendingRedisPool { redis ->
          val user = fetchUser()
          when {
            redis.isNull() -> DBMS_DOWN.value
            user.isNotValidUser(redis) -> "Must be logged in for this function"
            user.isNotAdminUser(redis) -> "Must be system admin for this function"
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

  fun PipelineCall.defaultLanguageTab(content: ReadingBatContent) =
    LanguageType.languageTypesInOrder
      .asSequence()
      .filter { content[it].isNotEmpty() }
      .map {
        val params = call.parameters.formUrlEncode()
        "${it.contentRoot}${if (params.isNotEmpty()) "?$params" else ""}"
      }
      .firstOrNull() ?: throw InvalidConfigurationException("Missing default language")

  fun Int.rows(cols: Int) = if (this % cols == 0) this / cols else (this / cols) + 1
}

class RedirectException(val redirectUrl: String) : Exception()