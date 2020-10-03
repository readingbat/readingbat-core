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
import com.github.pambrose.common.redis.RedisUtils.withNonNullRedisPool
import com.github.pambrose.common.redis.RedisUtils.withRedisPool
import com.github.pambrose.common.time.format
import com.github.pambrose.common.util.isNotNull
import com.github.pambrose.common.util.isNull
import com.github.readingbat.common.ClassCode
import com.github.readingbat.common.CommonUtils.keyOf
import com.github.readingbat.common.CommonUtils.md5Of
import com.github.readingbat.common.CommonUtils.pathOf
import com.github.readingbat.common.Constants.COLUMN_CNT
import com.github.readingbat.common.Constants.NO
import com.github.readingbat.common.Constants.PING_CODE
import com.github.readingbat.common.Constants.UNANSWERED
import com.github.readingbat.common.Constants.UNKNOWN
import com.github.readingbat.common.Constants.YES
import com.github.readingbat.common.Endpoints.CHALLENGE_ENDPOINT
import com.github.readingbat.common.Endpoints.CHALLENGE_GROUP_ENDPOINT
import com.github.readingbat.common.Endpoints.CLASS_SUMMARY_ENDPOINT
import com.github.readingbat.common.Endpoints.STUDENT_SUMMARY_ENDPOINT
import com.github.readingbat.common.Endpoints.WS_ROOT
import com.github.readingbat.common.Metrics
import com.github.readingbat.common.User
import com.github.readingbat.common.User.Companion.gson
import com.github.readingbat.common.User.Companion.toUser
import com.github.readingbat.common.isNotValidUser
import com.github.readingbat.dsl.Challenge
import com.github.readingbat.dsl.InvalidRequestException
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.dsl.agentLaunchId
import com.github.readingbat.server.ReadingBatServer.redisPool
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
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import redis.clients.jedis.JedisPubSub
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.TimeSource
import kotlin.time.seconds

internal object WsEndoints : KLogging() {

  private const val LANGUAGE_NAME = "languageName"
  private const val GROUP_NAME = "groupName"
  private const val STUDENT_ID = "studentId"
  private const val CLASS_CODE = "classCode"
  private const val CHALLENGE_MD5 = "challengeMd5"

  fun classTopicName(classCode: ClassCode, challengeMd5: String) = keyOf(classCode, challengeMd5)

