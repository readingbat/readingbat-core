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
import com.github.pambrose.common.redis.RedisUtils.withSuspendingRedisPool
import com.github.pambrose.common.time.format
import com.github.pambrose.common.util.encode
import com.github.pambrose.common.util.isNotNull
import com.github.pambrose.common.util.isNull
import com.github.readingbat.common.ClassCode
import com.github.readingbat.common.Constants.COLUMN_CNT
import com.github.readingbat.common.Constants.PING_CODE
import com.github.readingbat.common.Endpoints.CHALLENGE_ENDPOINT
import com.github.readingbat.common.Endpoints.CHALLENGE_GROUP_ENDPOINT
import com.github.readingbat.common.Endpoints.CLASS_SUMMARY_ENDPOINT
import com.github.readingbat.common.Metrics
import com.github.readingbat.common.User
import com.github.readingbat.common.User.Companion.gson
import com.github.readingbat.common.isNotValidUser
import com.github.readingbat.dsl.*
import com.github.readingbat.posts.ChallengeHistory
import com.github.readingbat.server.ReadingBatServer.pool
import com.github.readingbat.server.ServerUtils.fetchEmail
import com.github.readingbat.server.ServerUtils.fetchUser
import com.github.readingbat.server.ServerUtils.rows
import io.ktor.features.*
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

  fun Routing.wsEndpoints(metrics: Metrics, contentSrc: () -> ReadingBatContent) {

    suspend fun WebSocketSession.validateContext(classCode: ClassCode, user: User, context: String) {
      pool.withSuspendingRedisPool { redis ->
        when {
          redis.isNull() -> {
            close(CloseReason(Codes.GOING_AWAY, "Client disconnected"))
            throw RedisUnavailableException(context)
          }
          classCode.isNotValid(redis) -> {
            close(CloseReason(Codes.GOING_AWAY, "Client disconnected"))
            throw InvalidRequestException("Invalid classCode $classCode")
          }
          user.isNotValidUser(redis) -> {
            close(CloseReason(Codes.GOING_AWAY, "Client disconnected"))
            throw InvalidRequestException("Invalid user")
          }
          classCode.fetchClassTeacherId(redis) != user.id -> {
            close(CloseReason(Codes.GOING_AWAY, "Client disconnected"))
            val teacherId = classCode.fetchClassTeacherId(redis)
            throw InvalidRequestException("User id ${user.id} does not match classCode teacher Id $teacherId")
          }
          else -> {
          }
        }
      }
    }

    webSocket("$CHALLENGE_ENDPOINT/{$CLASS_CODE}/{$CHALLENGE_MD5}") {
      val content = contentSrc.invoke()
      val classCode =
        call.parameters[CLASS_CODE]?.let { ClassCode(it) } ?: throw InvalidPathException("Missing class code")
      val challengeMd5 = call.parameters[CHALLENGE_MD5] ?: throw InvalidPathException("Missing challenge md5")
      val finished = BooleanMonitor(false)
      val remote = call.request.origin.remoteHost
      val user = fetchUser() ?: throw InvalidRequestException("Null user")
      val email = fetchEmail()
      val path = content.functionInfoByMd5(challengeMd5)?.challenge?.path ?: "Unknown"
      val desc = "$CHALLENGE_ENDPOINT/$classCode/$challengeMd5 ($path) - $remote - $email"

      validateContext(classCode, user, "Student answers")

      logger.info { "Opened student answers websocket for $desc" }

      outgoing.invokeOnClose {
        logger.debug { "Close received for student answers websocket for $desc" }
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
              pool.withRedisPool { redis ->
                if (redis.isNotNull()) {
                  val clock = TimeSource.Monotonic
                  val start = clock.markNow()

                  launch {
                    var secs = 0
                    while (!finished.get()) {
                      val duration = start.elapsedNow()
                      val json = gson.toJson(PingMessage(duration.format()))
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
                        logger.debug { "Sending data $message from $channel to $challengeMd5" }
                        metrics.wsStudentAnswerResponseCount.labels(agentLaunchId()).inc()
                        runBlocking {
                          outgoing.send(Frame.Text(message))
                        }
                      }
                    }

                    override fun onSubscribe(channel: String?, subscribedChannels: Int) {
                      logger.debug { "Subscribed to $channel for $challengeMd5" }
                    }

                    override fun onUnsubscribe(channel: String?, subscribedChannels: Int) {
                      logger.debug { "Unsubscribed from $channel for $challengeMd5" }
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
      val content = contentSrc.invoke()
      val (languageName, groupName, classCode) =
        Triple(
          call.parameters[LANG_NAME]?.let { LanguageName(it) } ?: throw InvalidPathException("Missing language name"),
          call.parameters[GROUP_NAME]?.let { GroupName(it) } ?: throw InvalidPathException("Missing group name"),
          call.parameters[CLASS_CODE]?.let { ClassCode(it) } ?: throw InvalidPathException("Missing class code"))
      val challenges = content.findGroup(languageName.toLanguageType(), groupName).challenges
      val finished = AtomicBoolean(false)
      val remote = call.request.origin.remoteHost
      val user = fetchUser() ?: throw InvalidRequestException("Null user")
      val email = fetchEmail()
      val desc = "$CHALLENGE_ENDPOINT/$languageName/$groupName/$classCode - $remote - $email"

      validateContext(classCode, user, "Class statistics")

      logger.info { "Opened class statistics websocket for $desc" }

      outgoing.invokeOnClose {
        logger.debug { "Close received for class statistics websocket for $desc" }
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
              pool.withRedisPool { redis ->
                if (redis.isNotNull() && classCode.isEnabled) {
                  val enrollees = classCode.fetchEnrollees(redis)
                  if (enrollees.isNotEmpty()) {
                    // Reorder challenges to return values left to right
                    val ltor = mutableListOf<Challenge>()
                    val rows = challenges.size.rows(COLUMN_CNT)
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
                            break
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
                          break
                      }

                      val avgCorrect =
                        if (totAttemptedAtLeastOne > 0) totCorrect / totAttemptedAtLeastOne.toFloat() else 0.0f
                      val avgCorrectFmt = "%.1f".format(avgCorrect)

                      val msg =
                        " ($numCalls | $totAttemptedAtLeastOne | $totAllCorrect | $avgCorrectFmt | $incorrectAttempts | $likes/$dislikes)"

                      val json = gson.toJson(ChallengeStats(challengeName.value, msg))

                      metrics.wsClassStatisticsResponseCount.labels(agentLaunchId()).inc()
                      logger.debug { "Sending data $json" }
                      runBlocking { outgoing.send(Frame.Text(json)) }

                      if (finished.get())
                        break
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

    webSocket("$CLASS_SUMMARY_ENDPOINT/{$LANG_NAME}/{$GROUP_NAME}/{$CLASS_CODE}") {
      val content = contentSrc.invoke()
      val (languageName, groupName, classCode) =
        Triple(
          call.parameters[LANG_NAME]?.let { LanguageName(it) } ?: throw InvalidPathException("Missing language name"),
          call.parameters[GROUP_NAME]?.let { GroupName(it) } ?: throw InvalidPathException("Missing group name"),
          call.parameters[CLASS_CODE]?.let { ClassCode(it) } ?: throw InvalidPathException("Missing class code"))
      val challenges = content.findGroup(languageName.toLanguageType(), groupName).challenges
      val finished = AtomicBoolean(false)
      val remote = call.request.origin.remoteHost
      val user = fetchUser() ?: throw InvalidRequestException("Null user")
      val email = fetchEmail()
      val desc = "$CLASS_SUMMARY_ENDPOINT/$languageName/$groupName/$classCode - $remote - $email"

      validateContext(classCode, user, "Class overview")

      logger.info { "Opened class overview websocket for $desc" }

      outgoing.invokeOnClose {
        logger.debug { "Close received for class overview websocket for $desc" }
        finished.set(true)
      }

      metrics.measureEndpointRequest("/websocket_class_overview") {
        try {
          metrics.wsClassOverviewCount.labels(agentLaunchId()).inc()
          metrics.wsClassOverviewGauge.labels(agentLaunchId()).inc()

          incoming
            .consumeAsFlow()
            .mapNotNull { it as? Frame.Text }
            .collect { frame ->
              val inboundMsg = frame.readText()
              pool.withRedisPool { redis ->
                if (redis.isNotNull() && classCode.isEnabled) {
                  val enrollees = classCode.fetchEnrollees(redis)
                  if (enrollees.isNotEmpty()) {
                    for (challenge in challenges) {
                      val funcInfo = challenge.functionInfo(content)
                      val challengeName = challenge.challengeName
                      val numCalls = funcInfo.invocations.size
                      var likes = 0
                      var dislikes = 0

                      for (enrollee in enrollees) {
                        var incorrectAttempts = 0
                        var attempted = 0

                        val results = mutableListOf<String>()
                        for (invocation in funcInfo.invocations) {
                          val historyKey = enrollee.answerHistoryKey(languageName, groupName, challengeName, invocation)

                          if (redis.exists(historyKey)) {
                            attempted++
                            val json = redis[historyKey] ?: ""
                            val ch = gson.fromJson(json, ChallengeHistory::class.java) ?: ChallengeHistory(invocation)

                            results +=
                              if (ch.correct)
                                "Y"
                              else
                                if (ch.incorrectAttempts > 0) "N" else "U"

                            incorrectAttempts += ch.incorrectAttempts
                          }
                          else {
                            results += "U"
                          }

                          if (finished.get())
                            break
                        }

                        val msg = " $incorrectAttempts"
                        val json = gson.toJson(ClassOverview(enrollee.id, challengeName.value.encode(), results, msg))

                        metrics.wsClassOverviewResponseCount.labels(agentLaunchId()).inc()
                        logger.debug { "Sending data $json" }
                        runBlocking { outgoing.send(Frame.Text(json)) }

                        if (finished.get())
                          break
                      }
                    }
                  }
                }
              }

              // Shut things down to exit collect
              outgoing.close()
              incoming.cancel()
            }
        } finally {
          metrics.wsClassOverviewGauge.labels(agentLaunchId()).dec()
          close(CloseReason(Codes.GOING_AWAY, "Client disconnected"))
          logger.info { "Closed class overview websocket for $desc" }
        }
      }
    }
  }
}

internal class ChallengeStats(val challengeName: String, val msg: String)

internal class ClassOverview(val userId: String, val challengeName: String, val results: List<String>, val msg: String)

internal class PingMessage(val msg: String) {
  val type = PING_CODE
}

