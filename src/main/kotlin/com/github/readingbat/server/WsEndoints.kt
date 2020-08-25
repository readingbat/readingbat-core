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
import com.github.pambrose.common.redis.RedisUtils.withRedisPool
import com.github.pambrose.common.time.format
import com.github.pambrose.common.util.isNotNull
import com.github.readingbat.common.ClassCode
import com.github.readingbat.common.Constants.PING_CODE
import com.github.readingbat.common.Endpoints.CHALLENGE_ENDPOINT
import com.github.readingbat.common.Endpoints.CHALLENGE_GROUP_ENDPOINT
import com.github.readingbat.common.Metrics
import com.github.readingbat.dsl.Challenge
import com.github.readingbat.dsl.InvalidPathException
import com.github.readingbat.dsl.agentLaunchId
import com.github.readingbat.pages.ChallengeGroupPage.cols
import com.github.readingbat.posts.ChallengeHistory
import com.github.readingbat.server.ServerUtils.rows
import com.github.readingbat.server.User.Companion.gson
import io.ktor.http.cio.websocket.*
import io.ktor.http.cio.websocket.CloseReason.*
import io.ktor.routing.*
import io.ktor.websocket.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KLogging
import redis.clients.jedis.JedisPubSub
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.TimeSource
import kotlin.time.seconds

internal object WsEndoints : KLogging() {

  private const val LANG_NAME = "languageName"
  private const val GROUP_NAME = "groupName"
  private const val CLASS_CODE = "classCode"
  private const val CHALLENGE_MD5 = "challengeMd5"

  fun classTopicName(classCode: ClassCode, challengeMd5: String) = keyOf(classCode, challengeMd5)

