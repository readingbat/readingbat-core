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

import com.github.pambrose.common.time.format
import com.github.pambrose.common.util.simpleClassName
import com.github.readingbat.common.ClassCode
import com.github.readingbat.common.Constants.FLOW_BUFFER_CAPACITY
import com.github.readingbat.common.Constants.PING_CODE
import com.github.readingbat.common.Endpoints.CHALLENGE_ENDPOINT
import com.github.readingbat.common.Endpoints.WS_ROOT
import com.github.readingbat.common.KeyConstants.keyOf
import com.github.readingbat.common.Metrics
import com.github.readingbat.dsl.InvalidRequestException
import com.github.readingbat.dsl.agentLaunchId
import com.github.readingbat.dsl.isMultiServerEnabled
import com.github.readingbat.server.ServerUtils.fetchUser
import com.github.readingbat.server.ws.PubSubCommandsWs.ChallengeAnswerData
import com.github.readingbat.server.ws.PubSubCommandsWs.publishShim
import com.github.readingbat.server.ws.WsCommon.CHALLENGE_MD5
import com.github.readingbat.server.ws.WsCommon.CLASS_CODE
import com.github.readingbat.server.ws.WsCommon.closeChannels
import com.github.readingbat.server.ws.WsCommon.validateContext
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.routing.Routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.CloseReason.Codes.GOING_AWAY
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Collections.synchronizedSet
import kotlin.concurrent.timer
import kotlin.math.max
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

internal object ChallengeWs {
  private val logger = KotlinLogging.logger {}
  private val clock = TimeSource.Monotonic
  val singleServerWsFlow by lazy {
    MutableSharedFlow<ChallengeAnswerData>(extraBufferCapacity = FLOW_BUFFER_CAPACITY)
  }
  val multiServerWsWriteFlow by lazy {
    MutableSharedFlow<ChallengeAnswerData>(extraBufferCapacity = FLOW_BUFFER_CAPACITY)
  }
  val multiServerWsReadFlow by lazy {
    MutableSharedFlow<ChallengeAnswerData>(extraBufferCapacity = FLOW_BUFFER_CAPACITY)
  }
  val answerWsConnections: MutableSet<AnswerSessionContext> = synchronizedSet(LinkedHashSet<AnswerSessionContext>())
  var maxAnswerWsConnections = 0

  private fun assignMaxConnections() {
    synchronized(this) {
      maxAnswerWsConnections = max(maxAnswerWsConnections, answerWsConnections.size)
    }
  }

  data class AnswerSessionContext(val wsSession: DefaultWebSocketServerSession, val metrics: Metrics) {
    val start = clock.markNow()
    var targetName = ""
    val enabled get() = targetName.isNotEmpty()
  }

  @Serializable
  class PingMessage(val msg: String) {
    @Required
    val type: String = PING_CODE

    fun toJson() = Json.encodeToString(serializer(), this)
  }

  init {
    logger.info { "Initializing ChallengeWs" }

    timer("pinger", false, 0L, 1.seconds.inWholeMilliseconds) {
      for (sessionContext in answerWsConnections) {
        if (sessionContext.enabled) {
          {
            runCatching {
              val elapsed = sessionContext.start.elapsedNow().format()
              val json = PingMessage(elapsed).toJson()
              runBlocking {
                sessionContext.wsSession.outgoing.send(Frame.Text(json))
              }
            }.onFailure { e ->
              logger.error { "Exception in pinger ${e.simpleClassName} ${e.message}" }
            }
          }
        }
      }
    }

    if (isMultiServerEnabled()) {
      newSingleThreadContext("multiServerWsWriteChannel").executor.execute {
        while (true) {
          runCatching {
            runBlocking {
              multiServerWsWriteFlow
                .onStart { logger.info { "Starting to read multi-server writer ws channel values" } }
                .onCompletion { logger.info { "Finished reading multi-server writer ws channel values" } }
                .collect { data ->
                  publishShim(data.pubSubTopic.name, data.toJson())
                }
            }
          }.onFailure { e ->
            logger.error { "Exception in challenge ws writer: ${e.simpleClassName} ${e.message}" }
            Thread.sleep(1.seconds.inWholeMilliseconds)
          }
        }
      }
    }

    newSingleThreadContext("answerWsConnections").executor.execute {
      while (true) {
        runCatching {
          runBlocking {
            (if (isMultiServerEnabled()) multiServerWsReadFlow else singleServerWsFlow)
              .onStart { logger.info { "Starting to read challenge ws channel values" } }
              .onCompletion { logger.info { "Finished reading challenge ws channel values" } }
              .collect { data ->
                answerWsConnections
                  .filter { it.targetName == data.target }
                  .forEach {
                    it.metrics.wsStudentAnswerResponseCount.labels(agentLaunchId())?.inc()
                    it.wsSession.outgoing.send(Frame.Text(data.jsonArgs))
                    logger.debug { "Sent $data ${answerWsConnections.size}" }
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

  fun Routing.challengeWsEndpoint(metrics: Metrics) {
    webSocket("$WS_ROOT$CHALLENGE_ENDPOINT/{$CLASS_CODE}/{$CHALLENGE_MD5}") {
      val answerWsContext = AnswerSessionContext(this, metrics)
      try {
        outgoing.invokeOnClose {
          logger.debug { "Close received for student answers websocket:  ${answerWsConnections.size}" }
          incoming.cancel()
        }

        answerWsConnections += answerWsContext
        assignMaxConnections()

        logger.debug { "Opened student answers websocket: ${answerWsConnections.size}" }

        metrics.wsStudentAnswerCount.labels(agentLaunchId())?.inc()
        metrics.wsStudentAnswerGauge.labels(agentLaunchId())?.inc()

        metrics.measureEndpointRequest("/websocket_student_answers") {
          val p = call.parameters
          val classCode = p[CLASS_CODE]?.let { ClassCode(it) } ?: throw InvalidRequestException("Missing class code")
          val challengeMd5 = p[CHALLENGE_MD5] ?: throw InvalidRequestException("Missing challenge md5")
          val user = fetchUser() ?: throw InvalidRequestException("Null user")

          validateContext(null, null, classCode, null, user)

          incoming
            .consumeAsFlow()
            .mapNotNull { it as? Frame.Text }
            .collect {
              // Pause to show the "Connected" message on the client
              delay(1.seconds)
              // This will enable the connected client to get msgs
              if (answerWsContext.targetName.isBlank())
                answerWsContext.targetName = classTargetName(classCode, challengeMd5)
            }
        }
      } finally {
        answerWsConnections -= answerWsContext
        closeChannels()
        close(CloseReason(GOING_AWAY, "Client disconnected"))
        metrics.wsStudentAnswerGauge.labels(agentLaunchId())?.dec()
        logger.debug { "Closed student answers websocket ${answerWsConnections.size}" }
      }
    }
  }

  fun classTargetName(classCode: ClassCode, challengeMd5: String) = keyOf(classCode, challengeMd5)
}
