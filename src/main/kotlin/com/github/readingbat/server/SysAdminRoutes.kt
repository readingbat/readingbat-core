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

import com.github.pambrose.common.redis.RedisUtils.scanKeys
import com.github.pambrose.common.redis.RedisUtils.withRedisPool
import com.github.pambrose.common.response.respondWith
import com.github.pambrose.common.response.uriPrefix
import com.github.pambrose.common.util.isNull
import com.github.pambrose.common.util.pluralize
import com.github.readingbat.common.CommonUtils.keyOf
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
import com.github.readingbat.common.Metrics
import com.github.readingbat.dsl.LanguageType
import com.github.readingbat.dsl.LanguageType.Java
import com.github.readingbat.dsl.LanguageType.Kotlin
import com.github.readingbat.dsl.LanguageType.Python
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.pages.SystemAdminPage.systemAdminPage
import com.github.readingbat.server.ReadingBatServer.logger
import com.github.readingbat.server.ReadingBatServer.redisPool
import com.github.readingbat.server.ServerUtils.authenticateAdminUser
import com.github.readingbat.server.ServerUtils.fetchUser
import com.github.readingbat.server.ServerUtils.get
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.routing.*
import kotlin.time.measureTime

internal fun Routing.sysAdminRoutes(metrics: Metrics, contentSrc: () -> ReadingBatContent, resetFunc: () -> Unit) {

  fun deleteContentDslInRedis() =
    redisPool?.withRedisPool { redis ->
      if (redis.isNull())
        REDIS_IS_DOWN
      else {
        val pattern = keyOf(CONTENT_DSL_KEY, "*")
        val keys = redis.scanKeys(pattern).toList()
        val cnt = keys.count()
        keys.forEach { redis.del(it) }
        "$cnt content DSLs ${"file".pluralize(cnt)} deleted from Redis".also { logger.info { it } }
      }
    } ?: REDIS_IS_DOWN

  fun deleteDirContentsInRedis() =
    redisPool?.withRedisPool { redis ->
      if (redis.isNull())
        REDIS_IS_DOWN
      else {
        val pattern = keyOf(DIR_CONTENTS_KEY, "*")
        val keys = redis.scanKeys(pattern).toList()
        val cnt = keys.count()
        keys.forEach { redis.del(it) }
        "$cnt directory ${"content".pluralize(cnt)} deleted from Redis".also { logger.info { it } }
      }
    } ?: REDIS_IS_DOWN

  fun deleteSourceCodeInRedis() =
    redisPool?.withRedisPool { redis ->
      if (redis.isNull())
        REDIS_IS_DOWN
      else {
        val pattern = keyOf(SOURCE_CODE_KEY, "*")
        val keys = redis.scanKeys(pattern).toList()
        val cnt = keys.count()
        keys.forEach { redis.del(it) }
        "$cnt source code ${"file".pluralize(cnt)} deleted from Redis".also { logger.info { it } }
      }
    } ?: REDIS_IS_DOWN

  fun deleteContentInRedis() =
    listOf(deleteContentDslInRedis(),
           deleteDirContentsInRedis(),
           deleteSourceCodeInRedis())
      .joinToString(", ")
      .also { logger.info { "clearContentInRedis(): $it" } }

  get(RESET_CONTENT_DSL_ENDPOINT, metrics) {
    respondWith {
      val user = fetchUser()
      val msg =
        authenticateAdminUser(user) {
          measureTime {
            deleteContentInRedis()
            resetFunc.invoke()
          }.let { "DSL content reset in $it".also { logger.info { it } } }
        }
      systemAdminPage(contentSrc(), user, msg)
    }
  }

  get(RESET_CACHE_ENDPOINT, metrics) {
    respondWith {
      val user = fetchUser()
      val msg =
        authenticateAdminUser(user) {
          deleteContentInRedis()
          val content = contentSrc()
          val cnt = content.functionInfoMap.size
          content.clearSourcesMap()
            .let {
              "Challenge cache reset -- $cnt challenges removed".also { logger.info { it } }
            }
        }
      systemAdminPage(contentSrc(), user, msg)
    }
  }

  get(DELETE_CONTENT_IN_REDIS_ENDPOINT, metrics) {
    respondWith {
      val user = fetchUser()
      val msg = authenticateAdminUser(user) { deleteContentInRedis() }
      systemAdminPage(contentSrc(), user, msg)
    }
  }

  listOf(LOAD_JAVA_ENDPOINT to Java,
         LOAD_PYTHON_ENDPOINT to Python,
         LOAD_KOTLIN_ENDPOINT to Kotlin)
    .forEach { pair ->
      get(pair.first) {
        respondWith {
          val user = fetchUser()
          val msg =
            authenticateAdminUser(user) {
              contentSrc().loadChallenges(call.request.origin.uriPrefix, pair.second, false)
            }
          systemAdminPage(contentSrc(), user, msg)
        }
      }
    }

  get(LOAD_ALL_ENDPOINT) {
    respondWith {
      val user = fetchUser()
      val msg =
        authenticateAdminUser(user) {
          LanguageType.values()
            .joinToString(", ") { contentSrc().loadChallenges(call.request.origin.uriPrefix, it, false) }
        }
      systemAdminPage(contentSrc(), user, msg)
    }
  }

  get(GARBAGE_COLLECTOR_ENDPOINT, metrics) {
    respondWith {
      val user = fetchUser()
      val msg =
        authenticateAdminUser(user) {
          val dur = measureTime { System.gc() }
          "Garbage collector invoked for $dur".also { logger.info { it } }
        }
      systemAdminPage(contentSrc(), user, msg)
    }
  }
}