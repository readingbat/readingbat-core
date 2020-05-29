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
import com.github.readingbat.dsl.LanguageType.Companion.toLanguageType
import com.github.readingbat.dsl.LanguageType.Java
import com.github.readingbat.dsl.LanguageType.Kotlin
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.CheckAnswersJs.challengeSrc
import com.github.readingbat.misc.CheckAnswersJs.groupSrc
import com.github.readingbat.misc.CheckAnswersJs.langSrc
import com.github.readingbat.misc.Constants.RESP
import com.github.readingbat.misc.UserId.Companion.saveAnswers
import com.github.readingbat.server.PipelineCall
import io.ktor.application.call
import io.ktor.request.receiveParameters
import io.ktor.response.respondText
import kotlinx.coroutines.delay
import mu.KLogging
import javax.script.ScriptException
import kotlin.time.milliseconds

internal data class StudentInfo(val studentId: String, val firstName: String, val lastName: String)

internal data class ClassEnrollment(val sessionId: String,
                                    val students: List<StudentInfo> = mutableListOf())

internal data class ChallengeResults(val invocation: String,
                                     val userResponse: String,
                                     val answered: Boolean,
                                     val correct: Boolean)

internal class DashboardInfo(maxHistoryLength: Int,
                             val userId: String,
                             val complete: Boolean,
                             val numCorrect: Int,
                             origHistory: ChallengeHistory) {
  val history = DashboardHistory(origHistory.invocation,
                                 origHistory.correct,
                                 origHistory.answers.asReversed().take(maxHistoryLength).joinToString("<br>"))
}

internal class DashboardHistory(val invocation: String,
                                val correct: Boolean = false,
                                val answers: String)

internal data class ChallengeHistory(var invocation: String,
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

internal class ChallengeNames(compareMap: Map<String, String>) {
  val languageName: String = compareMap[langSrc] ?: throw InvalidConfigurationException("Missing language")
  val groupName: String = compareMap[groupSrc] ?: throw InvalidConfigurationException("Missing group name")
  val challengeName: String = compareMap[challengeSrc] ?: throw InvalidConfigurationException("Missing challenge name")
}

internal object CheckAnswers : KLogging() {

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

  suspend fun PipelineCall.checkAnswers(content: ReadingBatContent) {
    val params = call.receiveParameters()
    val compareMap = params.entries().map { it.key to it.value[0] }.toMap()
    val names = ChallengeNames(compareMap)
    val isJvm = names.languageName in listOf(Java.lowerName, Kotlin.lowerName)
    val userResps = params.entries().filter { it.key.startsWith(RESP) }
    val challenge =
      content
        .findLanguage(names.languageName.toLanguageType())
        .findChallenge(names.groupName, names.challengeName)
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

    logger.debug("Found ${userResps.size} user responses in $compareMap")

    val results =
      userResps.indices.map { i ->
        val userResponse =
          compareMap[RESP + i]?.trim() ?: throw InvalidConfigurationException("Missing user response")
        val answer = funcInfo.answers[i]
        val answered = userResponse.isNotEmpty()
        ChallengeResults(invocation = funcInfo.invocations[i],
                         userResponse = userResponse,
                         answered = answered,
                         correct = if (answered) checkWithAnswer(isJvm, userResponse, answer) else false)
      }

    // Save whether all the answers for the challenge were correct
    saveAnswers(content, names, compareMap, funcInfo, userResps, results)

    /*
    results
      .filter { it.answered }
      .forEach { logger.debug { "Item with args; ${it.arguments} was correct: ${it.correct}" } }
    */

    delay(200.milliseconds.toLongMilliseconds())
    call.respondText(results.map { it.correct }.toString())
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