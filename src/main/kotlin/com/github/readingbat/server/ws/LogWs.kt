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
import com.github.pambrose.common.util.simpleClassName
import com.github.readingbat.common.Endpoints.LOG_ENDPOINT
import com.github.readingbat.common.Endpoints.WS_ROOT
import com.github.readingbat.common.Metrics
import com.github.readingbat.dsl.InvalidRequestException
import com.github.readingbat.dsl.LanguageType
import com.github.readingbat.dsl.RedisUnavailableException
import com.github.readingbat.server.ReadingBatServer
import com.github.readingbat.server.ReadingBatServer.redisPool
import com.github.readingbat.server.ServerUtils.fetchUser
import com.github.readingbat.server.ws.WsCommon.LOG_ID
import com.github.readingbat.server.ws.WsCommon.closeChannels
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mu.KLogging
import java.util.Collections.synchronizedSet
import java.util.concurrent.Executors.newSingleThreadExecutor
import kotlin.time.TimeSource
import kotlin.time.seconds

internal object LogWs : KLogging() {
  private val clock = TimeSource.Monotonic
  val adminCommandChannel by lazy { BroadcastChannel<LoadCommandData>(Channel.BUFFERED) }
  val logWsReadChannel by lazy { BroadcastChannel<LogMessage>(Channel.BUFFERED) }
  val logWsConnections: MutableSet<LogSessionContext> = synchronizedSet(LinkedHashSet<LogSessionContext>())

  data class LogSessionContext(val wsSession: DefaultWebSocketServerSession, val metrics: Metrics) {
    val start = clock.markNow()
    var logId = ""
    val enabled get() = logId.isNotEmpty()
  }

  @Serializable
  data class LogMessage(val logId: String, val message: String) {
    fun toJson() = Json.encodeToString(serializer(), this)
  }

  enum class Topic { LOAD_COMMAND, USER_ANSWERS, LIKE_DISLIKE, LOG_MESSAGE }

  data class LoadCommandData(val command: LoadCommand)

  enum class LoadCommand(val languageTypes: List<LanguageType>) {
    LOAD_JAVA(listOf(LanguageType.Java)),
    LOAD_PYTHON(listOf(LanguageType.Python)),
    LOAD_KOTLIN(listOf(LanguageType.Kotlin)),
    LOAD_ALL(listOf(LanguageType.Java, LanguageType.Python, LanguageType.Kotlin))
  }

  init {
    newSingleThreadExecutor()
      .submit {
        while (true) {
          try {
            runBlocking {
              adminCommandChannel
                .openSubscription()
                .consumeAsFlow()
                .onStart { logger.info { "Starting to read admin command channel values" } }
                .onCompletion { logger.info { "Finished reading admin command channel values" } }
                .collect { loadCommandData ->
                  redisPool?.withNonNullRedisPool { redis ->
                    loadCommandData.command.languageTypes
                      .forEach {
                        val output = ReadingBatServer.content.get().loadChallenges(it, "", false)
                        redis.publish(Topic.LOG_MESSAGE.name, output)
                      }
                  } ?: throw RedisUnavailableException("adminCommandChannel")
                }
            }
          } catch (e: Throwable) {
            logger.error { "Exception in dispatcher ${e.simpleClassName} ${e.message}" }
            Thread.sleep(1.seconds.toLongMilliseconds())
          }
        }
      }

    newSingleThreadExecutor()
      .submit {
        while (true) {
          try {
            runBlocking {
              logWsReadChannel
                .openSubscription()
                .consumeAsFlow()
                .onStart { logger.info { "Starting to read log ws channel values" } }
                .onCompletion { logger.info { "Finished reading log ws channel values" } }
                .collect { logData ->
                  logWsConnections
                    .filter { it.logId == logData.logId }
                    .forEach {
                      it.wsSession.outgoing.send(Frame.Text(logData.message))
                      logger.debug { "Sent $logData ${logWsConnections.size}" }
                    }
                }
            }
          } catch (e: Throwable) {
            logger.error { "Exception in dispatcher ${e.simpleClassName} ${e.message}" }
            Thread.sleep(1.seconds.toLongMilliseconds())
          }
        }
      }
  }

  fun Routing.logWsEndpoint(metrics: Metrics) {
    webSocket("$WS_ROOT$LOG_ENDPOINT/{$LOG_ID}") {
      val logWsContext = LogSessionContext(this, metrics)
      try {
        outgoing.invokeOnClose {
          logger.debug { "Close received for log websocket:  ${logWsConnections.size}" }
          incoming.cancel()
        }

        logWsConnections += logWsContext

        logger.debug { "Opened log websocket: ${logWsConnections.size}" }

        metrics.measureEndpointRequest("/websocket_log") {
          val p = call.parameters
          val logId = p[LOG_ID] ?: throw InvalidRequestException("Missing log id")
          val user = fetchUser() ?: throw InvalidRequestException("Null user")

          WsCommon.validateLogContext(user)

          incoming
            .consumeAsFlow()
            .mapNotNull { it as? Frame.Text }
            .collect {
              if (logWsContext.logId.isBlank())
                logWsContext.logId = logId
            }
        }
      } finally {
        logWsConnections -= logWsContext
        closeChannels()
        close(CloseReason(CloseReason.Codes.GOING_AWAY, "Client disconnected"))
        logger.debug { "Closed log websocket ${logWsConnections.size}" }
      }
    }
  }
}