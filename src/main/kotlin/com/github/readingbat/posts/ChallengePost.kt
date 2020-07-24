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

package com.github.readingbat.posts

import com.github.pambrose.common.script.KotlinScript
import com.github.pambrose.common.script.PythonScript
import com.github.pambrose.common.util.*
import com.github.readingbat.dsl.InvalidConfigurationException
import com.github.readingbat.dsl.LanguageType.Java
import com.github.readingbat.dsl.LanguageType.Kotlin
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.BrowserSession
import com.github.readingbat.misc.CheckAnswersJs.challengeSrc
import com.github.readingbat.misc.CheckAnswersJs.groupSrc
import com.github.readingbat.misc.CheckAnswersJs.langSrc
import com.github.readingbat.misc.Constants.CHALLENGE_ROOT
import com.github.readingbat.misc.Constants.MSG
import com.github.readingbat.misc.Constants.RESP
import com.github.readingbat.misc.FormFields.CHALLENGE_ANSWERS_KEY
import com.github.readingbat.misc.FormFields.CHALLENGE_NAME_KEY
import com.github.readingbat.misc.FormFields.GROUP_NAME_KEY
import com.github.readingbat.misc.FormFields.LANGUAGE_NAME_KEY
import com.github.readingbat.misc.KeyConstants.CORRECT_ANSWERS_KEY
import com.github.readingbat.misc.PageUtils.pathOf
import com.github.readingbat.misc.User
import com.github.readingbat.misc.User.Companion.gson
import com.github.readingbat.misc.User.Companion.saveChallengeAnswers
import com.github.readingbat.server.*
import com.github.readingbat.server.ChallengeName.Companion.getChallengeName
import com.github.readingbat.server.GroupName.Companion.getGroupName
import com.github.readingbat.server.LanguageName.Companion.getLanguageName
import io.ktor.application.call
import io.ktor.request.receiveParameters
import io.ktor.response.respondText
import io.ktor.sessions.get
import io.ktor.sessions.sessions
import kotlinx.coroutines.delay
import mu.KLogging
import redis.clients.jedis.Jedis
import javax.script.ScriptException
import kotlin.time.milliseconds

internal data class StudentInfo(val studentId: String, val firstName: String, val lastName: String)

internal data class ClassEnrollment(val sessionId: String,
                                    val students: List<StudentInfo> = mutableListOf())

internal data class ChallengeResults(val invocation: Invocation,
                                     val userResponse: String = "",
                                     val answered: Boolean = false,
                                     val correct: Boolean = false)

internal class DashboardInfo(val userId: String,
                             val complete: Boolean,
                             val numCorrect: Int,
                             maxHistoryLength: Int,
                             origHistory: ChallengeHistory) {
  val history = DashboardHistory(origHistory.invocation.value,
                                 origHistory.correct,
                                 origHistory.answers.asReversed().take(maxHistoryLength).joinToString("<br>"))
}

internal class DashboardHistory(val invocation: String,
                                val correct: Boolean = false,
                                val answers: String)

internal data class ChallengeHistory(var invocation: Invocation,
                                     var correct: Boolean = false,
                                     var incorrectAttempts: Int = 0,
                                     val answers: MutableList<String> = mutableListOf()) {
  fun markCorrect() {
    correct = true
  }

  fun markIncorrect(userResponse: String) {
    correct = false
    if (userResponse.isNotBlank() && userResponse !in answers) {
      incorrectAttempts++
      answers += userResponse
    }
  }

  fun markUnanswered() {
    correct = false
  }
}

internal class ChallengeNames(paramMap: Map<String, String>) {
  val languageName = LanguageName(paramMap[langSrc] ?: throw InvalidConfigurationException("Missing language name"))
  val groupName = GroupName(paramMap[groupSrc] ?: throw InvalidConfigurationException("Missing group name"))
  val challengeName =
    ChallengeName(paramMap[challengeSrc] ?: throw InvalidConfigurationException("Missing challenge name"))
}

