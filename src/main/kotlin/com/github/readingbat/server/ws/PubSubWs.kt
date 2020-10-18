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
import com.github.readingbat.dsl.RedisUnavailableException
import com.github.readingbat.server.ReadingBatServer
import com.github.readingbat.server.ws.ChallengeWs.AnswerData
import com.github.readingbat.server.ws.ChallengeWs.AnswerMessage
import com.github.readingbat.server.ws.ChallengeWs.multiServerWsReadChannel
import com.github.readingbat.server.ws.LogWs.LoadCommand
import com.github.readingbat.server.ws.LogWs.LoadCommandData
import com.github.readingbat.server.ws.LogWs.LogMessage
import com.github.readingbat.server.ws.LogWs.Topic
import com.github.readingbat.server.ws.LogWs.Topic.LIKE_DISLIKE
import com.github.readingbat.server.ws.LogWs.Topic.LOAD_COMMAND
import com.github.readingbat.server.ws.LogWs.Topic.LOG_MESSAGE
import com.github.readingbat.server.ws.LogWs.Topic.USER_ANSWERS
import com.github.readingbat.server.ws.LogWs.adminCommandChannel
import com.github.readingbat.server.ws.LogWs.logWsReadChannel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import mu.KLogging
import redis.clients.jedis.JedisPubSub
import java.util.concurrent.Executors
import kotlin.time.seconds

object PubSubWs : KLogging() {

  init {
    Executors.newSingleThreadExecutor()
      .submit {
        val pubsub =
          object : JedisPubSub() {
            override fun onMessage(channel: String?, message: String?) {
              logger.debug { "On message $channel" }
              if (channel.isNotNull() && message.isNotNull())
                runBlocking {
                  val topic = Topic.valueOf(channel)
                  when (topic) {
                    LOAD_COMMAND -> {
                      val adminCommand = LoadCommand.valueOf(message)
                      adminCommandChannel.send(LoadCommandData(adminCommand))
                    }
                    USER_ANSWERS,
                    LIKE_DISLIKE -> {
                      val answerMessage = Json.decodeFromString<AnswerMessage>(message)
                      multiServerWsReadChannel.send(AnswerData(topic, answerMessage))
                    }
                    LOG_MESSAGE -> {
                      val logMessage = Json.decodeFromString<LogMessage>(message)
                      logWsReadChannel.send(logMessage)
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
            ReadingBatServer.redisPool?.withNonNullRedisPool { redis ->
              redis.subscribe(pubsub, *Topic.values().map { it.name }.toTypedArray())
            } ?: throw RedisUnavailableException("multiServerReadChannel")
          } catch (e: Throwable) {
            logger.error { "Exception in multiServerReadChannel reader ${e.simpleClassName} ${e.message}" }
            Thread.sleep(1.seconds.toLongMilliseconds())
          }
        }
      }


  }
}