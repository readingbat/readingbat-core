/*
 * Copyright © 2021 Paul Ambrose (pambrose@mac.com)
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

package com.github.readingbat.server.ws

import com.github.pambrose.common.redis.RedisUtils.withNonNullRedisPool
import com.github.pambrose.common.util.isNotNull
import com.github.pambrose.common.util.simpleClassName
import com.github.readingbat.common.Endpoints.LOAD_ALL_ENDPOINT
import com.github.readingbat.common.Endpoints.LOAD_JAVA_ENDPOINT
import com.github.readingbat.common.Endpoints.LOAD_KOTLIN_ENDPOINT
import com.github.readingbat.common.Endpoints.LOAD_PYTHON_ENDPOINT
import com.github.readingbat.dsl.LanguageType
import com.github.readingbat.dsl.LanguageType.Java
import com.github.readingbat.dsl.LanguageType.Kotlin
import com.github.readingbat.dsl.LanguageType.Python
import com.github.readingbat.dsl.RedisUnavailableException
import com.github.readingbat.server.ReadingBatServer.redisPool
import com.github.readingbat.server.ReadingBatServer.serverSessionId
import com.github.readingbat.server.ws.ChallengeWs.multiServerWsReadChannel
import com.github.readingbat.server.ws.LoggingWs.adminCommandChannel
import com.github.readingbat.server.ws.LoggingWs.logWsReadChannel
import com.github.readingbat.server.ws.PubSubCommandsWs.PubSubTopic.ADMIN_COMMAND
import com.github.readingbat.server.ws.PubSubCommandsWs.PubSubTopic.LIKE_DISLIKE
import com.github.readingbat.server.ws.PubSubCommandsWs.PubSubTopic.LOG_MESSAGE
import com.github.readingbat.server.ws.PubSubCommandsWs.PubSubTopic.USER_ANSWERS
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import mu.KLogging
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPubSub
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.seconds

internal object PubSubCommandsWs : KLogging() {

  enum class PubSubTopic { ADMIN_COMMAND, USER_ANSWERS, LIKE_DISLIKE, LOG_MESSAGE }

  enum class AdminCommand { RESET_CONTENT_DSL, RESET_CACHE, LOAD_CHALLENGE, RUN_GC }

  @Serializable
  enum class LoadChallengeType(@Transient val endPoint: String, val languageTypes: List<LanguageType>) {
    LOAD_JAVA(LOAD_JAVA_ENDPOINT, listOf(Java)),
    LOAD_PYTHON(LOAD_PYTHON_ENDPOINT, listOf(Python)),
    LOAD_KOTLIN(LOAD_KOTLIN_ENDPOINT, listOf(Kotlin)),
    LOAD_ALL(LOAD_ALL_ENDPOINT, listOf(Java, Python, Kotlin));

    fun toJson() = Json.encodeToString(serializer(), this)
  }

  @Serializable
  class AdminCommandData(val command: AdminCommand, val jsonArgs: String, val logId: String) {
    fun toJson() = Json.encodeToString(serializer(), this)
  }

  @Serializable
  class ChallengeAnswerData(val pubSubTopic: PubSubTopic, val target: String, val jsonArgs: String) {
    fun toJson() = Json.encodeToString(serializer(), this)
  }

  @Serializable
  class LogData(val text: String, val logId: String) {
    fun toJson() = Json.encodeToString(serializer(), this)
  }

  private val timeFormat = DateTimeFormatter.ofPattern("H:m:ss.SSS")

  fun Jedis.publishAdminCommand(command: AdminCommand, logId: String, jsonArgs: String = "") {
    publishLog("Dispatching ${command.name} $jsonArgs", logId)
    val adminCommandData = AdminCommandData(command, jsonArgs, logId)
    publish(ADMIN_COMMAND.name, adminCommandData.toJson())
  }

  fun Jedis.publishLog(msg: String, logId: String) {
    val logData = LogData("${LocalDateTime.now().format(timeFormat)} [$serverSessionId] - $msg", logId)
    publish(LOG_MESSAGE.name, logData.toJson())
  }

  fun initThreads() {
    val pubSub =
      object : JedisPubSub() {
        override fun onMessage(channel: String?, message: String?) {
          if (channel.isNotNull() && message.isNotNull())
            runBlocking {
              when (enumValueOf<PubSubTopic>(channel)) {
                ADMIN_COMMAND -> {
                  val data = Json.decodeFromString<AdminCommandData>(message)
                  adminCommandChannel.send(data)
                }
                USER_ANSWERS,
                LIKE_DISLIKE -> {
                  val data = Json.decodeFromString<ChallengeAnswerData>(message)
                  multiServerWsReadChannel.send(data)
                }
                LOG_MESSAGE -> {
                  val data = Json.decodeFromString<LogData>(message)
                  logWsReadChannel.send(data)
                }
              }
            }
        }

        override fun onSubscribe(channel: String?, subscribedChannels: Int) {
          logger.info { "Subscribed to channel: $channel [$subscribedChannels]" }
        }

        override fun onUnsubscribe(channel: String?, subscribedChannels: Int) {
          logger.info { "Unsubscribed from channel: $channel [$subscribedChannels]" }
        }
      }

    newSingleThreadContext("pubsubcommands-ws-redis").executor.execute {
      while (true) {
        try {
          redisPool?.withNonNullRedisPool { redis ->
            redis.subscribe(pubSub, *PubSubTopic.values().map { it.name }.toTypedArray())
          } ?: throw RedisUnavailableException("pubsubWs subscriber")
        } catch (e: Throwable) {
          logger.error(e) { "Exception in pubsubWs subscriber ${e.simpleClassName} ${e.message}" }
          Thread.sleep(10.seconds.inWholeMilliseconds)
        }
      }
    }
  }
}