  fun Routing.wsEndpoints(metrics: Metrics, content: () -> ReadingBatContent) {

    webSocket("$CHALLENGE_ENDPOINT/{$CLASS_CODE}/{$CHALLENGE_MD5}") {
      val classCode =
        call.parameters[CLASS_CODE]?.let { ClassCode(it) } ?: throw InvalidPathException("Missing class code")
      val challengeMd5 = call.parameters[CHALLENGE_MD5] ?: throw InvalidPathException("Missing challenge md5")
      val desc = "$CHALLENGE_ENDPOINT/$classCode/$challengeMd5"
      val finished = BooleanMonitor(false)

      logger.info { "Opening student answers websocket for $desc" }

      outgoing.invokeOnClose { e ->
        logger.info { "Close received for student answers websocket for $desc" }
        finished.set(true)
      }

      metrics.measureEndpointRequest("/websocket_student_answers") {
        try {
          metrics.wsStudentAnswerCount.labels(agentLaunchId()).inc()
          metrics.wsStudentAnswerGauge.labels(agentLaunchId()).inc()

          incoming
            .consumeAsFlow()
            .mapNotNull { it as? Frame.Text }
            .collect { frame ->
              val inboundMsg = frame.readText()
              // Check redis early to see if it is available
              withRedisPool { redis ->
                if (redis.isNotNull()) {
                  val clock = TimeSource.Monotonic
                  val start = clock.markNow()

                  launch {
                    var secs = 0
                    while (!finished.get()) {
                      val duration = start.elapsedNow()
                      val message = PingMessage(duration.format())
                      val json = gson.toJson(message)
                      logger.debug { "Sending $json" }
                      outgoing.send(Frame.Text(json))
                      delay(1.seconds)
                      secs += 1
                    }
                  }
                }

                val pubsub =
                  object : JedisPubSub() {
                    override fun onMessage(channel: String?, message: String?) {
                      if (message.isNotNull()) {
                        logger.info { "Sending data $message from $channel to $challengeMd5" }
                        metrics.wsStudentAnswerResponseCount.labels(agentLaunchId()).inc()
                        runBlocking {
                          outgoing.send(Frame.Text(message))
                        }
                      }
                    }

                    override fun onSubscribe(channel: String?, subscribedChannels: Int) {
                      logger.info { "Subscribed to $channel to $challengeMd5" }
                    }

                    override fun onUnsubscribe(channel: String?, subscribedChannels: Int) {
                      logger.info { "Unsubscribed from $channel to $challengeMd5" }
                    }
                  }

                val subscriber = launch {
                  redis?.subscribe(pubsub, classTopicName(classCode, challengeMd5))
                }

                // Wait for closure to happen
                finished.waitUntilTrue()

                pubsub.unsubscribe()
                runBlocking { subscriber.join() }
              }
            }
        } finally {
          metrics.wsStudentAnswerGauge.labels(agentLaunchId()).dec()
          close(CloseReason(Codes.GOING_AWAY, "Client disconnected"))
          logger.info { "Closed student answers websocket for $desc" }
        }
      }
    }

    webSocket("$CHALLENGE_GROUP_ENDPOINT/{$LANG_NAME}/{$GROUP_NAME}/{$CLASS_CODE}") {
      val (languageName, groupName, classCode) =
        Triple(
          call.parameters[LANG_NAME]?.let { LanguageName(it) } ?: throw InvalidPathException("Missing language name"),
          call.parameters[GROUP_NAME]?.let { GroupName(it) } ?: throw InvalidPathException("Missing group name"),
          call.parameters[CLASS_CODE]?.let { ClassCode(it) } ?: throw InvalidPathException("Missing class code"))
      val challenges = content.invoke().findGroup(languageName.toLanguageType(), groupName).challenges
      val desc = "$CHALLENGE_ENDPOINT/$languageName/$groupName/$classCode"
      val finished = AtomicBoolean(false)

      logger.info { "Opening class statistics websocket for $desc" }

      outgoing.invokeOnClose { e ->
        logger.info { "Close received for class statistics websocket for $desc" }
        finished.set(true)
      }

      metrics.measureEndpointRequest("/websocket_class_statistics") {
        try {
          metrics.wsClassStatisticsCount.labels(agentLaunchId()).inc()
          metrics.wsClassStatisticsGauge.labels(agentLaunchId()).inc()

          incoming
            .consumeAsFlow()
            .mapNotNull { it as? Frame.Text }
            .collect { frame ->
              val inboundMsg = frame.readText()
              withRedisPool { redis ->
                if (redis.isNotNull() && classCode.isEnabled) {
                  val enrollees = classCode.fetchEnrollees(redis)
                  if (enrollees.isNotEmpty()) {
                    // Reorder challenges to return values left to right
                    val ltor = mutableListOf<Challenge>()
                    val rows = challenges.size.rows(cols)
                    repeat(rows) { i ->
                      challenges
                        .apply {
                          ltor += elementAt(i)
                          elementAtOrNull(i + rows)?.also { ltor += it }
                          elementAtOrNull(i + (2 * rows))?.also { ltor += it }
                        }
                    }

                    ltor.forEach challenge@{ challenge ->
                      val funcInfo = challenge.funcInfo(content.invoke())
                      val challengeName = challenge.challengeName
                      val numCalls = funcInfo.invocations.size
                      var totAttemptedAtLeastOne = 0
                      var totAllCorrect = 0
                      var totCorrect = 0
                      var incorrectAttempts = 0
                      var likes = 0
                      var dislikes = 0

                      enrollees.forEach { enrollee ->
                        var attempted = 0
                        var numCorrect = 0

                        funcInfo.invocations.forEach { invocation ->
                          val historyKey = enrollee.answerHistoryKey(languageName, groupName, challengeName, invocation)

                          if (redis.exists(historyKey)) {
                            attempted++
                            val json = redis[historyKey] ?: ""
                            val history =
                              gson.fromJson(json, ChallengeHistory::class.java) ?: ChallengeHistory(invocation)
                            if (history.correct)
                              numCorrect++

                            incorrectAttempts += history.incorrectAttempts
                          }

                          if (finished.get())
                            return@challenge
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

                        if (finished.get())
                          return@challenge
                      }

                      val avgCorrect =
                        if (totAttemptedAtLeastOne > 0) totCorrect / totAttemptedAtLeastOne.toFloat() else 0.0f
                      val avgCorrectFmt = "%.1f".format(avgCorrect)

                      val msg =
                        " ($numCalls | $totAttemptedAtLeastOne | $totAllCorrect | $avgCorrectFmt | $incorrectAttempts | $likes/$dislikes)"

                      val challengeStats = ChallengeStats(challengeName.value, msg)
                      val json = gson.toJson(challengeStats)

                      metrics.wsClassStatisticsResponseCount.labels(agentLaunchId()).inc()
                      logger.debug { "Sending data $json" }
                      runBlocking { outgoing.send(Frame.Text(json)) }

                      if (finished.get())
                        return@challenge
                    }
                  }
                }
              }

              // Shut things down to exit collect
              outgoing.close()
              incoming.cancel()
            }
        } finally {
          metrics.wsClassStatisticsGauge.labels(agentLaunchId()).dec()
          close(CloseReason(Codes.GOING_AWAY, "Client disconnected"))
          logger.info { "Closed class statistics websocket for $desc" }
        }
      }
    }
  }
}

internal class ChallengeStats(val challengeName: String, val msg: String)

internal class PingMessage(val msg: String) {
  val type = PING_CODE
}

