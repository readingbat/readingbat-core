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
import com.github.pambrose.common.response.redirectTo
import com.github.pambrose.common.response.respondWith
import com.github.pambrose.common.util.isNull
import com.github.pambrose.common.util.pluralize
import com.github.readingbat.common.Constants.MSG
import com.github.readingbat.common.Endpoints.DELETE_CONTENT_IN_REDIS_ENDPOINT
import com.github.readingbat.common.Endpoints.GARBAGE_COLLECTOR_ENDPOINT
import com.github.readingbat.common.Endpoints.LOAD_JAVA_ENDPOINT
import com.github.readingbat.common.Endpoints.LOAD_KOTLIN_ENDPOINT
import com.github.readingbat.common.Endpoints.LOAD_PYTHON_ENDPOINT
import com.github.readingbat.common.Endpoints.MESSAGE_ENDPOINT
import com.github.readingbat.common.Endpoints.RESET_CACHE_ENDPOINT
import com.github.readingbat.common.Endpoints.RESET_CONTENT_DSL_ENDPOINT
import com.github.readingbat.common.Endpoints.SYSTEM_ADMIN_ENDPOINT
import com.github.readingbat.common.FormFields.RETURN_PARAM
import com.github.readingbat.common.KeyConstants.CONTENT_DSL_KEY
import com.github.readingbat.common.KeyConstants.DIR_CONTENTS_KEY
import com.github.readingbat.common.KeyConstants.SOURCE_CODE_KEY
import com.github.readingbat.common.Metrics
import com.github.readingbat.common.RedisAdmin.scanKeys
import com.github.readingbat.dsl.LanguageType.Java
import com.github.readingbat.dsl.LanguageType.Kotlin
import com.github.readingbat.dsl.LanguageType.Python
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.pages.MessagePage.messagePage
import com.github.readingbat.server.ReadingBatServer.logger
import com.github.readingbat.server.ReadingBatServer.redisPool
import com.github.readingbat.server.ServerUtils.authenticatedAction
import com.github.readingbat.server.ServerUtils.get
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.routing.*
import kotlin.time.measureTime

internal fun Routing.sysAdminRoutes(metrics: Metrics,
                                    contentSrc: () -> ReadingBatContent,
                                    resetContentFunc: () -> Unit) {

  fun deleteContentDslInRedis() =
    redisPool.withRedisPool { redis ->
      if (redis.isNull())
        "Redis is down"
      else {
        val pattern = keyOf(CONTENT_DSL_KEY, "*")
        val keys = redis.scanKeys(pattern).toList()
        val cnt = keys.count()
        keys.forEach { redis.del(it) }
        "$cnt content DSLs ${"file".pluralize(cnt)} deleted from Redis".also { logger.info { it } }
      }
    }

  fun deleteDirContentsInRedis() =
    redisPool.withRedisPool { redis ->
      if (redis.isNull())
        "Redis is down"
      else {
        val pattern = keyOf(DIR_CONTENTS_KEY, "*")
        val keys = redis.scanKeys(pattern).toList()
        val cnt = keys.count()
        keys.forEach { redis.del(it) }
        "$cnt directory ${"content".pluralize(cnt)} deleted from Redis".also { logger.info { it } }
      }
    }

  fun deleteSourceCodeInRedis() =
    redisPool.withRedisPool { redis ->
      if (redis.isNull())
        "Redis is down"
      else {
        val pattern = keyOf(SOURCE_CODE_KEY, "*")
        val keys = redis.scanKeys(pattern).toList()
        val cnt = keys.count()
        keys.forEach { redis.del(it) }
        "$cnt source code ${"file".pluralize(cnt)} deleted from Redis".also { logger.info { it } }
      }
    }

  fun deleteContentInRedis() =
    listOf(deleteContentDslInRedis(),
           deleteDirContentsInRedis(),
           deleteSourceCodeInRedis())
      .joinToString(", ")
      .also { logger.info { "clearContentInRedis(): $it" } }

  get(RESET_CONTENT_DSL_ENDPOINT, metrics) {
    val msg =
      authenticatedAction {
        measureTime {
          deleteContentInRedis()
          resetContentFunc.invoke()
        }
          .let {
            "DSL content reset in $it".also { logger.info { it } }
          }
      }
    redirectTo { "$MESSAGE_ENDPOINT?$MSG=$msg&$RETURN_PARAM=$SYSTEM_ADMIN_ENDPOINT" }
  }

  get(RESET_CACHE_ENDPOINT, metrics) {
    val msg =
      authenticatedAction {
        deleteContentInRedis()
        val content = contentSrc()
        val cnt = content.functionInfoMap.size
        content.clearSourcesMap()
          .let {
            "Challenge cache reset -- $cnt challenges removed".also { logger.info { it } }
          }
      }
    redirectTo { "$MESSAGE_ENDPOINT?$MSG=$msg&$RETURN_PARAM=$SYSTEM_ADMIN_ENDPOINT" }
  }

  get(DELETE_CONTENT_IN_REDIS_ENDPOINT, metrics) {
    val msg = authenticatedAction { deleteContentInRedis() }
    redirectTo { "$MESSAGE_ENDPOINT?$MSG=$msg&$RETURN_PARAM=$SYSTEM_ADMIN_ENDPOINT" }
  }

  listOf(LOAD_JAVA_ENDPOINT to Java,
         LOAD_PYTHON_ENDPOINT to Python,
         LOAD_KOTLIN_ENDPOINT to Kotlin)
    .forEach { pair ->
      get(pair.first) {
        val msg =
          authenticatedAction {
            contentSrc().loadChallenges(call.request.origin.preUri, pair.second, false)
          }
        redirectTo { "$MESSAGE_ENDPOINT?$MSG=$msg&$RETURN_PARAM=$SYSTEM_ADMIN_ENDPOINT" }
      }
    }

  get(GARBAGE_COLLECTOR_ENDPOINT, metrics) {
    val msg =
      authenticatedAction {
        System.gc()
        "Garbage collector invoked".also { logger.info { it } }
      }
    redirectTo { "$MESSAGE_ENDPOINT?$MSG=$msg&$RETURN_PARAM=$SYSTEM_ADMIN_ENDPOINT" }
  }

  get(MESSAGE_ENDPOINT, metrics) {
    respondWith { messagePage(contentSrc()) }
  }
}

// TODO Move this to utils
private val RequestConnectionPoint.preUri get() = "$scheme://$host:$port"
