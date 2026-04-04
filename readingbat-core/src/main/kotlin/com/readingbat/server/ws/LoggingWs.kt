/*
 * Copyright © 2026 Paul Ambrose (pambrose@mac.com)
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

package com.readingbat.server.ws

import com.pambrose.common.util.simpleClassName
import com.readingbat.common.Constants.FLOW_BUFFER_CAPACITY
import com.readingbat.common.Endpoints.LOGGING_ENDPOINT
import com.readingbat.common.Endpoints.WS_ROOT
import com.readingbat.common.Metrics
import com.readingbat.dsl.InvalidRequestException
import com.readingbat.dsl.ReadingBatContent
import com.readingbat.server.ReadingBatServer.content
import com.readingbat.server.ServerUtils.fetchUser
import com.readingbat.server.ws.PubSubCommandsWs.AdminCommand.LOAD_CHALLENGE
import com.readingbat.server.ws.PubSubCommandsWs.AdminCommand.RESET_CACHE
import com.readingbat.server.ws.PubSubCommandsWs.AdminCommand.RESET_CONTENT_DSL
import com.readingbat.server.ws.PubSubCommandsWs.AdminCommand.RUN_GC
import com.readingbat.server.ws.PubSubCommandsWs.AdminCommandData
import com.readingbat.server.ws.PubSubCommandsWs.LoadChallengeType
import com.readingbat.server.ws.PubSubCommandsWs.publishLog
import com.readingbat.server.ws.WsCommon.LOG_ID
import com.readingbat.server.ws.WsCommon.closeChannels
import com.readingbat.server.ws.WsCommon.validateLogContext
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.routing.Routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.Collections.synchronizedSet
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlin.time.measureTime

/**
 * WebSocket endpoint and background processors for real-time admin logging.
 *
 * Provides two main capabilities:
 * 1. An admin command processor that listens on [adminCommandFlow] and executes operations
 *    such as DSL content reset, cache clearing, challenge loading, and garbage collection.
 * 2. A logging WebSocket endpoint that streams timestamped log messages to connected admin
 *    clients, filtered by a unique log ID so multiple admin sessions can run independently.
 */
internal object LoggingWs {
  private val logger = KotlinLogging.logger {}
  private val clock = TimeSource.Monotonic
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val logWsConnections: MutableSet<LogSessionContext> = synchronizedSet(LinkedHashSet<LogSessionContext>())
  val adminCommandFlow by lazy { MutableSharedFlow<AdminCommandData>(extraBufferCapacity = FLOW_BUFFER_CAPACITY) }
  val logWsReadFlow by lazy { MutableSharedFlow<PubSubCommandsWs.LogData>(extraBufferCapacity = FLOW_BUFFER_CAPACITY) }

  data class LogSessionContext(val wsSession: DefaultWebSocketServerSession, val metrics: Metrics) {
    val start = clock.markNow()
    var logId = ""
    val enabled get() = logId.isNotEmpty()
  }

  /** Launches background coroutines that process admin commands and dispatch log messages to WebSocket clients. */
  fun initThreads(contentSrc: () -> ReadingBatContent, resetContentFunc: (String) -> Unit) {
    scope.launch(CoroutineName("logging-ws-adminCommandChannel")) {
      while (isActive) {
        runCatching {
          adminCommandFlow
            .onStart { logger.info { "Starting to read admin command channel values" } }
            .onCompletion { logger.info { "Finished reading admin command channel values" } }
            .collect { data ->
              val logItem = { s: String -> publishLog(s, data.logId) }

                when (data.command) {
                  RESET_CONTENT_DSL -> {
                    measureTime { resetContentFunc(data.logId) }
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
        }.onFailure { e ->
          logger.error(e) { "Exception in dispatcher ${e.simpleClassName} ${e.message}" }
          delay(1.seconds)
        }
      }
    }

    scope.launch(CoroutineName("logging-ws-logWsReadChannel")) {
      while (isActive) {
        runCatching {
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
        }.onFailure { e ->
          logger.error { "Exception in dispatcher ${e.simpleClassName} ${e.message}" }
          delay(1.seconds)
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
