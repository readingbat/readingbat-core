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

import com.github.pambrose.common.redis.RedisUtils.withSuspendingNonNullRedisPool
import com.github.pambrose.common.time.format
import com.github.pambrose.common.util.simpleClassName
import com.github.readingbat.common.ClassCode
import com.github.readingbat.common.Constants.PING_CODE
import com.github.readingbat.common.Endpoints.CHALLENGE_ENDPOINT
import com.github.readingbat.common.Endpoints.WS_ROOT
import com.github.readingbat.common.KeyConstants.keyOf
import com.github.readingbat.common.Metrics
import com.github.readingbat.dsl.InvalidRequestException
import com.github.readingbat.dsl.RedisUnavailableException
import com.github.readingbat.dsl.agentLaunchId
import com.github.readingbat.dsl.isMultiServerEnabled
import com.github.readingbat.server.ReadingBatServer.redisPool
import com.github.readingbat.server.ServerUtils.fetchUser
import com.github.readingbat.server.ws.PubSubCommandsWs.ChallengeAnswerData
import com.github.readingbat.server.ws.WsCommon.CHALLENGE_MD5
import com.github.readingbat.server.ws.WsCommon.CLASS_CODE
import com.github.readingbat.server.ws.WsCommon.closeChannels
import com.github.readingbat.server.ws.WsCommon.validateContext
import io.ktor.http.cio.websocket.*
import io.ktor.http.cio.websocket.CloseReason.Codes.*
import io.ktor.routing.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel.Factory.BUFFERED
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mu.KLogging
import java.util.Collections.synchronizedSet
import java.util.concurrent.Executors.newSingleThreadExecutor
import kotlin.concurrent.timer
import kotlin.math.max
import kotlin.time.TimeSource
import kotlin.time.seconds

internal object ChallengeWs : KLogging() {
  private val clock = TimeSource.Monotonic
  val singleServerWsChannel by lazy { BroadcastChannel<ChallengeAnswerData>(BUFFERED) }
  val multiServerWsWriteChannel by lazy { BroadcastChannel<ChallengeAnswerData>(BUFFERED) }
  val multiServerWsReadChannel by lazy { BroadcastChannel<ChallengeAnswerData>(BUFFERED) }
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

    timer("pinger", false, 0L, 1.seconds.toLongMilliseconds()) {
      for (sessionContext in answerWsConnections)
        if (sessionContext.enabled)
          try {
            val elapsed = sessionContext.start.elapsedNow().format()
            val json = PingMessage(elapsed).toJson()
            runBlocking {
              sessionContext.wsSession.outgoing.send(Frame.Text(json))
            }
          } catch (e: Throwable) {
            logger.error { "Exception in pinger ${e.simpleClassName} ${e.message}" }
          }
    }

    if (isMultiServerEnabled()) {
      newSingleThreadExecutor()
        .submit {
          while (true) {
            try {
              runBlocking {
                redisPool?.withSuspendingNonNullRedisPool { redis ->
                  multiServerWsWriteChannel
                    .openSubscription()
                    .consumeAsFlow()
                    .onStart { logger.info { "Starting to read multi-server writer ws channel values" } }
                    .onCompletion { logger.info { "Finished reading multi-server writer ws channel values" } }
                    .collect { data ->
                      redis.publish(data.pubSubTopic.name, data.toJson())
                    }
                } ?: throw RedisUnavailableException("multiServerWriteChannel")
              }
            } catch (e: Throwable) {
              logger.error { "Exception in challenge ws writer: ${e.simpleClassName} ${e.message}" }
              Thread.sleep(1.seconds.toLongMilliseconds())
            }
          }
        }
    }

    newSingleThreadExecutor()
      .submit {
        while (true) {
          try {
            runBlocking {
              (if (isMultiServerEnabled()) multiServerWsReadChannel else singleServerWsChannel)
                .openSubscription()
                .consumeAsFlow()
                .onStart { logger.info { "Starting to read challenge ws channel values" } }
                .onCompletion { logger.info { "Finished reading challenge ws channel values" } }
                .collect { data ->
                  answerWsConnections
                    .filter { it.targetName == data.target }
                    .forEach {
                      it.metrics.wsStudentAnswerResponseCount.labels(agentLaunchId()).inc()
                      it.wsSession.outgoing.send(Frame.Text(data.jsonArgs))
                      logger.debug { "Sent $data ${answerWsConnections.size}" }
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

        metrics.wsStudentAnswerCount.labels(agentLaunchId()).inc()
        metrics.wsStudentAnswerGauge.labels(agentLaunchId()).inc()

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
        metrics.wsStudentAnswerGauge.labels(agentLaunchId()).dec()
        logger.debug { "Closed student answers websocket ${answerWsConnections.size}" }
      }
    }
  }

  fun classTargetName(classCode: ClassCode, challengeMd5: String) = keyOf(classCode, challengeMd5)
}