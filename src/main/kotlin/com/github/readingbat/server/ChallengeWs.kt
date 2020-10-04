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
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.dsl.agentLaunchId
import com.github.readingbat.server.ServerUtils.fetchUser
import com.github.readingbat.server.WsEndoints.CHALLENGE_MD5
import com.github.readingbat.server.WsEndoints.CLASS_CODE
import com.github.readingbat.server.WsEndoints.validateContext
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.runBlocking
import mu.KLogging
import java.util.*
import java.util.concurrent.Executors
import kotlin.collections.LinkedHashSet
import kotlin.concurrent.schedule
import kotlin.time.TimeSource
import kotlin.time.seconds

internal object ChallengeWs : KLogging() {
  private val wsConnections = Collections.synchronizedSet(LinkedHashSet<SessionContext>())
  private val clock = TimeSource.Monotonic
  private val timer = Timer()

  data class SessionContext(val wsSession: DefaultWebSocketServerSession, val metrics: Metrics) {
    val start = clock.markNow()
    var topicName = ""
  }

  init {
    timer.schedule(0L, 1.seconds.toLongMilliseconds()) {
      runBlocking {
        for (sessionContext in wsConnections) {
          gson.toJson(PingMessage(sessionContext.start.elapsedNow().format()))
            .also { json ->
              try {
                sessionContext.wsSession.outgoing.send(Frame.Text(json))
                //logger.info { "Sent $json ${wsConnections.size}" }
              } catch (e: Throwable) {
                logger.info { "Exception in pinger ${e.simpleClassName} ${e.message}" }
              }
            }
        }
      }
    }

    Executors.newSingleThreadExecutor()
      .submit {
        while (true) {
          try {
            runBlocking {
              for (data in ReadingBatServer.channel.openSubscription()) {
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

  fun Routing.challengeWsEndpoint(metrics: Metrics, contentSrc: () -> ReadingBatContent) {

    webSocket("$WS_ROOT$CHALLENGE_ENDPOINT/{$CLASS_CODE}/{$CHALLENGE_MD5}") {
      val wsContext = SessionContext(this, metrics)
      try {
        val finished = BooleanMonitor(false)

        outgoing.invokeOnClose {
          logger.debug { "Close received for student answers websocket:  ${wsConnections.size}" }
          finished.set(true)
        }

        wsConnections += wsContext

        logger.info { "Opened student answers websocket: ${wsConnections.size}" }

        metrics.wsStudentAnswerCount.labels(agentLaunchId()).inc()
        metrics.wsStudentAnswerGauge.labels(agentLaunchId()).inc()

        metrics.measureEndpointRequest("/websocket_student_answers") {

          logger.info { "Past thread context: ${wsConnections.size}" }

          val content = contentSrc.invoke()
          val p = call.parameters
          val classCode = p[CLASS_CODE]?.let { ClassCode(it) } ?: throw InvalidRequestException("Missing class code")
          val challengeMd5 = p[CHALLENGE_MD5] ?: throw InvalidRequestException("Missing challenge md5")
          val user = fetchUser() ?: throw InvalidRequestException("Null user")

          wsContext.topicName = classTopicName(classCode, challengeMd5)

          logger.info { "Before validateContext: ${wsConnections.size}" }

          validateContext(null, null, classCode, null, user, "Student answers")
            .also { (valid, msg) -> if (!valid) throw InvalidRequestException(msg) }

          val frame = incoming.receive()
          logger.info { "Waiting to finish: ${wsConnections.size}" }
          finished.waitUntilTrue()
        }
      } finally {
        metrics.wsStudentAnswerGauge.labels(agentLaunchId()).dec()
        close(CloseReason(CloseReason.Codes.GOING_AWAY, "Client disconnected"))
        logger.debug { "Closed student answers websocket for desc" }
        wsConnections -= wsContext
        logger.info { "Connection count exit: ${wsConnections.size}" }
      }
    }
  }

  fun classTopicName(classCode: ClassCode, challengeMd5: String) = CommonUtils.keyOf(classCode, challengeMd5)

  fun exceptionHandler(name: String) =
    CoroutineExceptionHandler { _, e ->
      logger.error(e) { "Error ${e.simpleClassName} ${e.message} in $name" }
    }

  class PingMessage(val msg: String) {
    val type = Constants.PING_CODE
  }
}