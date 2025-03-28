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

import com.github.pambrose.common.util.md5Of
import com.github.readingbat.common.ClassCode
import com.github.readingbat.common.Constants
import com.github.readingbat.common.Constants.UNANSWERED
import com.github.readingbat.common.Endpoints.CLASS_SUMMARY_ENDPOINT
import com.github.readingbat.common.Endpoints.WS_ROOT
import com.github.readingbat.common.Metrics
import com.github.readingbat.dsl.InvalidRequestException
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.dsl.agentLaunchId
import com.github.readingbat.server.GroupName
import com.github.readingbat.server.LanguageName
import com.github.readingbat.server.ServerUtils.fetchUser
import com.github.readingbat.server.ws.WsCommon.CLASS_CODE
import com.github.readingbat.server.ws.WsCommon.GROUP_NAME
import com.github.readingbat.server.ws.WsCommon.LANGUAGE_NAME
import com.github.readingbat.server.ws.WsCommon.closeChannels
import com.github.readingbat.server.ws.WsCommon.validateContext
import com.pambrose.common.exposed.readonlyTx
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.routing.Routing
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.concurrent.atomics.AtomicBoolean

internal object ClassSummaryWs {
  private val logger = KotlinLogging.logger {}

  fun Routing.classSummaryWsEndpoint(metrics: Metrics, contentSrc: () -> ReadingBatContent) {
    webSocket("$WS_ROOT$CLASS_SUMMARY_ENDPOINT/{$LANGUAGE_NAME}/{$GROUP_NAME}/{$CLASS_CODE}") {
      try {
        val finished = AtomicBoolean(false)

        logger.debug { "Opened class summary websocket" }

        outgoing.invokeOnClose {
          logger.debug { "Close received for class summary websocket" }
          finished.store(true)
          incoming.cancel()
        }

        metrics.wsClassSummaryCount.labels(agentLaunchId()).inc()
        metrics.wsClassSummaryGauge.labels(agentLaunchId()).inc()

        metrics.measureEndpointRequest("/websocket_class_summary") {
          val content = contentSrc.invoke()
          val p = call.parameters
          val langName = p[LANGUAGE_NAME]?.let { LanguageName(it) } ?: throw InvalidRequestException("Missing language")
          val groupName = p[GROUP_NAME]?.let { GroupName(it) } ?: throw InvalidRequestException("Missing group name")
          val classCode = p[CLASS_CODE]?.let { ClassCode(it) } ?: throw InvalidRequestException("Missing class code")
          val challenges = content.findGroup(langName, groupName).challenges
          val user = fetchUser() ?: throw InvalidRequestException("Null user")
          // val email = user.email
          // val remote = call.request.origin.remoteHost
          // val desc = "${pathOf(WS_ROOT, CLASS_SUMMARY_ENDPOINT, languageName, groupName, classCode)} - $remote - $email"

          validateContext(langName, groupName, classCode, null, user)

          incoming
            .consumeAsFlow()
            .mapNotNull { it as? Frame.Text }
            .collect {
              val enrollees = classCode.fetchEnrollees()
              if (enrollees.isNotEmpty()) {
                for (challenge in challenges) {
                  val funcInfo = challenge.functionInfo()
                  val challengeName = challenge.challengeName
                  // val numCalls = funcInfo.invocationCount
                  // var likes = 0
                  // var dislikes = 0

                  for (enrollee in enrollees) {
                    var incorrectAttempts = 0

                    val results = mutableListOf<String>()
                    for (invocation in funcInfo.invocations) {
                      readonlyTx {
                        val historyMd5 = md5Of(langName, groupName, challengeName, invocation)
                        if (enrollee.historyExists(historyMd5, invocation)) {
                          results +=
                            enrollee.answerHistory(historyMd5, invocation)
                              .let {
                                incorrectAttempts += it.incorrectAttempts
                                if (it.correct) {
                                  Constants.YES
                                } else {
                                  if (it.incorrectAttempts > 0) Constants.NO else UNANSWERED
                                }
                              }
                        } else {
                          results += UNANSWERED
                        }
                      }

                      if (finished.load())
                        break
                    }

                    val likeDislike = enrollee.likeDislike(challenge)
                    if (incorrectAttempts > 0 || results.any { it != UNANSWERED } || likeDislike != 0) {
                      val stats =
                        if (incorrectAttempts == 0 && results.all { it == UNANSWERED })
                          ""
                        else
                          incorrectAttempts.toString()
                      val json =
                        ClassSummary(
                          userId = enrollee.userId,
                          challengeName = challengeName.encode(),
                          results = results,
                          stats = stats,
                          // TODO Consolidate this into a single call per user
                          likeDislike = enrollee.likeDislikeEmoji(likeDislike),
                        ).toJson()

                      metrics.wsClassSummaryResponseCount.labels(agentLaunchId()).inc()
                      logger.debug { "Sending data $json" }
                      if (!finished.load())
                        outgoing.send(Frame.Text(json))
                    }

                    if (finished.load())
                      break
                  }
                }
              }

              // Shut things down to exit collect
              incoming.cancel()
            }
        }
      } finally {
        // In case exited early
        closeChannels()
        close(CloseReason(CloseReason.Codes.GOING_AWAY, "Client disconnected"))
        metrics.wsClassSummaryGauge.labels(agentLaunchId()).dec()
        logger.debug { "Closed class summary websocket" }
      }
    }
  }

  @Suppress("unused")
  @Serializable
  class ClassSummary(
    val userId: String,
    val challengeName: String,
    val results: List<String>,
    val stats: String,
    val likeDislike: String,
  ) {
    fun toJson() = Json.encodeToString(serializer(), this)
  }
}
