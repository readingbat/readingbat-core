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
import com.github.pambrose.common.util.random
import com.github.pambrose.common.util.simpleClassName
import com.github.readingbat.common.ClassCode
import com.github.readingbat.common.CommonUtils
import com.github.readingbat.common.Constants
import com.github.readingbat.common.Endpoints.CHALLENGE_ENDPOINT
import com.github.readingbat.common.Endpoints.WS_ROOT
import com.github.readingbat.common.Metrics
import com.github.readingbat.common.User
import com.github.readingbat.dsl.InvalidRequestException
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.dsl.agentLaunchId
import com.github.readingbat.server.ServerUtils.fetchUser
import com.github.readingbat.server.WsEndoints.CHALLENGE_MD5
import com.github.readingbat.server.WsEndoints.CLASS_CODE
import com.github.readingbat.server.WsEndoints.validateContext
import io.ktor.features.*
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import mu.KLogging
import java.util.*
import java.util.concurrent.Executors
import kotlin.collections.LinkedHashSet
import kotlin.time.TimeSource
import kotlin.time.milliseconds

internal object ChallengeWs : KLogging() {
  private val wsConnections = Collections.synchronizedSet(LinkedHashSet<DefaultWebSocketSession>())
  private val dispatcher = Executors.newCachedThreadPool().asCoroutineDispatcher()
  private val clock = TimeSource.Monotonic

  fun Routing.challengeWsEndpoint(metrics: Metrics, contentSrc: () -> ReadingBatContent) {

    webSocket("$WS_ROOT$CHALLENGE_ENDPOINT/{$CLASS_CODE}/{$CHALLENGE_MD5}") {
      try {
        val finished = BooleanMonitor(false)

        outgoing.invokeOnClose {
          logger.debug { "Close received for student answers websocket:  ${wsConnections.size}" }
          finished.set(true)
        }

        wsConnections += this

        logger.info { "Opened student answers websocket: ${wsConnections.size}" }

        metrics.wsStudentAnswerCount.labels(agentLaunchId()).inc()
        metrics.wsStudentAnswerGauge.labels(agentLaunchId()).inc()

        metrics.measureEndpointRequest("/websocket_student_answers") {

          logger.info { "Past thread context: ${wsConnections.size}" }

          val content = contentSrc.invoke()
          val p = call.parameters
          val classCode = p[CLASS_CODE]?.let { ClassCode(it) } ?: throw InvalidRequestException("Missing class code")
          val challengeMd5 = p[CHALLENGE_MD5] ?: throw InvalidRequestException("Missing challenge md5")
          val remote = call.request.origin.remoteHost
          val user = fetchUser() ?: throw InvalidRequestException("Null user")
          val email = user.email
          val path = content.functionInfoByMd5(challengeMd5)?.challenge?.path ?: Constants.UNKNOWN
          val desc =
            "${CommonUtils.pathOf(WS_ROOT, CHALLENGE_ENDPOINT, classCode, challengeMd5)} ($path) - $remote - $email"

          logger.info { "Before validateContext: ${wsConnections.size}" }

          validateContext(null, null, classCode, null, user, "Student answers")
            .also { (valid, msg) ->
              if (!valid)
                throw InvalidRequestException(msg)
            }

          logger.info { "Before incoming: ${wsConnections.size}" }

          incoming
            .consumeAsFlow()
            .mapNotNull { it as? Frame.Text }
            .collect { frame ->
              val inboundMsg = frame.readText()
              val start = clock.markNow()

              logger.info { "Past incoming: ${wsConnections.size}" }

              val pinger =
                launch(dispatcher + exceptionHandler("pinger")) {
                  logger.info { "Starting pinger: ${wsConnections.size}" }
                  while (!finished.get()) {
                    User.gson.toJson(PingMessage(start.elapsedNow().format()))
                      .also { json ->
                        outgoing.send(Frame.Text(json))
                        logger.info { "Sent $json ${wsConnections.size}" }
                      }

                    for (i in 0..(10.random())) {
                      delay(1000.milliseconds)
                      if (finished.get())
                        break
                    }
                  }
                  logger.info { "Ending pinger: ${wsConnections.size}" }
                }

              logger.info { "Before pubsub: ${wsConnections.size}" }

              val subscriber =
                launch(dispatcher + exceptionHandler("outgoing.send()")) {
                  val topicName = classTopicName(classCode, challengeMd5)
                  for (data in ReadingBatServer.channel.openSubscription()) {
                    if (finished.get())
                      break
                    if (data.topic == topicName) {
                      metrics.wsStudentAnswerResponseCount.labels(agentLaunchId()).inc()
                      outgoing.send(Frame.Text(data.message))
                      logger.info { "Sent $data ${wsConnections.size}" }
                    }
                  }
                }

              logger.info { "Waiting for finished to be true: ${wsConnections.size}" }

              // Wait for user to close socket
              finished.waitUntilTrue()

              // Send this to break out of loop reading channel
              ReadingBatServer.channel.send(PublishedData("no-op", ""))

              pinger.join()
              subscriber.join()
            }
        }
      } finally {
        metrics.wsStudentAnswerGauge.labels(agentLaunchId()).dec()
        close(CloseReason(CloseReason.Codes.GOING_AWAY, "Client disconnected"))
        logger.debug { "Closed student answers websocket for desc" }
        wsConnections -= this
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