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

package com.github.readingbat.server.routes

import com.github.pambrose.common.response.respondWith
import com.github.pambrose.common.util.pluralize
import com.github.readingbat.common.Endpoints.DELETE_CONTENT_IN_CONTENT_CACHE_ENDPOINT
import com.github.readingbat.common.Endpoints.GARBAGE_COLLECTOR_ENDPOINT
import com.github.readingbat.common.Endpoints.RESET_CACHE_ENDPOINT
import com.github.readingbat.common.Endpoints.RESET_CONTENT_DSL_ENDPOINT
import com.github.readingbat.common.Metrics
import com.github.readingbat.dsl.ContentCaches.contentDslCache
import com.github.readingbat.dsl.ContentCaches.dirCache
import com.github.readingbat.dsl.ContentCaches.sourceCache
import com.github.readingbat.dsl.InvalidRequestException
import com.github.readingbat.dsl.isContentCachingEnabled
import com.github.readingbat.server.ReadingBatServer.logger
import com.github.readingbat.server.ServerUtils.authenticateAdminUser
import com.github.readingbat.server.ServerUtils.fetchUser
import com.github.readingbat.server.ServerUtils.paramMap
import com.github.readingbat.server.ServerUtils.post
import com.github.readingbat.server.ws.PubSubCommandsWs.AdminCommand.LOAD_CHALLENGE
import com.github.readingbat.server.ws.PubSubCommandsWs.AdminCommand.RESET_CACHE
import com.github.readingbat.server.ws.PubSubCommandsWs.AdminCommand.RESET_CONTENT_DSL
import com.github.readingbat.server.ws.PubSubCommandsWs.AdminCommand.RUN_GC
import com.github.readingbat.server.ws.PubSubCommandsWs.LoadChallengeType
import com.github.readingbat.server.ws.PubSubCommandsWs.publishAdminCommand
import com.github.readingbat.server.ws.PubSubCommandsWs.publishLog
import com.github.readingbat.server.ws.WsCommon.LOG_ID
import io.ktor.server.application.ApplicationCall
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import kotlin.time.measureTime

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
          measureTime { resetContentFunc.invoke(logId) }
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
