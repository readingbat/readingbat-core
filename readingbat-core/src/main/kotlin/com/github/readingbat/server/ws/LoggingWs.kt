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

package com.github.readingbat.server.ws

import com.github.pambrose.common.util.simpleClassName
import com.github.readingbat.common.Constants.FLOW_BUFFER_CAPACITY
import com.github.readingbat.common.Endpoints.LOGGING_ENDPOINT
import com.github.readingbat.common.Endpoints.WS_ROOT
import com.github.readingbat.common.Metrics
import com.github.readingbat.dsl.InvalidRequestException
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.server.ReadingBatServer.content
import com.github.readingbat.server.ServerUtils.fetchUser
import com.github.readingbat.server.ws.PubSubCommandsWs.AdminCommand.LOAD_CHALLENGE
import com.github.readingbat.server.ws.PubSubCommandsWs.AdminCommand.RESET_CACHE
import com.github.readingbat.server.ws.PubSubCommandsWs.AdminCommand.RESET_CONTENT_DSL
import com.github.readingbat.server.ws.PubSubCommandsWs.AdminCommand.RUN_GC
import com.github.readingbat.server.ws.PubSubCommandsWs.AdminCommandData
import com.github.readingbat.server.ws.PubSubCommandsWs.LoadChallengeType
import com.github.readingbat.server.ws.PubSubCommandsWs.publishLog
import com.github.readingbat.server.ws.WsCommon.LOG_ID
import com.github.readingbat.server.ws.WsCommon.closeChannels
import com.github.readingbat.server.ws.WsCommon.validateLogContext
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.routing.Routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.util.Collections.synchronizedSet
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlin.time.measureTime

internal object LoggingWs {
  private val logger = KotlinLogging.logger {}
  private val clock = TimeSource.Monotonic
  private val logWsConnections: MutableSet<LogSessionContext> = synchronizedSet(LinkedHashSet<LogSessionContext>())
  val adminCommandFlow by lazy { MutableSharedFlow<AdminCommandData>(extraBufferCapacity = FLOW_BUFFER_CAPACITY) }
  val logWsReadFlow by lazy { MutableSharedFlow<PubSubCommandsWs.LogData>(extraBufferCapacity = FLOW_BUFFER_CAPACITY) }

  data class LogSessionContext(val wsSession: DefaultWebSocketServerSession, val metrics: Metrics) {
    val start = clock.markNow()
    var logId = ""
    val enabled get() = logId.isNotEmpty()
  }

  fun initThreads(contentSrc: () -> ReadingBatContent, resetContentFunc: (String) -> Unit) {
    newSingleThreadContext("logging-ws-adminCommandChannel").executor.execute {
      while (true) {
        runCatching {
          runBlocking {
            adminCommandFlow
              .onStart { logger.info { "Starting to read admin command channel values" } }
              .onCompletion { logger.info { "Finished reading admin command channel values" } }
              .collect { data ->
                val logItem = { s: String -> publishLog(s, data.logId) }

                  when (data.command) {
                    RESET_CONTENT_DSL -> {
                      measureTime { resetContentFunc.invoke(data.logId) }
                        .also { dur ->
                          "DSL content reset in $dur"
                            .also {
                              logger.info { it }
                              logItem(it)
                            }
                        }
                    }

                    RESET_CACHE -> {
                      val content = contentSrc()
                      val cnt = content.functionInfoMap.size
                      content.clearSourcesMap()
                        .let {
                          "Challenge cache reset -- $cnt challenges removed"
                            .also {
                              logger.info { it }
                              logItem(it)
                            }
                        }
                    }

                    LOAD_CHALLENGE -> {
                      val type = Json.decodeFromString<LoadChallengeType>(data.jsonArgs)
                      type.languageTypes
                        .forEach { langType ->
                          content.load().loadChallenges(langType, logItem, "", false)
                            .also {
                              logger.info { it }
                              logItem(it)
                            }
                        }
                    }

                    RUN_GC -> {
                      measureTime { System.gc() }
                        .also { dur ->
                          "Garbage collector invoked for $dur"
                            .also {
                              logger.info { it }
                              logItem(it)
                            }
                        }
                    }
                  }
              }
          }
        }.onFailure { e ->
          logger.error(e) { "Exception in dispatcher ${e.simpleClassName} ${e.message}" }
          Thread.sleep(1.seconds.inWholeMilliseconds)
        }
      }
    }

    newSingleThreadContext("logging-ws-logWsReadChannel").executor.execute {
      while (true) {
        runCatching {
          runBlocking {
            logWsReadFlow
              .onStart { logger.info { "Starting to read log ws channel values" } }
              .onCompletion { logger.info { "Finished reading log ws channel values" } }
              .collect { data ->
                val json = Json.encodeToString(data.text)
                logWsConnections
                  .filter { it.logId == data.logId }
                  .forEach {
                    it.wsSession.outgoing.send(Frame.Text(json))
                  }
              }
          }
        }.onFailure { e ->
          logger.error { "Exception in dispatcher ${e.simpleClassName} ${e.message}" }
          Thread.sleep(1.seconds.inWholeMilliseconds)
        }
      }
    }
  }

  fun Routing.loggingWsEndpoint(metrics: Metrics) {
    webSocket("$WS_ROOT$LOGGING_ENDPOINT/{$LOG_ID}") {
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

          validateLogContext(user)

          incoming
            .consumeAsFlow()
            .mapNotNull { it as? Frame.Text }
            .collect {
              if (logWsContext.logId.isBlank()) {
                logger.debug { "Assigning log id: $logId" }
                logWsContext.logId = logId
              }
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
