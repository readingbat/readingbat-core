/*
 * Copyright © 2026 Paul Ambrose (pambrose@mac.com)
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

package com.readingbat.server.routes

import com.pambrose.common.response.respondWith
import com.pambrose.common.util.pluralize
import com.readingbat.common.Endpoints.DELETE_CONTENT_IN_CONTENT_CACHE_ENDPOINT
import com.readingbat.common.Endpoints.GARBAGE_COLLECTOR_ENDPOINT
import com.readingbat.common.Endpoints.RESET_CACHE_ENDPOINT
import com.readingbat.common.Endpoints.RESET_CONTENT_DSL_ENDPOINT
import com.readingbat.common.Metrics
import com.readingbat.dsl.ContentCaches.contentDslCache
import com.readingbat.dsl.ContentCaches.dirCache
import com.readingbat.dsl.ContentCaches.sourceCache
import com.readingbat.dsl.InvalidRequestException
import com.readingbat.dsl.isContentCachingEnabled
import com.readingbat.server.ServerUtils.authenticateAdminUser
import com.readingbat.server.ServerUtils.fetchUser
import com.readingbat.server.ServerUtils.paramMap
import com.readingbat.server.ServerUtils.post
import com.readingbat.server.routes.SysAdminRoutes.logger
import com.readingbat.server.ws.PubSubCommandsWs.AdminCommand.LOAD_CHALLENGE
import com.readingbat.server.ws.PubSubCommandsWs.AdminCommand.RESET_CACHE
import com.readingbat.server.ws.PubSubCommandsWs.AdminCommand.RESET_CONTENT_DSL
import com.readingbat.server.ws.PubSubCommandsWs.AdminCommand.RUN_GC
import com.readingbat.server.ws.PubSubCommandsWs.LoadChallengeType
import com.readingbat.server.ws.PubSubCommandsWs.publishAdminCommand
import com.readingbat.server.ws.PubSubCommandsWs.publishLog
import com.readingbat.server.ws.WsCommon.LOG_ID
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.ApplicationCall
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import kotlin.time.measureTime

/** Logger holder for system admin routes. */
object SysAdminRoutes {
  internal val logger = KotlinLogging.logger {}
}

/**
 * Registers system administrator routes for cache management and server operations.
 *
 * Endpoints include resetting the content DSL, clearing all caches (DSL, source code,
 * directory contents), invoking the garbage collector, and triggering per-language
 * challenge loading. All operations require admin authentication and publish progress
 * messages to the logging WebSocket via [com.readingbat.server.ws.PubSubCommandsWs].
 */
fun Routing.sysAdminRoutes(metrics: Metrics, resetContentFunc: (String) -> Unit) {
  suspend fun ApplicationCall.logId(): String {
    val paranMap = paramMap()
    return paranMap[LOG_ID] ?: throw InvalidRequestException("Missing log id")
  }

  fun deleteAllCaches(logId: String): String {
    fun deleteContentDslCache(): String {
      val cnt = contentDslCache.count()
      contentDslCache.clear()
      return "$cnt content DSLs ${"file".pluralize(cnt)} deleted from cache"
        .also { msg ->
          logger.info { msg }
          publishLog(msg, logId)
        }
    }

    fun deleteSourceCodeCache(): String {
      val cnt = sourceCache.count()
      sourceCache.clear()
      return "$cnt source code ${"file".pluralize(cnt)} deleted from cache"
        .also { msg ->
          logger.info { msg }
          publishLog(msg, logId)
        }
    }

    fun deleteDirContentsCache(): String {
      val cnt = dirCache.count()
      dirCache.clear()
      return "$cnt directory ${"content".pluralize(cnt)} deleted from cache"
        .also { msg ->
          logger.info { msg }
          publishLog(msg, logId)
        }
    }

    return listOf(
      deleteContentDslCache(),
      deleteSourceCodeCache(),
      deleteDirContentsCache(),
    ).joinToString(", ")
  }

  post(RESET_CONTENT_DSL_ENDPOINT, metrics) {
    respondWith {
      val user = fetchUser()
      val logId = call.logId()
      authenticateAdminUser(user) {
        deleteAllCaches(logId)
        // Run on the first server alone initially, to avoid multiple servers caching to redis simultaneously
        if (isContentCachingEnabled()) {
          measureTime { resetContentFunc(logId) }
            .also { dur ->
              "Initial DSL content reset in $dur"
                .also {
                  logger.info { it }
                  publishLog(it, logId)
                }
            }
        }
        publishAdminCommand(RESET_CONTENT_DSL, logId)
        ""
      }
      ""
    }
  }

  post(RESET_CACHE_ENDPOINT, metrics) {
    respondWith {
      val user = fetchUser()
      val logId = call.logId()
      authenticateAdminUser(user) {
        deleteAllCaches(logId)
        publishAdminCommand(RESET_CACHE, logId)
        ""
      }
    }
  }

  post(DELETE_CONTENT_IN_CONTENT_CACHE_ENDPOINT, metrics) {
    respondWith {
      val user = fetchUser()
      val logId = call.logId()
      authenticateAdminUser(user) {
        deleteAllCaches(logId)
      }
    }
  }

  post(GARBAGE_COLLECTOR_ENDPOINT, metrics) {
    respondWith {
      val user = fetchUser()
      val logId = call.logId()
      authenticateAdminUser(user) {
        publishAdminCommand(RUN_GC, logId)
        ""
      }
    }
  }

  LoadChallengeType.entries
    .forEach { type ->
      post(type.endPoint) {
        respondWith {
          val user = fetchUser()
          val logId = call.logId()
          authenticateAdminUser(user) {
            publishAdminCommand(LOAD_CHALLENGE, logId, type.toJson())
            ""
          }
        }
      }
    }
}
