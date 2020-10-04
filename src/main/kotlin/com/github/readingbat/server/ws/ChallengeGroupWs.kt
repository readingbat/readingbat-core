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

import com.github.readingbat.common.ClassCode
import com.github.readingbat.common.CommonUtils
import com.github.readingbat.common.CommonUtils.pathOf
import com.github.readingbat.common.Constants
import com.github.readingbat.common.Endpoints
import com.github.readingbat.common.Endpoints.CHALLENGE_GROUP_ENDPOINT
import com.github.readingbat.common.Endpoints.WS_ROOT
import com.github.readingbat.common.Metrics
import com.github.readingbat.common.User
import com.github.readingbat.dsl.Challenge
import com.github.readingbat.dsl.InvalidRequestException
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.dsl.agentLaunchId
import com.github.readingbat.server.GroupName
import com.github.readingbat.server.LanguageName
import com.github.readingbat.server.ServerUtils.fetchUser
import com.github.readingbat.server.ServerUtils.rows
import com.github.readingbat.server.UserChallengeInfo
import com.github.readingbat.server.ws.WsCommon.CLASS_CODE
import com.github.readingbat.server.ws.WsCommon.GROUP_NAME
import com.github.readingbat.server.ws.WsCommon.LANGUAGE_NAME
import com.github.readingbat.server.ws.WsCommon.closeChannels
import com.github.readingbat.server.ws.WsCommon.validateContext
import io.ktor.features.*
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.mapNotNull
import mu.KLogging
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.concurrent.atomic.AtomicBoolean

internal object ChallengeGroupWs : KLogging() {

  fun Routing.challengeGroupWsEndpoint(metrics: Metrics, contentSrc: () -> ReadingBatContent) {
    webSocket("$WS_ROOT$CHALLENGE_GROUP_ENDPOINT/{$LANGUAGE_NAME}/{$GROUP_NAME}/{$CLASS_CODE}") {
      try {
        val finished = AtomicBoolean(false)
        logger.debug { "Opened class statistics websocket" }

        outgoing.invokeOnClose {
          logger.debug { "Close received for class statistics websocket" }
          finished.set(true)
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
          val remote = call.request.origin.remoteHost
          val user = fetchUser() ?: throw InvalidRequestException("Null user")
          val email = user.email //fetchEmail()
          val desc =
            "${pathOf(WS_ROOT, Endpoints.CHALLENGE_ENDPOINT, languageName, groupName, classCode)} - $remote - $email"

          validateContext(languageName, groupName, classCode, null, user, "Class statistics")
            .also { (valid, msg) -> if (!valid) throw InvalidRequestException(msg) }

          incoming
            .consumeAsFlow()
            .mapNotNull { it as? Frame.Text }
            .collect { frame ->
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
                  val funcInfo = challenge.functionInfo(content)
                  val challengeName = challenge.challengeName
                  val numCalls = funcInfo.invocations.size
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
                      transaction {
                        val historyMd5 = CommonUtils.md5Of(languageName, groupName, challengeName, invocation)
                        if (enrollee.historyExists(historyMd5, invocation)) {
                          attempted++
                          val history = enrollee.answerHistory(historyMd5, invocation)
                          if (history.correct)
                            numCorrect++

                          incorrectAttempts += history.incorrectAttempts
                        }
                      }

                      if (finished.get())
                        break
                    }

                    val likeDislike =
                      transaction {
                        val challengeMd5 = CommonUtils.md5Of(languageName, groupName, challengeName)
                        UserChallengeInfo
                          .slice(UserChallengeInfo.likeDislike)
                          .select { (UserChallengeInfo.userRef eq enrollee.userDbmsId) and (UserChallengeInfo.md5 eq challengeMd5) }
                          .map { it[UserChallengeInfo.likeDislike].toInt() }
                          .firstOrNull() ?: 0
                      }

                    if (likeDislike == 1)
                      likes++
                    else if (likeDislike == 2)
                      dislikes++

                    if (attempted > 0)
                      totAttemptedAtLeastOne++

                    if (numCorrect == numCalls)
                      totAllCorrect++

                    totCorrect += numCorrect

                    if (finished.get())
                      break
                  }

                  val avgCorrect =
                    if (totAttemptedAtLeastOne > 0) totCorrect / totAttemptedAtLeastOne.toFloat() else 0.0f
                  val avgCorrectFmt = "%.1f".format(avgCorrect)

                  val msg =
                    " ($numCalls | $totAttemptedAtLeastOne | $totAllCorrect | $avgCorrectFmt | $incorrectAttempts | $likes/$dislikes)"

                  val json = User.gson.toJson(ChallengeStats(challengeName.value, msg))

                  metrics.wsClassStatisticsResponseCount.labels(agentLaunchId()).inc()
                  logger.debug { "Sending data $json" }
                  if (finished.get())
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

  class ChallengeStats(val challengeName: String, val msg: String)
}