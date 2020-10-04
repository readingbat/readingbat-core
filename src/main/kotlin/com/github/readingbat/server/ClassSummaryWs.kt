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

import com.github.readingbat.common.ClassCode
import com.github.readingbat.common.CommonUtils
import com.github.readingbat.common.Constants
import com.github.readingbat.common.Endpoints.CLASS_SUMMARY_ENDPOINT
import com.github.readingbat.common.Endpoints.WS_ROOT
import com.github.readingbat.common.Metrics
import com.github.readingbat.common.User
import com.github.readingbat.dsl.InvalidRequestException
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.dsl.agentLaunchId
import com.github.readingbat.server.ServerUtils.fetchUser
import com.github.readingbat.server.WsEndoints.CLASS_CODE
import com.github.readingbat.server.WsEndoints.GROUP_NAME
import com.github.readingbat.server.WsEndoints.LANGUAGE_NAME
import com.github.readingbat.server.WsEndoints.validateContext
import io.ktor.features.*
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.mapNotNull
import mu.KLogging
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.concurrent.atomic.AtomicBoolean

internal object ClassSummaryWs : KLogging() {

  fun Routing.classSummaryWsEndpoint(metrics: Metrics, contentSrc: () -> ReadingBatContent) {
    webSocket("$WS_ROOT$CLASS_SUMMARY_ENDPOINT/{$LANGUAGE_NAME}/{$GROUP_NAME}/{$CLASS_CODE}") {
      try {
        val finished = AtomicBoolean(false)

        logger.debug { "Opened class summary websocket" }

        outgoing.invokeOnClose {
          logger.debug { "Close received for class summary websocket" }
          finished.set(true)
        }

        metrics.wsClassSummaryCount.labels(agentLaunchId()).inc()
        metrics.wsClassSummaryGauge.labels(agentLaunchId()).inc()

        metrics.measureEndpointRequest("/websocket_class_summary") {

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
          val desc = "${
            CommonUtils.pathOf(WS_ROOT,
                               CLASS_SUMMARY_ENDPOINT,
                               languageName,
                               groupName,
                               classCode)
          } - $remote - $email"

          validateContext(languageName, groupName, classCode, null, user, "Class summary")
            .also { (valid, msg) ->
              if (!valid)
                throw InvalidRequestException(msg)
            }

          incoming
            .consumeAsFlow()
            .mapNotNull { it as? Frame.Text }
            .collect { frame ->
              val inboundMsg = frame.readText()
              val enrollees = classCode.fetchEnrollees()
              if (enrollees.isNotEmpty()) {
                for (challenge in challenges) {
                  val funcInfo = challenge.functionInfo(content)
                  val challengeName = challenge.challengeName
                  val numCalls = funcInfo.invocations.size
                  var likes = 0
                  var dislikes = 0

                  for (enrollee in enrollees) {
                    var incorrectAttempts = 0

                    val results = mutableListOf<String>()
                    for (invocation in funcInfo.invocations) {
                      transaction {
                        val historyMd5 = CommonUtils.md5Of(languageName, groupName, challengeName, invocation)
                        if (enrollee.historyExists(historyMd5, invocation)) {
                          results +=
                            enrollee.answerHistory(historyMd5, invocation)
                              .let {
                                incorrectAttempts += it.incorrectAttempts
                                if (it.correct) Constants.YES else if (it.incorrectAttempts > 0) Constants.NO else Constants.UNANSWERED
                              }
                        }
                        else {
                          results += Constants.UNANSWERED
                        }
                      }

                      if (finished.get())
                        break
                    }

                    if (incorrectAttempts > 0 || results.any { it != Constants.UNANSWERED }) {
                      val json =
                        User.gson.toJson(
                          ClassSummary(enrollee.userId,
                                       challengeName.encode(),
                                       results,
                                       if (incorrectAttempts == 0 && results.all { it == Constants.UNANSWERED }) "" else incorrectAttempts.toString()))

                      metrics.wsClassSummaryResponseCount.labels(agentLaunchId()).inc()
                      logger.debug { "Sending data $json" }
                      outgoing.send(Frame.Text(json))
                    }

                    if (finished.get())
                      break
                  }
                }
              }

              // Shut things down to exit collect
              outgoing.close()
              incoming.cancel()
            }
        }
      } finally {
        metrics.wsClassSummaryGauge.labels(agentLaunchId()).dec()
        close(CloseReason(CloseReason.Codes.GOING_AWAY, "Client disconnected"))
        logger.debug { "Closed class summary websocket" }
      }
    }
  }

  class ClassSummary(val userId: String, val challengeName: String, val results: List<String>, val msg: String)
}