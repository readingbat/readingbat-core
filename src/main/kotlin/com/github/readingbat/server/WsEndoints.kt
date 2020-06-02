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
import com.github.readingbat.dsl.InvalidPathException
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.ClassCode
import com.github.readingbat.misc.Endpoints.CHALLENGE_ENDPOINT
import com.github.readingbat.misc.Endpoints.CHALLENGE_GROUP_ENDPOINT
import com.github.readingbat.misc.User
import com.github.readingbat.posts.ChallengeHistory
import io.ktor.http.cio.websocket.CloseReason
import io.ktor.http.cio.websocket.CloseReason.Codes
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.close
import io.ktor.http.cio.websocket.readText
import io.ktor.routing.Routing
import io.ktor.websocket.webSocket
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import mu.KLogging
import redis.clients.jedis.JedisPubSub

internal object WsEndoints : KLogging() {

  fun Routing.wsEndpoints(content: ReadingBatContent) {
    webSocket("$CHALLENGE_ENDPOINT/{classCode}") {
      val classCode =
        call.parameters["classCode"]?.let { ClassCode(it) } ?: throw InvalidPathException("Missing class code")
      incoming
        .receiveAsFlow()
        .mapNotNull { it as? Frame.Text }
        .collect { frame ->
          val inboundMsg = frame.readText()
          withRedis { redis ->
            redis?.subscribe(object : JedisPubSub() {
              override fun onMessage(channel: String?, message: String?) {
                if (message != null)
                  runBlocking {
                    logger.debug { "Sending data $message from $channel" }
                    outgoing.send(Frame.Text(message))
                  }
              }
            }, classCode.value)
          }

          if (inboundMsg.equals("bye", ignoreCase = true)) {
            close(CloseReason(Codes.NORMAL, "Client said BYE"))
          }
        }
    }

    webSocket("$CHALLENGE_GROUP_ENDPOINT/{languageName}/{groupName}/{classCode}") {
      val languageName =
        call.parameters["languageName"]?.let { LanguageName(it) } ?: throw InvalidPathException("Missing language name")
      val groupName =
        call.parameters["groupName"]?.let { GroupName(it) } ?: throw InvalidPathException("Missing group name")
      val classCode =
        call.parameters["classCode"]?.let { ClassCode(it) } ?: throw InvalidPathException("Missing class code")
      val challenges = content.findGroup(languageName.toLanguageType(), groupName).challenges

      incoming
        .receiveAsFlow()
        .mapNotNull { it as? Frame.Text }
        .collect { frame ->
          val inboundMsg = frame.readText()
          withRedis { redis ->
            if (redis != null) {
              val enrollees = if (classCode.isEnabled) classCode.fetchEnrollees(redis) else emptyList()

              challenges.forEach { challenge ->
                if (classCode.isEnabled && enrollees.isNotEmpty()) {
                  val funcInfo = challenge.funcInfo(content)
                  val challengeName = challenge.challengeName
                  val numCalls = funcInfo.invocations.size
                  var totAttemptedAtLeastOne = 0
                  var totAllCorrect = 0
                  var totCorrect = 0

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
                            User.gson.fromJson(json, ChallengeHistory::class.java) ?: ChallengeHistory(invocation)
                          if (history.correct)
                            numCorrect++
                        }
                      }

                    if (attempted > 0)
                      totAttemptedAtLeastOne++

                    if (numCorrect == numCalls)
                      totAllCorrect++

                    totCorrect += numCorrect
                  }

                  val avgCorrect =
                    if (totAttemptedAtLeastOne > 0) totCorrect / totAttemptedAtLeastOne.toFloat() else 0.0f

                  val msg = " ($numCalls | $totAttemptedAtLeastOne | $totAllCorrect | ${"%.1f".format(avgCorrect)})"
                  val challengeStats = ChallengeStats(challengeName.value, msg)
                  val json = User.gson.toJson(challengeStats)

                  runBlocking {
                    logger.debug { "Sending data $json" }
                    outgoing.send(Frame.Text(json))
                  }
                }
              }
            }
          }

          logger.info { "Closing $languageName/$groupName/$classCode websocket" }
          close(CloseReason(Codes.NORMAL, "Client said BYE"))
        }
    }
  }
}

internal class ChallengeStats(val challengeName: String, val msg: String)
