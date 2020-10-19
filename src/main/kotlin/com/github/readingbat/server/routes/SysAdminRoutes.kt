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

package com.github.readingbat.server.routes

import com.github.pambrose.common.redis.RedisUtils.scanKeys
import com.github.pambrose.common.redis.RedisUtils.withNonNullRedisPool
import com.github.pambrose.common.response.respondWith
import com.github.pambrose.common.util.pluralize
import com.github.readingbat.common.Constants.REDIS_IS_DOWN
import com.github.readingbat.common.Endpoints.DELETE_CONTENT_IN_REDIS_ENDPOINT
import com.github.readingbat.common.Endpoints.GARBAGE_COLLECTOR_ENDPOINT
import com.github.readingbat.common.Endpoints.LOAD_ALL_ENDPOINT
import com.github.readingbat.common.Endpoints.LOAD_JAVA_ENDPOINT
import com.github.readingbat.common.Endpoints.LOAD_KOTLIN_ENDPOINT
import com.github.readingbat.common.Endpoints.LOAD_PYTHON_ENDPOINT
import com.github.readingbat.common.Endpoints.RESET_CACHE_ENDPOINT
import com.github.readingbat.common.Endpoints.RESET_CONTENT_DSL_ENDPOINT
import com.github.readingbat.common.KeyConstants.CONTENT_DSL_KEY
import com.github.readingbat.common.KeyConstants.DIR_CONTENTS_KEY
import com.github.readingbat.common.KeyConstants.SOURCE_CODE_KEY
import com.github.readingbat.common.KeyConstants.keyOf
import com.github.readingbat.common.Metrics
import com.github.readingbat.dsl.InvalidRequestException
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.dsl.RedisUnavailableException
import com.github.readingbat.pages.SystemAdminPage.systemAdminPage
import com.github.readingbat.server.ReadingBatServer.logger
import com.github.readingbat.server.ReadingBatServer.redisPool
import com.github.readingbat.server.ServerUtils.authenticateAdminUser
import com.github.readingbat.server.ServerUtils.fetchUser
import com.github.readingbat.server.ServerUtils.post
import com.github.readingbat.server.ws.LoggingWs.AdminCommand.LOAD_CHALLENGE
import com.github.readingbat.server.ws.LoggingWs.AdminCommand.RUN_GC
import com.github.readingbat.server.ws.LoggingWs.AdminCommandData
import com.github.readingbat.server.ws.LoggingWs.LoadCommand.LOAD_ALL
import com.github.readingbat.server.ws.LoggingWs.LoadCommand.LOAD_JAVA
import com.github.readingbat.server.ws.LoggingWs.LoadCommand.LOAD_KOTLIN
import com.github.readingbat.server.ws.LoggingWs.LoadCommand.LOAD_PYTHON
import com.github.readingbat.server.ws.LoggingWs.Topic.ADMIN_COMMAND
import com.github.readingbat.server.ws.LoggingWs.log
import com.github.readingbat.server.ws.WsCommon.LOG_ID
import io.ktor.application.*
import io.ktor.request.*
import io.ktor.routing.*
import kotlin.time.measureTime