internal object ChallengePost : KLogging() {

  private fun String.isJavaBoolean() = this == "true" || this == "false"
  private fun String.isPythonBoolean() = this == "True" || this == "False"

  private infix fun String.equalsAsJvmScalar(that: String) =
    when {
      isEmpty() || that.isEmpty() -> false
      isDoubleQuoted() || that.isDoubleQuoted() -> this == that
      contains(".") || that.contains(".") -> toDouble() == that.toDouble()
      isJavaBoolean() && that.isJavaBoolean() -> toBoolean() == that.toBoolean()
      else -> toInt() == that.toInt()
    }

  private infix fun String.equalsAsPythonScalar(that: String) =
    when {
      isEmpty() || that.isEmpty() -> false
      isDoubleQuoted() -> this == that
      isSingleQuoted() -> singleToDoubleQuoted() == that
      contains(".") || that.contains(".") -> toDouble() == that.toDouble()
      isPythonBoolean() && that.isPythonBoolean() -> toBoolean() == that.toBoolean()
      else -> toInt() == that.toInt()
    }

  suspend fun PipelineCall.checkAnswers(content: ReadingBatContent, user: User?, redis: Jedis?) {
    val params = call.receiveParameters()
    val paramMap = params.entries().map { it.key to it.value[0] }.toMap()
    val names = ChallengeNames(paramMap)
    val isJvm = names.languageName in listOf(Java.languageName, Kotlin.languageName)
    val userResponses = params.entries().filter { it.key.startsWith(RESP) }
    val challenge = content.findChallenge(names.languageName, names.groupName, names.challengeName)
    val funcInfo = challenge.funcInfo(content)
    val kotlinScriptEngine by lazy { KotlinScript() }
    val pythonScriptEngine by lazy { PythonScript() }

    fun checkWithAnswer(isJvm: Boolean, userResponse: String, answer: String) =
      try {
        logger.debug("""Comparing user response: "$userResponse" with answer: "$answer"""")
        if (isJvm) {
          if (answer.isBracketed())
            answer.equalsAsKotlinList(userResponse, kotlinScriptEngine)
          else
            userResponse equalsAsJvmScalar answer
        }
        else {
          if (answer.isBracketed())
            answer.equalsAsPythonList(userResponse, pythonScriptEngine)
          else
            userResponse equalsAsPythonScalar answer
        }
      } catch (e: Exception) {
        false
      }

    logger.debug("Found ${userResponses.size} user responses in $paramMap")

    val results =
      userResponses.indices
        .map { i ->
          val userResponse = paramMap[RESP + i]?.trim() ?: throw InvalidConfigurationException("Missing user response")
          val answer = funcInfo.answers[i]
          val answered = userResponse.isNotBlank()
          ChallengeResults(invocation = funcInfo.invocations[i],
                           userResponse = userResponse,
                           answered = answered,
                           correct = if (answered) checkWithAnswer(isJvm, userResponse, answer) else false)
        }

    // Save whether all the answers for the challenge were correct
    if (redis.isNotNull()) {
      val browserSession = call.sessions.get<BrowserSession>()
      user.saveChallengeAnswers(content, browserSession, names, paramMap, funcInfo, userResponses, results, redis)
    }

    delay(200.milliseconds.toLongMilliseconds())

    // Return values: 0 = not answered, 1 = correct, 2 = incorrect
    val answerMapping =
      results
        .map {
          when {
            !it.answered -> 0
            it.correct -> 1
            else -> 2
          }
        }
    call.respondText(answerMapping.toString())
  }

  suspend fun PipelineCall.clearChallengeAnswers(content: ReadingBatContent,
                                                 user: User?,
                                                 redis: Jedis): String {
    val params = call.receiveParameters()

    val languageName = params.getLanguageName(LANGUAGE_NAME_KEY)
    val groupName = params.getGroupName(GROUP_NAME_KEY)
    val challengeName = params.getChallengeName(CHALLENGE_NAME_KEY)

    val correctAnswersKey = params[CORRECT_ANSWERS_KEY] ?: ""
    val challengeAnswersKey = params[CHALLENGE_ANSWERS_KEY] ?: ""

    val challenge = content[languageName, groupName, challengeName]
    val funcInfo = challenge.funcInfo(content)
    val path = pathOf(CHALLENGE_ROOT, languageName, groupName, challengeName)

    if (correctAnswersKey.isNotEmpty()) {
      logger.info { "Clearing answers for $challenge $correctAnswersKey" }
      redis.del(correctAnswersKey)
    }

    if (challengeAnswersKey.isNotEmpty()) {
      logger.info { "Clearing answers for $challenge $challengeAnswersKey" }
      redis.del(challengeAnswersKey)
    }

    user?.resetHistory(content.maxHistoryLength, funcInfo, languageName, groupName, challengeName, redis)

    throw RedirectException("$path?$MSG=${"Answers cleared".encode()}")
  }

  suspend fun PipelineCall.clearGroupAnswers(content: ReadingBatContent, user: User?, redis: Jedis): String {
    val parameters = call.receiveParameters()

    val languageName = parameters.getLanguageName(LANGUAGE_NAME_KEY)
    val groupName = parameters.getGroupName(GROUP_NAME_KEY)

    val correctJson = parameters[CORRECT_ANSWERS_KEY] ?: ""
    val challengeJson = parameters[CHALLENGE_ANSWERS_KEY] ?: ""

    val correctAnswersKeys = gson.fromJson(correctJson, List::class.java) as List<String>
    val challengeAnswersKeys = gson.fromJson(challengeJson, List::class.java) as List<String>

    val path = pathOf(CHALLENGE_ROOT, languageName, groupName)

    correctAnswersKeys
      .forEach { correctAnswersKey ->
        if (correctAnswersKey.isNotEmpty()) {
          logger.info { "Clearing answers for $correctAnswersKey" }
          redis.del(correctAnswersKey)
        }
      }

    challengeAnswersKeys
      .forEach { challengeAnswersKey ->
        if (challengeAnswersKey.isNotEmpty()) {
          logger.info { "Clearing answers for $challengeAnswersKey" }
          redis.del(challengeAnswersKey)
        }
      }

    if (user.isNotNull()) {
      for (challenge in content.findGroup(languageName.toLanguageType(), groupName).challenges) {
        logger.info { "Clearing answers for ${challenge.challengeName}" }
        val funcInfo = challenge.funcInfo(content)
        user.resetHistory(content.maxHistoryLength, funcInfo, languageName, groupName, challenge.challengeName, redis)
      }
    }

    throw RedirectException("$path?$MSG=${"Answers cleared".encode()}")
  }

  private fun String.equalsAsKotlinList(other: String, scriptEngine: KotlinScript): Boolean {
    val compareExpr = "listOf(${this.trimEnds()}) == listOf(${other.trimEnds()})"
    logger.debug { "Check answers expression: $compareExpr" }
    return try {
      scriptEngine.eval(compareExpr) as Boolean
    } catch (e: ScriptException) {
      logger.info { "Caught exception comparing $this and $other: ${e.message} in $compareExpr" }
      false
    }
  }

  private fun String.equalsAsPythonList(other: String, scriptEngine: PythonScript): Boolean {
    val compareExpr = "${this@equalsAsPythonList.trim()} == ${other.trim()}"
    logger.debug { "Check answers expression: $compareExpr" }
    return try {
      scriptEngine.eval(compareExpr) as Boolean
    } catch (e: ScriptException) {
      logger.info { "Caught exception comparing $this and $other: ${e.message} in: $compareExpr" }
      false
    }
  }
}