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

import com.pambrose.common.util.md5Of
import com.readingbat.common.ClassCode
import com.readingbat.common.Constants.NO
import com.readingbat.common.Constants.UNANSWERED
import com.readingbat.common.Constants.YES
import com.readingbat.common.Endpoints.STUDENT_SUMMARY_ENDPOINT
import com.readingbat.common.Endpoints.WS_ROOT
import com.readingbat.common.Metrics
import com.readingbat.common.User.Companion.toUser
import com.readingbat.common.WsProtocol
import com.readingbat.dsl.InvalidRequestException
import com.readingbat.dsl.ReadingBatContent
import com.readingbat.dsl.agentLaunchId
import com.readingbat.server.LanguageName
import com.readingbat.server.ServerUtils.fetchUser
import com.readingbat.server.ws.WsCommon.CLASS_CODE
import com.readingbat.server.ws.WsCommon.LANGUAGE_NAME
import com.readingbat.server.ws.WsCommon.STUDENT_ID
import com.readingbat.server.ws.WsCommon.closeChannels
import com.readingbat.server.ws.WsCommon.validateContext
import com.readingbat.utils.toJson
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.routing.Routing
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.CloseReason.Codes.GOING_AWAY
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.concurrent.atomics.AtomicBoolean

/**
 * WebSocket endpoint for a single student's progress summary.
 *
 * Iterates over all challenge groups and challenges for a given language, streaming
 * per-invocation results (correct/incorrect/unanswered), incorrect attempt counts,
 * and like/dislike data for the specified student to the connected teacher.
 */
internal object StudentSummaryWs {
  private val logger = KotlinLogging.logger {}

  fun Routing.studentSummaryWsEndpoint(metrics: Metrics, contentSrc: () -> ReadingBatContent) {
    webSocket("$WS_ROOT$STUDENT_SUMMARY_ENDPOINT/{$LANGUAGE_NAME}/{$STUDENT_ID}/{$CLASS_CODE}") {
      try {
        val finished = AtomicBoolean(false)
        logger.debug { "Opened student summary websocket" }

        outgoing.invokeOnClose {
          logger.debug { "Close received for student summary websocket" }
          finished.store(true)
          incoming.cancel()
        }

        metrics.wsStudentSummaryCount.labels(agentLaunchId()).inc()
        metrics.wsStudentSummaryGauge.labels(agentLaunchId()).inc()

        metrics.measureEndpointRequest("/websocket_student_summary") {
          val content = contentSrc()
          val p = call.parameters
          val languageName =
            p[LANGUAGE_NAME]?.let { LanguageName(it) } ?: throw InvalidRequestException("Missing language")
          val student = p[STUDENT_ID]?.toUser() ?: throw InvalidRequestException("Missing student id")
          val classCode = p[CLASS_CODE]?.let { ClassCode(it) } ?: throw InvalidRequestException("Missing class code")
          val user = fetchUser() ?: throw InvalidRequestException("Null user")
          // val email = user.email //fetchEmail()
          // val remote = call.request.origin.remoteHost
          // val desc = "${pathOf(WS_ROOT, CLASS_SUMMARY_ENDPOINT, languageName, student.userId, classCode)} - $remote - $email"

          validateContext(languageName, null, classCode, student, user)

          incoming
            .consumeAsFlow()
            .mapNotNull { it as? Frame.Text }
            .collect {
              for (challengeGroup in content.findLanguage(languageName).challengeGroups) {
                for (challenge in challengeGroup.challenges) {
                  val funcInfo = challenge.functionInfo()
                  val groupName = challengeGroup.groupName
                  val challengeName = challenge.challengeName
                  // val numCalls = funcInfo.invocationCount
                  // var likes = 0
                  // var dislikes = 0
                  var incorrectAttempts = 0
                  var attempted = 0

                  val results = mutableListOf<String>()
                  for (invocation in funcInfo.invocations) {
                    val historyMd5 = md5Of(languageName, groupName, challengeName, invocation)
                    if (student.historyExists(historyMd5, invocation)) {
                      attempted++
                      results +=
                        student.answerHistory(historyMd5, invocation)
                          .let {
                            incorrectAttempts += it.incorrectAttempts
                            if (it.correct) {
                              YES
                            } else {
                              if (it.incorrectAttempts > 0)
                                NO
                              else
                                UNANSWERED
                            }
                          }
                    } else {
                      results += UNANSWERED
                    }

                    if (finished.load())
                      break
                  }

                  val likeDislike = student.likeDislike(challenge)

                  if (incorrectAttempts > 0 || results.any { it != UNANSWERED } || likeDislike != 0) {
                    val stats =
                      if (incorrectAttempts == 0 && results.all { it == UNANSWERED })
                        ""
                      else
                        incorrectAttempts.toString()
                    val json =
                      StudentSummary(
                        groupName.encode(),
                        challengeName.encode(),
                        results,
                        stats,
                        student.likeDislikeEmoji(likeDislike),
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

              // Shut things down to exit collect
              incoming.cancel()
            }
        }
      } finally {
        // In case exited early
        closeChannels()
        close(CloseReason(GOING_AWAY, "Client disconnected"))
        metrics.wsStudentSummaryGauge.labels(agentLaunchId()).dec()
        logger.debug { "Closed student summary websocket" }
      }
    }
  }

  /** JSON payload for one challenge's results for a student, sent over the student summary WebSocket. */
  @Serializable
  @Suppress("unused")
  data class StudentSummary(
    @SerialName(WsProtocol.GROUP_NAME_FIELD) val groupName: String,
    @SerialName(WsProtocol.CHALLENGE_NAME_FIELD) val challengeName: String,
    @SerialName(WsProtocol.RESULTS_FIELD) val results: List<String>,
    @SerialName(WsProtocol.STATS_FIELD) val stats: String,
    @SerialName(WsProtocol.LIKE_DISLIKE_FIELD) val likeDislike: String,
  )
}
