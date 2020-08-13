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

import com.github.pambrose.common.redis.RedisUtils.withRedis
import com.github.pambrose.common.util.isNotNull
import com.github.readingbat.dsl.InvalidPathException
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.dsl.agentLaunchId
import com.github.readingbat.misc.ClassCode
import com.github.readingbat.misc.Endpoints.CHALLENGE_ENDPOINT
import com.github.readingbat.misc.Endpoints.CHALLENGE_GROUP_ENDPOINT
import com.github.readingbat.misc.User.Companion.gson
import com.github.readingbat.posts.ChallengeHistory
import io.ktor.http.cio.websocket.CloseReason
import io.ktor.http.cio.websocket.CloseReason.Codes
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.close
import io.ktor.http.cio.websocket.readText
import io.ktor.routing.Routing
import io.ktor.websocket.webSocket
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import mu.KLogging
import redis.clients.jedis.JedisPubSub

internal object WsEndoints : KLogging() {

  private const val LANGUAGE_NAME = "languageName"
  private const val GROUP_NAME = "groupName"
  private const val CLASS_CODE = "classCode"

  fun Routing.wsEndpoints(metrics: Metrics, content: () -> ReadingBatContent) {

    webSocket("$CHALLENGE_ENDPOINT/{$CLASS_CODE}") {
      var desc = "unassigned"
      logger.info { "Called student answer websocket" }
      metrics.measureEndpointRequest("/websocket_class") {
        try {
          val classCode = call.parameters[CLASS_CODE]?.let { ClassCode(it) }
            ?: throw InvalidPathException("Missing class code")

          desc = "$CHALLENGE_ENDPOINT/$classCode"
          logger.info { "Opening student answer websocket for $desc" }

          metrics.wsStudentAnswerStartCount.labels(agentLaunchId()).inc()

          incoming
            .receiveAsFlow()
            .mapNotNull { it as? Frame.Text }
            .collect { frame ->
              val inboundMsg = frame.readText()
              withRedis { redis ->
                redis?.subscribe(object : JedisPubSub() {
                  override fun onMessage(channel: String?, message: String?) {
                    if (message.isNotNull()) {
                      metrics.wsStudentAnswerResponseCount.labels(agentLaunchId()).inc()
                      logger.debug { "Sending data $message from $channel" }
                      runBlocking { outgoing.send(Frame.Text(message)) }
                    }
                  }
                }, classCode.value)
              }
            }
        } finally {
          logger.info { "Closing student answer websocket for $desc" }
          close(CloseReason(Codes.NORMAL, "Client disconnected"))
        }
      }
    }

    webSocket("$CHALLENGE_GROUP_ENDPOINT/{$LANGUAGE_NAME}/{$GROUP_NAME}/{$CLASS_CODE}") {
      val closed = atomic(false)
      var desc = "unassigned"
      logger.info { "Called class statistics websocket" }
      metrics.measureEndpointRequest("/websocket_group") {
        try {
          val languageName =
            call.parameters[LANGUAGE_NAME]?.let { LanguageName(it) }
              ?: throw InvalidPathException("Missing language name")
          val groupName =
            call.parameters[GROUP_NAME]?.let { GroupName(it) } ?: throw InvalidPathException("Missing group name")
          val classCode =
            call.parameters[CLASS_CODE]?.let { ClassCode(it) } ?: throw InvalidPathException("Missing class code")
          val challenges = content.invoke().findGroup(languageName.toLanguageType(), groupName).challenges

          desc = "$CHALLENGE_ENDPOINT/$languageName/$groupName/$classCode"
          logger.info { "Opening class statistics websocket for $desc" }

          metrics.wsClassStatisticsStartCount.labels(agentLaunchId()).inc()

          incoming
            .receiveAsFlow()
            .mapNotNull { it as? Frame.Text }
            .collect { frame ->
              val inboundMsg = frame.readText()
              withRedis { redis ->
                if (redis.isNotNull()) {
                  val enrollees = classCode.fetchEnrollees(redis)
                  challenges
                    .forEach { challenge ->
                      if (classCode.isTeacherMode && enrollees.isNotEmpty()) {
                        val funcInfo = challenge.funcInfo(content.invoke())
                        val challengeName = challenge.challengeName
                        val numCalls = funcInfo.invocations.size
                        var totAttemptedAtLeastOne = 0
                        var totAllCorrect = 0
                        var totCorrect = 0
                        var likes = 0
                        var dislikes = 0

                        enrollees.forEach { enrollee ->
                          var attempted = 0
                          var numCorrect = 0

                          funcInfo.invocations
                            .forEach { invocation ->
                              val answerHistoryKey =
                                enrollee.answerHistoryKey(languageName, groupName, challengeName, invocation)

                              if (redis.exists(answerHistoryKey)) {
                                attempted++
                                val json = redis[answerHistoryKey] ?: ""
                                val history =
                                  gson.fromJson(json, ChallengeHistory::class.java) ?: ChallengeHistory(invocation)
                                if (history.correct)
                                  numCorrect++
                              }
                            }

                          val likeDislikeKey = enrollee.likeDislikeKey(languageName, groupName, challengeName)
                          val likeDislike = redis[likeDislikeKey]?.toInt() ?: 0
                          if (likeDislike == 1)
                            likes++
                          else if (likeDislike == 2)
                            dislikes++

                          if (attempted > 0)
                            totAttemptedAtLeastOne++

                          if (numCorrect == numCalls)
                            totAllCorrect++

                          totCorrect += numCorrect
                        }

                        val avgCorrect =
                          if (totAttemptedAtLeastOne > 0) totCorrect / totAttemptedAtLeastOne.toFloat() else 0.0f
                        val avgCorrectFmt = "%.1f".format(avgCorrect)

                        val msg =
                          " ($numCalls | $totAttemptedAtLeastOne | $totAllCorrect | $avgCorrectFmt | $likes | $dislikes)"

                        val challengeStats = ChallengeStats(challengeName.value, msg)
                        val json = gson.toJson(challengeStats)

                        metrics.wsClassStatisticsResponseCount.labels(agentLaunchId()).inc()
                        logger.debug { "Sending data $json" }
                        runBlocking { outgoing.send(Frame.Text(json)) }
                      }
                    }
                }
              }
              // This will close it if the results run to completion
              logger.info { "Closing class statistics websocket for $desc" }
              close(CloseReason(Codes.NORMAL, "Client disconnected"))
              closed.value = true
            }
        } finally {
          // This will close it if the user exits the page early
          if (!closed.value) {
            logger.info { "Closing class statistics websocket for $desc" }
            close(CloseReason(Codes.NORMAL, "Client disconnected"))
          }
        }
      }
    }
  }
}

internal class ChallengeStats(val challengeName: String, val msg: String)
