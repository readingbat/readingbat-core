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
import com.github.readingbat.common.Endpoints.DELETE_CONTENT_IN_REDIS_ENDPOINT
import com.github.readingbat.common.Endpoints.GARBAGE_COLLECTOR_ENDPOINT
import com.github.readingbat.common.Endpoints.LOAD_ALL_ENDPOINT
import com.github.readingbat.common.Endpoints.RESET_CACHE_ENDPOINT
import com.github.readingbat.common.Endpoints.RESET_CONTENT_DSL_ENDPOINT
import com.github.readingbat.common.KeyConstants.CONTENT_DSL_KEY
import com.github.readingbat.common.KeyConstants.DIR_CONTENTS_KEY
import com.github.readingbat.common.KeyConstants.SOURCE_CODE_KEY
import com.github.readingbat.common.KeyConstants.keyOf
import com.github.readingbat.common.Metrics
import com.github.readingbat.dsl.InvalidRequestException
import com.github.readingbat.dsl.RedisUnavailableException
import com.github.readingbat.server.ReadingBatServer.logger
import com.github.readingbat.server.ReadingBatServer.redisPool
import com.github.readingbat.server.ServerUtils.authenticateAdminUser
import com.github.readingbat.server.ServerUtils.fetchUser
import com.github.readingbat.server.ServerUtils.paramMap
import com.github.readingbat.server.ServerUtils.post
import com.github.readingbat.server.ws.PubSubCommandsWs.AdminCommand.LOAD_CHALLENGE
import com.github.readingbat.server.ws.PubSubCommandsWs.AdminCommand.RESET_CACHE
import com.github.readingbat.server.ws.PubSubCommandsWs.AdminCommand.RESET_DSL_CONTENT
import com.github.readingbat.server.ws.PubSubCommandsWs.AdminCommand.RUN_GC
import com.github.readingbat.server.ws.PubSubCommandsWs.LoadChallengeType
import com.github.readingbat.server.ws.PubSubCommandsWs.publishAdminCommand
import com.github.readingbat.server.ws.PubSubCommandsWs.publishLog
import com.github.readingbat.server.ws.WsCommon.LOG_ID
import io.ktor.application.*
import io.ktor.routing.*
import redis.clients.jedis.Jedis

internal fun Routing.sysAdminRoutes(metrics: Metrics) {

  suspend fun ApplicationCall.logId(): String {
    val paranMap = paramMap()
    return paranMap[LOG_ID] ?: throw InvalidRequestException("Missing log id")
  }

  fun deleteContentInRedis(logId: String, redis: Jedis): String {
    fun deleteContentDslInRedis(): String {
      val pattern = keyOf(CONTENT_DSL_KEY, "*")
      val keys = redis.scanKeys(pattern).toList()
      val cnt = keys.count()
      keys.forEach { redis.del(it) }
      return "$cnt content DSLs ${"file".pluralize(cnt)} deleted from Redis"
        .also {
          logger.info { it }
          redis.publishLog(it, logId)
        }
    }

    fun deleteDirContentsInRedis(): String {
      val pattern = keyOf(DIR_CONTENTS_KEY, "*")
      val keys = redis.scanKeys(pattern).toList()
      val cnt = keys.count()
      keys.forEach { redis.del(it) }
      return "$cnt directory ${"content".pluralize(cnt)} deleted from Redis"
        .also {
          logger.info { it }
          redis.publishLog(it, logId)
        }
    }

    fun deleteSourceCodeInRedis(): String {
      val pattern = keyOf(SOURCE_CODE_KEY, "*")
      val keys = redis.scanKeys(pattern).toList()
      val cnt = keys.count()
      keys.forEach { redis.del(it) }
      return "$cnt source code ${"file".pluralize(cnt)} deleted from Redis"
        .also {
          logger.info { it }
          redis.publishLog(it, logId)
        }
    }

    return listOf(deleteContentDslInRedis(), deleteDirContentsInRedis(), deleteSourceCodeInRedis())
      .joinToString(", ")
      .also {
        logger.info { "deleteContentInRedis(): $it" }
        redis.publishLog("deleteContentInRedis(): $it", logId)
      }
  }

  post(RESET_CONTENT_DSL_ENDPOINT, metrics) {
    respondWith {
      val user = fetchUser()
      val logId = call.logId()
      authenticateAdminUser(user) {
        redisPool?.withNonNullRedisPool { redis ->
          deleteContentInRedis(logId, redis)
          redis.publishAdminCommand(RESET_DSL_CONTENT, logId)
          ""
        } ?: throw RedisUnavailableException(RESET_CONTENT_DSL_ENDPOINT)
      }
      ""
    }
  }

  post(RESET_CACHE_ENDPOINT, metrics) {
    respondWith {
      val user = fetchUser()
      val logId = call.logId()
      authenticateAdminUser(user) {
        redisPool?.withNonNullRedisPool { redis ->
          deleteContentInRedis(logId, redis)
          redis.publishAdminCommand(RESET_CACHE, logId)
        } ?: throw RedisUnavailableException(RESET_CACHE_ENDPOINT)
        ""
      }
    }
  }

  post(DELETE_CONTENT_IN_REDIS_ENDPOINT, metrics) {
    respondWith {
      val user = fetchUser()
      val logId = call.logId()
      authenticateAdminUser(user) {
        redisPool?.withNonNullRedisPool { redis ->
          deleteContentInRedis(logId, redis)
        } ?: throw RedisUnavailableException(DELETE_CONTENT_IN_REDIS_ENDPOINT)
      }
    }
  }

  LoadChallengeType.values()
    .forEach { type ->
      post(type.endPoint) {
        respondWith {
          val user = fetchUser()
          val logId = call.logId()
          authenticateAdminUser(user) {
            redisPool?.withNonNullRedisPool { redis ->
              redis.publishAdminCommand(LOAD_CHALLENGE, logId, type.toJson())
            } ?: throw RedisUnavailableException(type.endPoint)
            ""
          }
        }
      }
    }

  post(GARBAGE_COLLECTOR_ENDPOINT, metrics) {
    respondWith {
      val user = fetchUser()
      val logId = call.logId()
      authenticateAdminUser(user) {
        redisPool?.withNonNullRedisPool { redis ->
          redis.publishAdminCommand(RUN_GC, logId)
        } ?: throw RedisUnavailableException(LOAD_ALL_ENDPOINT)
        ""
      }
    }
  }
}