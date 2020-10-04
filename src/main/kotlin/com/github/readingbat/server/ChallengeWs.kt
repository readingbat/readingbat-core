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

import com.github.pambrose.common.concurrent.BooleanMonitor
import com.github.pambrose.common.time.format
import com.github.pambrose.common.util.simpleClassName
import com.github.readingbat.common.ClassCode
import com.github.readingbat.common.CommonUtils
import com.github.readingbat.common.Constants
import com.github.readingbat.common.Endpoints.CHALLENGE_ENDPOINT
import com.github.readingbat.common.Endpoints.WS_ROOT
import com.github.readingbat.common.Metrics
import com.github.readingbat.common.User.Companion.gson
import com.github.readingbat.dsl.InvalidRequestException
import com.github.readingbat.dsl.agentLaunchId
import com.github.readingbat.server.ReadingBatServer.anwwersChannel
import com.github.readingbat.server.ServerUtils.fetchUser
import com.github.readingbat.server.WsCommon.CHALLENGE_MD5
import com.github.readingbat.server.WsCommon.CLASS_CODE
import com.github.readingbat.server.WsCommon.closeChannels
import com.github.readingbat.server.WsCommon.validateContext
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.websocket.*
import kotlinx.coroutines.runBlocking
import mu.KLogging
import java.util.*
import java.util.concurrent.Executors
import kotlin.collections.LinkedHashSet
import kotlin.concurrent.schedule
import kotlin.math.max
import kotlin.time.TimeSource
import kotlin.time.seconds

internal object ChallengeWs : KLogging() {
  private val clock = TimeSource.Monotonic
  private val timer = Timer()
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

  class PingMessage(val msg: String) {
    val type = Constants.PING_CODE
  }

  init {
    timer.schedule(0L, 1.seconds.toLongMilliseconds()) {
      runBlocking {
        for (sessionContext in wsConnections)
          try {
            val json = gson.toJson(PingMessage(sessionContext.start.elapsedNow().format()))
            sessionContext.wsSession.outgoing.send(Frame.Text(json))
          } catch (e: Throwable) {
            logger.error { "Exception in pinger ${e.simpleClassName} ${e.message}" }
          }
      }
    }

    Executors.newSingleThreadExecutor()
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
        val finished = BooleanMonitor(false)

        outgoing.invokeOnClose {
          logger.debug { "Close received for student answers websocket:  ${wsConnections.size}" }
          finished.set(true)
        }

        wsConnections += wsContext
        assignMaxConnections()

        logger.info { "Opened student answers websocket: ${wsConnections.size}" }

        metrics.wsStudentAnswerCount.labels(agentLaunchId()).inc()
        metrics.wsStudentAnswerGauge.labels(agentLaunchId()).inc()

        metrics.measureEndpointRequest("/websocket_student_answers") {
          val p = call.parameters
          val classCode = p[CLASS_CODE]?.let { ClassCode(it) } ?: throw InvalidRequestException("Missing class code")
          val challengeMd5 = p[CHALLENGE_MD5] ?: throw InvalidRequestException("Missing challenge md5")
          val user = fetchUser() ?: throw InvalidRequestException("Null user")

          validateContext(null, null, classCode, null, user, "Student answers")
            .also { (valid, msg) -> if (!valid) throw InvalidRequestException(msg) }

          val frame = incoming.receive()
          wsContext.topicName = classTopicName(classCode, challengeMd5)
          logger.debug { "Waiting to finish: ${wsConnections.size}" }
          finished.waitUntilTrue()
        }
      } finally {
        closeChannels()
        close(CloseReason(CloseReason.Codes.GOING_AWAY, "Client disconnected"))
        wsConnections -= wsContext
        metrics.wsStudentAnswerGauge.labels(agentLaunchId()).dec()
        logger.info { "Closed student answers websocket ${wsConnections.size}" }
      }
    }
  }

  fun classTopicName(classCode: ClassCode, challengeMd5: String) = CommonUtils.keyOf(classCode, challengeMd5)
}