  fun Routing.wsEndpoints(metrics: Metrics, contentSrc: () -> ReadingBatContent) {

    fun validateContext(languageName: LanguageName?,
                        groupName: GroupName?,
                        classCode: ClassCode,
                        student: User?,
                        user: User,
                        context: String) =
      redisPool?.withRedisPool { redis ->
        when {
          redis.isNull() -> false to context
          languageName.isNotNull() && languageName.isNotValid() -> false to "Invalid language: $languageName"
          groupName.isNotNull() && groupName.isNotValid() -> false to "Invalid group: $groupName"
          classCode.isNotValid() -> false to "Invalid class code: $classCode"
          classCode.isNotEnabled -> false to "Class code not enabled"
          student.isNotNull() && student.isNotValidUser() -> false to "Invalid student id: ${student.userId}"
          student.isNotNull() && student.isNotEnrolled(classCode) -> false to "Student not enrolled in class"
          user.isNotValidUser() -> false to "Invalid user id: ${user.userId}"
          classCode.fetchClassTeacherId() != user.userId -> {
            val teacherId = classCode.fetchClassTeacherId()
            false to "User id ${user.userId} does not match class code's teacher Id $teacherId"
          }
          else -> true to ""
        }
      } ?: false to context

    webSocket("$WS_ROOT$CHALLENGE_ENDPOINT/{$CLASS_CODE}/{$CHALLENGE_MD5}") {
      val content = contentSrc.invoke()
      val classCode =
        call.parameters[CLASS_CODE]?.let { ClassCode(it) } ?: throw InvalidRequestException("Missing class code")
      val challengeMd5 = call.parameters[CHALLENGE_MD5] ?: throw InvalidRequestException("Missing challenge md5")
      val finished = BooleanMonitor(false)
      val remote = call.request.origin.remoteHost
      val user = fetchUser() ?: throw InvalidRequestException("Null user")
      val email = user.email // fetchEmail()
      val path = content.functionInfoByMd5(challengeMd5)?.challenge?.path ?: UNKNOWN
      val desc = "${pathOf(WS_ROOT, CHALLENGE_ENDPOINT, classCode, challengeMd5)} ($path) - $remote - $email"

      validateContext(null, null, classCode, null, user, "Student answers")
        .also { (valid, msg) ->
          if (!valid) {
            close(CloseReason(Codes.GOING_AWAY, "Client disconnected"))
            throw InvalidRequestException(msg)
          }
        }

      logger.debug { "Opened student answers websocket for $desc" }

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

              val pubsub =
                object : JedisPubSub() {
                  override fun onMessage(channel: String?, message: String?) {
                    if (message.isNotNull()) {
                      logger.debug { "Sending data $message from $channel to $challengeMd5" }
                      metrics.wsStudentAnswerResponseCount.labels(agentLaunchId()).inc()
                      runBlocking { outgoing.send(Frame.Text(message)) }
                    }
                  }

                  override fun onSubscribe(channel: String?, subscribedChannels: Int) {
                    logger.debug { "Subscribed to $channel for $challengeMd5" }
                  }

                  override fun onUnsubscribe(channel: String?, subscribedChannels: Int) {
                    logger.debug { "Unsubscribed from $channel for $challengeMd5" }
                  }
                }

              redisPool?.withNonNullRedisPool { redis ->
                val subscriber = launch { redis.subscribe(pubsub, classTopicName(classCode, challengeMd5)) }

                // Wait for closure to happen
                finished.waitUntilTrue()

                pubsub.unsubscribe()

                runBlocking { subscriber.join() }
              }
            }
        } finally {
          metrics.wsStudentAnswerGauge.labels(agentLaunchId()).dec()
          close(CloseReason(Codes.GOING_AWAY, "Client disconnected"))
          logger.debug { "Closed student answers websocket for $desc" }
        }
      }
    }

    webSocket("$WS_ROOT$CHALLENGE_GROUP_ENDPOINT/{$LANGUAGE_NAME}/{$GROUP_NAME}/{$CLASS_CODE}") {
      val content = contentSrc.invoke()
      val (languageName, groupName, classCode) =
        Triple(
          call.parameters[LANGUAGE_NAME]?.let { LanguageName(it) } ?: throw InvalidRequestException("Missing language"),
          call.parameters[GROUP_NAME]?.let { GroupName(it) } ?: throw InvalidRequestException("Missing group name"),
          call.parameters[CLASS_CODE]?.let { ClassCode(it) } ?: throw InvalidRequestException("Missing class code"))
      val challenges = content.findGroup(languageName, groupName).challenges
      val finished = AtomicBoolean(false)
      val remote = call.request.origin.remoteHost
      val user = fetchUser() ?: throw InvalidRequestException("Null user")
      val email = user.email //fetchEmail()
      val desc = "${pathOf(WS_ROOT, CHALLENGE_ENDPOINT, languageName, groupName, classCode)} - $remote - $email"

      validateContext(languageName, groupName, classCode, null, user, "Class statistics")
        .also { (valid, msg) ->
          if (!valid) {
            close(CloseReason(Codes.GOING_AWAY, "Client disconnected"))
            throw InvalidRequestException(msg)
          }
        }

      logger.debug { "Opened class statistics websocket for $desc" }

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
              val enrollees = classCode.fetchEnrollees()
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
                      transaction {
                        val historyMd5 = md5Of(languageName, groupName, challengeName, invocation)
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
                        val challengeMd5 = md5Of(languageName, groupName, challengeName)
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

                  val json = gson.toJson(ChallengeStats(challengeName.value, msg))

                  metrics.wsClassStatisticsResponseCount.labels(agentLaunchId()).inc()
                  logger.debug { "Sending data $json" }
                  runBlocking { outgoing.send(Frame.Text(json)) }

                  if (finished.get())
                    break
                }
              }

              // Shut things down to exit collect
              outgoing.close()
              incoming.cancel()
            }
        } finally {
          metrics.wsClassStatisticsGauge.labels(agentLaunchId()).dec()
          close(CloseReason(Codes.GOING_AWAY, "Client disconnected"))
          logger.debug { "Closed class statistics websocket for $desc" }
        }
      }
    }

    webSocket("$WS_ROOT$CLASS_SUMMARY_ENDPOINT/{$LANGUAGE_NAME}/{$GROUP_NAME}/{$CLASS_CODE}") {
      val content = contentSrc.invoke()
      val (languageName, groupName, classCode) =
        Triple(
          call.parameters[LANGUAGE_NAME]?.let { LanguageName(it) } ?: throw InvalidRequestException("Missing language"),
          call.parameters[GROUP_NAME]?.let { GroupName(it) } ?: throw InvalidRequestException("Missing group name"),
          call.parameters[CLASS_CODE]?.let { ClassCode(it) } ?: throw InvalidRequestException("Missing class code"))
      val challenges = content.findGroup(languageName, groupName).challenges
      val finished = AtomicBoolean(false)
      val remote = call.request.origin.remoteHost
      val user = fetchUser() ?: throw InvalidRequestException("Null user")
      val email = user.email //fetchEmail()
      val desc = "${pathOf(WS_ROOT, CLASS_SUMMARY_ENDPOINT, languageName, groupName, classCode)} - $remote - $email"

      validateContext(languageName, groupName, classCode, null, user, "Class summary")
        .also { (valid, msg) ->
          if (!valid) {
            close(CloseReason(Codes.GOING_AWAY, "Client disconnected"))
            throw InvalidRequestException(msg)
          }
        }

      logger.debug { "Opened class summary websocket for $desc" }

      outgoing.invokeOnClose {
        logger.debug { "Close received for class summary websocket for $desc" }
        finished.set(true)
      }

      metrics.measureEndpointRequest("/websocket_class_summary") {
        try {
          metrics.wsClassSummaryCount.labels(agentLaunchId()).inc()
          metrics.wsClassSummaryGauge.labels(agentLaunchId()).inc()

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
                        val historyMd5 = md5Of(languageName, groupName, challengeName, invocation)
                        if (enrollee.historyExists(historyMd5, invocation)) {
                          results +=
                            enrollee.answerHistory(historyMd5, invocation)
                              .let {
                                incorrectAttempts += it.incorrectAttempts
                                if (it.correct) YES else if (it.incorrectAttempts > 0) NO else UNANSWERED
                              }
                        }
                        else {
                          results += UNANSWERED
                        }
                      }

                      if (finished.get())
                        break
                    }

                    if (incorrectAttempts > 0 || results.any { it != UNANSWERED }) {
                      val json =
                        gson.toJson(
                          ClassSummary(enrollee.userId,
                                       challengeName.encode(),
                                       results,
                                       if (incorrectAttempts == 0 && results.all { it == UNANSWERED }) "" else incorrectAttempts.toString()))

                      metrics.wsClassSummaryResponseCount.labels(agentLaunchId()).inc()
                      logger.debug { "Sending data $json" }
                      runBlocking { outgoing.send(Frame.Text(json)) }
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
        } finally {
          metrics.wsClassSummaryGauge.labels(agentLaunchId()).dec()
          close(CloseReason(Codes.GOING_AWAY, "Client disconnected"))
          logger.debug { "Closed class summary websocket for $desc" }
        }
      }
    }

    webSocket("$WS_ROOT$STUDENT_SUMMARY_ENDPOINT/{$LANGUAGE_NAME}/{$STUDENT_ID}/{$CLASS_CODE}") {
      val content = contentSrc.invoke()
      val (languageName, student, classCode) =
        Triple(
          call.parameters[LANGUAGE_NAME]?.let { LanguageName(it) } ?: throw InvalidRequestException("Missing language"),
          call.parameters[STUDENT_ID]?.let { toUser(it) } ?: throw InvalidRequestException("Missing student id"),
          call.parameters[CLASS_CODE]?.let { ClassCode(it) } ?: throw InvalidRequestException("Missing class code"))
      val finished = AtomicBoolean(false)
      val remote = call.request.origin.remoteHost
      val user = fetchUser() ?: throw InvalidRequestException("Null user")
      val email = user.email //fetchEmail()
      val desc =
        "${pathOf(WS_ROOT, CLASS_SUMMARY_ENDPOINT, languageName, student.userId, classCode)} - $remote - $email"

      validateContext(languageName, null, classCode, student, user, "Student summary")
        .also { (valid, msg) ->
          if (!valid) {
            close(CloseReason(Codes.GOING_AWAY, "Client disconnected"))
            throw InvalidRequestException(msg)
          }
        }

      logger.debug { "Opened student summary websocket for $desc" }

      outgoing.invokeOnClose {
        logger.debug { "Close received for student summary websocket for $desc" }
        finished.set(true)
      }

      metrics.measureEndpointRequest("/websocket_student_summary") {
        try {
          metrics.wsStudentSummaryCount.labels(agentLaunchId()).inc()
          metrics.wsStudentSummaryGauge.labels(agentLaunchId()).inc()

          incoming
            .consumeAsFlow()
            .mapNotNull { it as? Frame.Text }
            .collect { frame ->
              val inboundMsg = frame.readText()
              for (challengeGroup in content.findLanguage(languageName).challengeGroups) {
                for (challenge in challengeGroup.challenges) {
                  val funcInfo = challenge.functionInfo(content)
                  val groupName = challengeGroup.groupName
                  val challengeName = challenge.challengeName
                  val numCalls = funcInfo.invocations.size
                  var likes = 0
                  var dislikes = 0
                  var incorrectAttempts = 0
                  var attempted = 0

                  val results = mutableListOf<String>()
                  for (invocation in funcInfo.invocations) {
                    transaction {
                      val historyMd5 = md5Of(languageName, groupName, challengeName, invocation)
                      if (student.historyExists(historyMd5, invocation)) {
                        attempted++
                        results +=
                          student.answerHistory(historyMd5, invocation)
                            .let {
                              incorrectAttempts += it.incorrectAttempts
                              if (it.correct) YES else if (it.incorrectAttempts > 0) NO else UNANSWERED
                            }
                      }
                      else {
                        results += UNANSWERED
                      }
                    }

                    if (finished.get())
                      break
                  }

                  if (incorrectAttempts > 0 || results.any { it != UNANSWERED }) {
                    val msg =
                      if (incorrectAttempts == 0 && results.all { it == UNANSWERED }) "" else incorrectAttempts.toString()
                    val json = gson.toJson(StudentSummary(groupName.encode(), challengeName.encode(), results, msg))

                    metrics.wsClassSummaryResponseCount.labels(agentLaunchId()).inc()
                    logger.debug { "Sending data $json" }
                    runBlocking { outgoing.send(Frame.Text(json)) }
                  }

                  if (finished.get())
                    break
                }
              }

              // Shut things down to exit collect
              outgoing.close()
              incoming.cancel()
            }
        } finally {
          metrics.wsStudentSummaryGauge.labels(agentLaunchId()).dec()
          close(CloseReason(Codes.GOING_AWAY, "Client disconnected"))
          logger.debug { "Closed student summary websocket for $desc" }
        }
      }
    }
  }
}

internal class ChallengeStats(val challengeName: String, val msg: String)

internal class ClassSummary(val userId: String, val challengeName: String, val results: List<String>, val msg: String)

internal class StudentSummary(val groupName: String,
                              val challengeName: String,
                              val results: List<String>,
                              val msg: String)

internal class PingMessage(val msg: String) {
  val type = PING_CODE
}

