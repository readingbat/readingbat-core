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
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import mu.KLogging
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPubSub
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors.newSingleThreadExecutor
import kotlin.time.seconds

internal object PubSubCommandsWs : KLogging() {

  enum class PubSubTopic { ADMIN_COMMAND, USER_ANSWERS, LIKE_DISLIKE, LOG_MESSAGE }

  enum class AdminCommand { RESET_DSL_CONTENT, RESET_CACHE, LOAD_CHALLENGE, RUN_GC }

  @Serializable
  class AdminCommandData(val command: AdminCommand, val jsonArgs: String, val logId: String) {
    fun toJson() = Json.encodeToString(serializer(), this)
  }

  @Serializable
  class LogData(val message: String, val logId: String) {
    fun toJson() = Json.encodeToString(serializer(), this)
  }

  @Serializable
  class ChallengeAnswerData(val pubSubTopic: PubSubTopic, val target: String, val jsonArgs: String) {
    fun toJson() = Json.encodeToString(serializer(), this)
  }

  @Serializable
  enum class LoadChallengeType(@Transient val endPoint: String, val languageTypes: List<LanguageType>) {
    LOAD_JAVA(LOAD_JAVA_ENDPOINT, listOf(Java)),
    LOAD_PYTHON(LOAD_PYTHON_ENDPOINT, listOf(Python)),
    LOAD_KOTLIN(LOAD_KOTLIN_ENDPOINT, listOf(Kotlin)),
    LOAD_ALL(LOAD_ALL_ENDPOINT, listOf(Java, Python, Kotlin));

    fun toJson() = Json.encodeToString(serializer(), this)
  }

  private val timeFormat = DateTimeFormatter.ofPattern("H:m:ss.SSS")

  fun Jedis.publishAdminCommand(command: AdminCommand, logId: String, jsonArgs: String = "") {
    val adminCommandData = AdminCommandData(command, jsonArgs, logId)
    publish(ADMIN_COMMAND.name, adminCommandData.toJson())
  }

  fun Jedis.publishLog(msg: String, logId: String) {
    val logData = LogData("${LocalDateTime.now().format(timeFormat)} [$serverSessionId] - $msg", logId)
    publish(LOG_MESSAGE.name, logData.toJson())
  }

  init {
    newSingleThreadExecutor()
      .submit {
        val pubsub =
          object : JedisPubSub() {
            override fun onMessage(channel: String?, message: String?) {
              logger.debug { "On channel $channel $message" }
              if (channel.isNotNull() && message.isNotNull())
                runBlocking {
                  when (PubSubTopic.valueOf(channel)) {
                    ADMIN_COMMAND -> {
                      val adminCommandData = Json.decodeFromString<AdminCommandData>(message)
                      adminCommandChannel.send(adminCommandData)
                    }
                    USER_ANSWERS,
                    LIKE_DISLIKE -> {
                      val answerMessage = Json.decodeFromString<ChallengeAnswerData>(message)
                      multiServerWsReadChannel.send(answerMessage)
                    }
                    LOG_MESSAGE -> {
                      val logData = Json.decodeFromString<LogData>(message)
                      logWsReadChannel.send(logData)
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

        while (true) {
          try {
            redisPool?.withNonNullRedisPool { redis ->
              redis.subscribe(pubsub, *PubSubTopic.values().map { it.name }.toTypedArray())
            } ?: throw RedisUnavailableException("pubsubWs subscriber")
          } catch (e: Throwable) {
            logger.error(e) { "Exception in pubsubWs subscriber ${e.simpleClassName} ${e.message}" }
            Thread.sleep(1.seconds.toLongMilliseconds())
          }
        }
      }
  }
}