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
import com.github.readingbat.common.KeyConstants.KEY_SEP
import com.github.readingbat.common.User.Companion.toUser
import com.github.readingbat.dsl.InvalidConfigurationException
import com.github.readingbat.dsl.LanguageType
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.dsl.isProduction
import com.github.readingbat.pages.DbmsDownPage.dbmsDownPage
import com.github.readingbat.server.ReadingBatServer.pool
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.config.*
import io.ktor.http.*
import io.ktor.routing.*
import io.ktor.sessions.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.runBlocking
import mu.KLogging
import redis.clients.jedis.Jedis

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

  suspend fun PipelineCall.respondWithDbmsCheck(content: ReadingBatContent, block: (redis: Jedis) -> String) =
    try {
      val html =
        pool.withRedisPool { redis ->
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
        pool.withSuspendingRedisPool { redis ->
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
        pool.withSuspendingRedisPool { redis ->
          val user = fetchUser()
          when {
            redis.isNull() -> Constants.DBMS_DOWN.value
            user.isNull() -> "Must be logged in for this function"
            user.isNotAdmin(redis) -> "Not authorized"
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