internal fun Routing.sysAdminRoutes(metrics: Metrics, contentSrc: () -> ReadingBatContent, resetFunc: () -> Unit) {

  fun deleteContentDslInRedis(logId: String) =
    redisPool?.withNonNullRedisPool { redis ->
      val pattern = keyOf(CONTENT_DSL_KEY, "*")
      val keys = redis.scanKeys(pattern).toList()
      val cnt = keys.count()
      keys.forEach { redis.del(it) }
      "$cnt content DSLs ${"file".pluralize(cnt)} deleted from Redis"
        .also {
          logger.info { it }
          redis.log(logId, it)
        }
    } ?: REDIS_IS_DOWN

  fun deleteDirContentsInRedis(logId: String) =
    redisPool?.withNonNullRedisPool { redis ->
      val pattern = keyOf(DIR_CONTENTS_KEY, "*")
      val keys = redis.scanKeys(pattern).toList()
      val cnt = keys.count()
      keys.forEach { redis.del(it) }
      "$cnt directory ${"content".pluralize(cnt)} deleted from Redis"
        .also {
          logger.info { it }
          redis.log(logId, it)
        }
    } ?: REDIS_IS_DOWN

  fun deleteSourceCodeInRedis(logId: String) =
    redisPool?.withNonNullRedisPool { redis ->
      val pattern = keyOf(SOURCE_CODE_KEY, "*")
      val keys = redis.scanKeys(pattern).toList()
      val cnt = keys.count()
      keys.forEach { redis.del(it) }
      "$cnt source code ${"file".pluralize(cnt)} deleted from Redis"
        .also {
          logger.info { it }
          redis.log(logId, it)
        }
    } ?: REDIS_IS_DOWN

  fun deleteContentInRedis(logId: String) =
    listOf(deleteContentDslInRedis(logId), deleteDirContentsInRedis(logId), deleteSourceCodeInRedis(logId))
      .joinToString(", ")
      .also { logger.info { "deleteContentInRedis(): $it" } }

  post(RESET_CONTENT_DSL_ENDPOINT, metrics) {
    respondWith {
      val user = fetchUser()
      val params = call.receiveParameters()
      val paramMap = params.entries().map { it.key to it.value[0] }.toMap()
      val logId = paramMap[LOG_ID] ?: throw InvalidRequestException("Missing log id")
      val msg =
        authenticateAdminUser(user) {
          measureTime {
            deleteContentInRedis(logId)
            // @TODO
            resetFunc.invoke()
          }.let { "DSL content reset in $it".also { logger.info { it } } }
        }
      systemAdminPage(contentSrc(), user, msg)
    }
  }

  post(RESET_CACHE_ENDPOINT, metrics) {
    respondWith {
      val user = fetchUser()
      val params = call.receiveParameters()
      val paramMap = params.entries().map { it.key to it.value[0] }.toMap()
      val logId = paramMap[LOG_ID] ?: throw InvalidRequestException("Missing log id")
      val msg =
        authenticateAdminUser(user) {
          deleteContentInRedis(logId)
          // @TODO
          val content = contentSrc()
          val cnt = content.functionInfoMap.size
          content.clearSourcesMap()
            .let { "Challenge cache reset -- $cnt challenges removed".also { logger.info { it } } }
        }
      systemAdminPage(contentSrc(), user, msg)
    }
  }

  post(DELETE_CONTENT_IN_REDIS_ENDPOINT, metrics) {
    respondWith {
      val user = fetchUser()
      val params = call.receiveParameters()
      val paramMap = params.entries().map { it.key to it.value[0] }.toMap()
      val logId = paramMap[LOG_ID] ?: throw InvalidRequestException("Missing log id")
      authenticateAdminUser(user) {
        deleteContentInRedis(logId)
      }
    }
  }

  listOf(LOAD_JAVA_ENDPOINT to LOAD_JAVA,
         LOAD_PYTHON_ENDPOINT to LOAD_PYTHON,
         LOAD_KOTLIN_ENDPOINT to LOAD_KOTLIN)
    .forEach { pair ->
      post(pair.first) {
        respondWith {
          val user = fetchUser()
          val params = call.receiveParameters()
          val paramMap = params.entries().map { it.key to it.value[0] }.toMap()
          val logId = paramMap[LOG_ID] ?: throw InvalidRequestException("Missing log id")
          authenticateAdminUser(user) {
            redisPool?.withNonNullRedisPool { redis ->
              logger.debug { "Publishing $ADMIN_COMMAND ${pair.second}" }
              val adminCommandData = AdminCommandData(logId, LOAD_CHALLENGE, pair.second.toJson())
              redis.publish(ADMIN_COMMAND.name, adminCommandData.toJson())
            } ?: throw RedisUnavailableException(pair.first)
            ""
          }
        }
      }
    }

  post(LOAD_ALL_ENDPOINT) {
    respondWith {
      val user = fetchUser()
      val params = call.receiveParameters()
      val paramMap = params.entries().map { it.key to it.value[0] }.toMap()
      val logId = paramMap[LOG_ID] ?: throw InvalidRequestException("Missing log id")
      authenticateAdminUser(user) {
        redisPool?.withNonNullRedisPool { redis ->
          val adminCommandData = AdminCommandData(logId, LOAD_CHALLENGE, LOAD_ALL.toJson())
          redis.publish(ADMIN_COMMAND.name, adminCommandData.toJson())
        } ?: throw RedisUnavailableException(LOAD_ALL_ENDPOINT)
        ""
      }
    }
  }

  post(GARBAGE_COLLECTOR_ENDPOINT, metrics) {
    respondWith {
      val user = fetchUser()
      val params = call.receiveParameters()
      val paramMap = params.entries().map { it.key to it.value[0] }.toMap()
      val logId = paramMap[LOG_ID] ?: throw InvalidRequestException("Missing log id")
      authenticateAdminUser(user) {
        redisPool?.withNonNullRedisPool { redis ->
          val adminCommandData = AdminCommandData(logId, RUN_GC, "")
          redis.publish(ADMIN_COMMAND.name, adminCommandData.toJson())
        } ?: throw RedisUnavailableException(LOAD_ALL_ENDPOINT)
        ""
      }
    }
  }
}