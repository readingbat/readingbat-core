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

import com.github.pambrose.common.time.format
import com.github.pambrose.common.util.simpleClassName
import com.github.readingbat.common.ClassCode
import com.github.readingbat.common.CommonUtils.keyOf
import com.github.readingbat.common.Constants
import com.github.readingbat.common.Endpoints.CHALLENGE_ENDPOINT
import com.github.readingbat.common.Endpoints.WS_ROOT
import com.github.readingbat.common.Metrics
import com.github.readingbat.dsl.InvalidRequestException
import com.github.readingbat.dsl.agentLaunchId
import com.github.readingbat.server.ReadingBatServer.anwwersChannel
import com.github.readingbat.server.ServerUtils.fetchUser
import com.github.readingbat.server.ws.WsCommon.CHALLENGE_MD5
import com.github.readingbat.server.ws.WsCommon.CLASS_CODE
import com.github.readingbat.server.ws.WsCommon.closeChannels
import com.github.readingbat.server.ws.WsCommon.validateContext
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mu.KLogging
import java.util.*
import java.util.concurrent.Executors.newSingleThreadExecutor
import kotlin.collections.LinkedHashSet
import kotlin.concurrent.timer
import kotlin.math.max
import kotlin.time.TimeSource
import kotlin.time.seconds

internal object ChallengeWs : KLogging() {
  private val clock = TimeSource.Monotonic
  val wsConnections = Collections.synchronizedSet(LinkedHashSet<SessionContext>())
  var maxWsConnections = 0

  @Synchronized
  private fun assignMaxConnections() {
    maxWsConnections = max(maxWsConnections, wsConnections.size)
  }

  data class SessionContext(val wsSession: DefaultWebSocketServerSession, val metrics: Metrics) {
    val start = clock.markNow()
    var topicName = ""
  }

  @Serializable
  class PingMessage(val msg: String) {
    val type = Constants.PING_CODE
    fun toJson() = Json.encodeToString(serializer(), this)
  }

  init {
    timer("pinger", true, 0L, 1.seconds.toLongMilliseconds()) {
      for (sessionContext in wsConnections)
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

    newSingleThreadExecutor()
      .submit {
        while (true) {
          try {
            runBlocking {
              for (data in anwwersChannel.openSubscription()) {
                wsConnections
                  .filter { it.topicName == data.topic }
                  .forEach {
                    it.metrics.wsStudentAnswerResponseCount.labels(agentLaunchId()).inc()
                    it.wsSession.outgoing.send(Frame.Text(data.message))
                    logger.debug { "Sent $data ${wsConnections.size}" }
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
      val wsContext = SessionContext(this, metrics)
      try {
        outgoing.invokeOnClose {
          logger.debug { "Close received for student answers websocket:  ${wsConnections.size}" }
          incoming.cancel()
        }

        wsConnections += wsContext
        assignMaxConnections()

        logger.debug { "Opened student answers websocket: ${wsConnections.size}" }

        metrics.wsStudentAnswerCount.labels(agentLaunchId()).inc()
        metrics.wsStudentAnswerGauge.labels(agentLaunchId()).inc()

        metrics.measureEndpointRequest("/websocket_student_answers") {
          val p = call.parameters
          val classCode = p[CLASS_CODE]?.let { ClassCode(it) } ?: throw InvalidRequestException("Missing class code")
          val challengeMd5 = p[CHALLENGE_MD5] ?: throw InvalidRequestException("Missing challenge md5")
          val user = fetchUser() ?: throw InvalidRequestException("Null user")

          validateContext(null, null, classCode, null, user)
            .also { (valid, msg) -> if (!valid) throw InvalidRequestException(msg) }

          incoming
            .consumeAsFlow()
            .mapNotNull { it as? Frame.Text }
            .collect {
              if (wsContext.topicName.isBlank())
                wsContext.topicName = classTopicName(classCode, challengeMd5)
            }
        }
      } finally {
        wsConnections -= wsContext
        closeChannels()
        close(CloseReason(CloseReason.Codes.GOING_AWAY, "Client disconnected"))
        metrics.wsStudentAnswerGauge.labels(agentLaunchId()).dec()
        logger.info { "Closed student answers websocket ${wsConnections.size}" }
      }
    }
  }

  fun classTopicName(classCode: ClassCode, challengeMd5: String) = keyOf(classCode, challengeMd5)
}