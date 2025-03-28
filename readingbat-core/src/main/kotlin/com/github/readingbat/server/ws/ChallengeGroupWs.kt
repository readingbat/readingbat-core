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
import com.github.readingbat.common.Endpoints.CHALLENGE_GROUP_ENDPOINT
import com.github.readingbat.common.Endpoints.WS_ROOT
import com.github.readingbat.common.Metrics
import com.github.readingbat.dsl.InvalidRequestException
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.dsl.agentLaunchId
import com.github.readingbat.dsl.challenge.Challenge
import com.github.readingbat.server.GroupName
import com.github.readingbat.server.LanguageName
import com.github.readingbat.server.ServerUtils.fetchUser
import com.github.readingbat.server.ServerUtils.rows
import com.github.readingbat.server.UserChallengeInfoTable
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
import org.jetbrains.exposed.sql.and
import kotlin.concurrent.atomics.AtomicBoolean

internal object ChallengeGroupWs {
  private val logger = KotlinLogging.logger {}

  fun Routing.challengeGroupWsEndpoint(metrics: Metrics, contentSrc: () -> ReadingBatContent) {
    webSocket("$WS_ROOT$CHALLENGE_GROUP_ENDPOINT/{$LANGUAGE_NAME}/{$GROUP_NAME}/{$CLASS_CODE}") {
      try {
        val finished = AtomicBoolean(false)
        logger.debug { "Opened class statistics websocket" }

        outgoing.invokeOnClose {
          logger.debug { "Close received for class statistics websocket" }
          finished.store(true)
          incoming.cancel()
        }

        metrics.wsClassStatisticsCount.labels(agentLaunchId()).inc()
        metrics.wsClassStatisticsGauge.labels(agentLaunchId()).inc()

        metrics.measureEndpointRequest("/websocket_class_statistics") {
          val content = contentSrc.invoke()
          val p = call.parameters
          val languageName =
            p[LANGUAGE_NAME]?.let { LanguageName(it) } ?: throw InvalidRequestException("Missing language")
          val groupName = p[GROUP_NAME]?.let { GroupName(it) } ?: throw InvalidRequestException("Missing group name")
          val classCode = p[CLASS_CODE]?.let { ClassCode(it) } ?: throw InvalidRequestException("Missing class code")
          val challenges = content.findGroup(languageName, groupName).challenges
          val user = fetchUser() ?: throw InvalidRequestException("Null user")
          // val email = user.email //fetchEmail()
          // val remote = call.request.origin.remoteHost
          // val desc = "${pathOf(WS_ROOT, Endpoints.CHALLENGE_ENDPOINT, languageName, groupName, classCode)} - $remote - $email"

          validateContext(languageName, groupName, classCode, null, user)

          incoming
            .consumeAsFlow()
            .mapNotNull { it as? Frame.Text }
            .collect {
              val enrollees = classCode.fetchEnrollees()

              if (enrollees.isNotEmpty()) {
                // Reorder challenges to return values left to right
                val ltor = mutableListOf<Challenge>()
                val rows = challenges.size.rows(Constants.COLUMN_CNT)
                repeat(rows) { i ->
                  challenges
                    .apply {
                      ltor += elementAt(i)
                      elementAtOrNull(i + rows)?.also { ltor += it }
                      elementAtOrNull(i + (2 * rows))?.also { ltor += it }
                    }
                }

                for (challenge in ltor) {
                  val funcInfo = challenge.functionInfo()
                  val challengeName = challenge.challengeName
                  val numCalls = funcInfo.invocationCount
                  var totAttemptedAtLeastOne = 0
                  var totAllCorrect = 0
                  var totCorrect = 0
                  var incorrectAttempts = 0
                  var likes = 0
                  var dislikes = 0

                  for (enrollee in enrollees) {
                    var attempted = 0
                    var numCorrect = 0

                    for (invocation in funcInfo.invocations) {
                      readonlyTx {
                        val historyMd5 = md5Of(languageName, groupName, challengeName, invocation)
                        if (enrollee.historyExists(historyMd5, invocation)) {
                          attempted++
                          val history = enrollee.answerHistory(historyMd5, invocation)
                          if (history.correct)
                            numCorrect++

                          incorrectAttempts += history.incorrectAttempts
                        }
                      }

                      if (finished.load())
                        break
                    }

                    val likeDislikeVal =
                      readonlyTx {
                        val md5Val = md5Of(languageName, groupName, challengeName)
                        with(UserChallengeInfoTable) {
                          select(likeDislike)
                            .where { (userRef eq enrollee.userDbmsId) and (md5 eq md5Val) }
                            .map { it[likeDislike].toInt() }
                            .firstOrNull() ?: 0
                        }
                      }

                    if (likeDislikeVal == 1)
                      likes++
                    else if (likeDislikeVal == 2)
                      dislikes++

                    if (attempted > 0)
                      totAttemptedAtLeastOne++

                    if (numCorrect == numCalls)
                      totAllCorrect++

                    totCorrect += numCorrect

                    if (finished.load())
                      break
                  }

                  val avgCorrect =
                    if (totAttemptedAtLeastOne > 0) totCorrect / totAttemptedAtLeastOne.toFloat() else 0.0f
                  val avgCorrectFmt = "%.1f".format(avgCorrect)

                  val msg =
                    " ($numCalls | $totAttemptedAtLeastOne | $totAllCorrect | $avgCorrectFmt | " +
                      "$incorrectAttempts | $likes/$dislikes)"

                  metrics.wsClassStatisticsResponseCount.labels(agentLaunchId()).inc()

                  val json = ChallengeStats(challengeName.value, msg).toJson()
                  logger.debug { "Sending data $json" }
                  if (finished.load())
                    break
                  outgoing.send(Frame.Text(json))
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
        metrics.wsClassStatisticsGauge.labels(agentLaunchId()).dec()
        logger.debug { "Closed class statistics websocket" }
      }
    }
  }

  @Serializable
  class ChallengeStats(val challengeName: String, val msg: String) {
    fun toJson() = Json.encodeToString(serializer(), this)
  }
}
