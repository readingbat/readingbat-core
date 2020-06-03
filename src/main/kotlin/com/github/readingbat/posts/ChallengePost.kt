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
import com.github.readingbat.misc.PageUtils.pathOf
import com.github.readingbat.misc.User
import com.github.readingbat.misc.User.Companion.gson
import com.github.readingbat.misc.User.Companion.saveChallengeAnswers
import com.github.readingbat.misc.isValidUser
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
                                     val userResponse: String,
                                     val answered: Boolean,
                                     val correct: Boolean)

internal class DashboardInfo(maxHistoryLength: Int,
                             val userId: String,
                             val complete: Boolean,
                             val numCorrect: Int,
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

  fun markIncorrect(userResp: String) {
    correct = false
    if (userResp.isNotEmpty() && userResp !in answers) {
      incorrectAttempts++
      answers += userResp
    }
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
    val userResps = params.entries().filter { it.key.startsWith(RESP) }
    val challenge =
      content
        .findLanguage(names.languageName.toLanguageType())
        .findChallenge(names.groupName.value, names.challengeName.value)
    val funcInfo = challenge.funcInfo(content)
    val kotlinScriptEngine by lazy { KotlinScript() }
    val pythonScriptEngine by lazy { PythonScript() }

    fun checkWithAnswer(isJvm: Boolean, userResp: String, answer: String) =
      try {
        logger.debug("""Comparing user response: "$userResp" with answer: "$answer"""")
        if (isJvm) {
          if (answer.isBracketed())
            answer.equalsAsKotlinList(userResp, kotlinScriptEngine)
          else
            userResp equalsAsJvmScalar answer
        }
        else {
          if (answer.isBracketed())
            answer.equalsAsPythonList(userResp, pythonScriptEngine)
          else
            userResp equalsAsPythonScalar answer
        }
      } catch (e: Exception) {
        false
      }

    logger.debug("Found ${userResps.size} user responses in $paramMap")

    val results =
      userResps.indices
        .map { i ->
          val userResponse =
            paramMap[RESP + i]?.trim() ?: throw InvalidConfigurationException("Missing user response")
          val answer = funcInfo.answers[i]
          val answered = userResponse.isNotEmpty()
          ChallengeResults(invocation = funcInfo.invocations[i],
                           userResponse = userResponse,
                           answered = answered,
                           correct = if (answered) checkWithAnswer(isJvm, userResponse, answer) else false)
        }

    // Save whether all the answers for the challenge were correct
    if (redis != null) {
      val browserSession = call.sessions.get<BrowserSession>()
      user.saveChallengeAnswers(content, browserSession, names, paramMap, funcInfo, userResps, results, redis)
    }

    /*
    results
      .filter { it.answered }
      .forEach { logger.debug { "Item with args; ${it.arguments} was correct: ${it.correct}" } }
    */

    delay(200.milliseconds.toLongMilliseconds())
    call.respondText(results.map { it.correct }.toString())
  }

  suspend fun PipelineCall.clearChallengeAnswers(content: ReadingBatContent,
                                                 user: User?,
                                                 redis: Jedis): String {
    val parameters = call.receiveParameters()
    val languageName = parameters.getLanguageName(LANGUAGE_NAME_KEY)
    val groupName = parameters.getGroupName(GROUP_NAME_KEY)
    val challengeName = parameters.getChallengeName(CHALLENGE_NAME_KEY)
    val challengeAnswersKey = parameters[CHALLENGE_ANSWERS_KEY] ?: ""
    val challenge = content.findChallenge(languageName.toLanguageType(), groupName, challengeName)
    val path = pathOf(CHALLENGE_ROOT, languageName, groupName, challengeName)

    val msg =
      if (user.isValidUser(redis)) {
        "Invalid user"
      }
      else {
        if (challengeAnswersKey.isNotEmpty()) {
          logger.info { "Clearing answers for $challenge" }
          redis.del(challengeAnswersKey)
        }
        "Answers cleared"
      }

    throw RedirectException("$path?$MSG=${msg.encode()}")
  }

  suspend fun PipelineCall.clearGroupAnswers(user: User?, redis: Jedis): String {
    val parameters = call.receiveParameters()
    val languageName = parameters.getLanguageName(LANGUAGE_NAME_KEY)
    val groupName = parameters.getGroupName(GROUP_NAME_KEY)
    val json = parameters[CHALLENGE_ANSWERS_KEY] ?: ""
    val challengeAnswersKeys = gson.fromJson(json, List::class.java) as List<String>
    val path = pathOf(CHALLENGE_ROOT, languageName, groupName)

    val msg =
      if (user.isValidUser(redis)) {
        "Invalid user"
      }
      else {
        challengeAnswersKeys
          .forEach { challengeAnswersKey ->
            if (challengeAnswersKey.isNotEmpty()) {
              logger.info { "Clearing answers for $challengeAnswersKey" }
              redis.del(challengeAnswersKey)
            }
          }
        "Answers cleared"
      }
    throw RedirectException("$path?$MSG=${msg.encode()}")
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