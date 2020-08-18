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

import com.github.pambrose.common.script.KotlinScriptPool
import com.github.pambrose.common.script.PythonScriptPool
import com.github.pambrose.common.util.*
import com.github.readingbat.dsl.InvalidConfigurationException
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.dsl.ReturnType
import com.github.readingbat.dsl.ReturnType.*
import com.github.readingbat.misc.BrowserSession
import com.github.readingbat.misc.CheckAnswersJs.challengeSrc
import com.github.readingbat.misc.CheckAnswersJs.groupSrc
import com.github.readingbat.misc.CheckAnswersJs.langSrc
import com.github.readingbat.misc.Constants.CHALLENGE_ROOT
import com.github.readingbat.misc.Constants.LIKE_DESC
import com.github.readingbat.misc.Constants.MSG
import com.github.readingbat.misc.Constants.RESP
import com.github.readingbat.misc.Constants.SCRIPTS_COMPARE_POOL_SIZE
import com.github.readingbat.misc.FormFields.CHALLENGE_ANSWERS_KEY
import com.github.readingbat.misc.FormFields.CHALLENGE_NAME_KEY
import com.github.readingbat.misc.FormFields.GROUP_NAME_KEY
import com.github.readingbat.misc.FormFields.LANGUAGE_NAME_KEY
import com.github.readingbat.misc.KeyConstants.CORRECT_ANSWERS_KEY
import com.github.readingbat.misc.PageUtils.pathOf
import com.github.readingbat.misc.ParameterIds.DISLIKE_CLEAR
import com.github.readingbat.misc.ParameterIds.DISLIKE_COLOR
import com.github.readingbat.misc.ParameterIds.LIKE_CLEAR
import com.github.readingbat.misc.ParameterIds.LIKE_COLOR
import com.github.readingbat.misc.User
import com.github.readingbat.misc.User.Companion.gson
import com.github.readingbat.misc.User.Companion.saveChallengeAnswers
import com.github.readingbat.misc.User.Companion.saveLikeDislike
import com.github.readingbat.server.*
import com.github.readingbat.server.ChallengeName.Companion.getChallengeName
import com.github.readingbat.server.GroupName.Companion.getGroupName
import com.github.readingbat.server.LanguageName.Companion.getLanguageName
import io.ktor.application.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.sessions.*
import mu.KLogging
import redis.clients.jedis.Jedis
import javax.script.ScriptException

internal data class StudentInfo(val studentId: String, val firstName: String, val lastName: String)

internal data class ClassEnrollment(val sessionId: String,
                                    val students: List<StudentInfo> = mutableListOf())

internal data class ChallengeResults(val invocation: Invocation,
                                     val userResponse: String = "",
                                     val answered: Boolean = false,
                                     val correct: Boolean = false,
                                     val hint: String = "")

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

  private val pythonScriptPool: PythonScriptPool
  private val kotlinScriptPool: KotlinScriptPool

  init {
    val poolSize = System.getProperty(SCRIPTS_COMPARE_POOL_SIZE).toInt()
    logger.info { "Creating script pools with size $poolSize" }
    pythonScriptPool = PythonScriptPool(poolSize)
    kotlinScriptPool = KotlinScriptPool(poolSize)
  }

  private fun String.isJavaBoolean() = this == "true" || this == "false"
  private fun String.isPythonBoolean() = this == "True" || this == "False"

  private fun String.equalsAsJvmScalar(that: String,
                                       returnType: ReturnType,
                                       languageName: LanguageName): Pair<Boolean, String> {
    val languageType = languageName.toLanguageType()

    fun deriveHint() =
      when {
        returnType == BooleanType ->
          when {
            isPythonBoolean() -> "$languageType boolean values are either true or false"
            !isJavaBoolean() -> "Answer should be either true or false"
            else -> ""
          }
        returnType == StringType && isNotDoubleQuoted() -> "$languageType strings are double quoted"
        returnType == IntType && isNotInt() -> "Answer should be an int value"
        else -> ""
      }

    return try {
      val result =
        when {
          this.isEmpty() || that.isEmpty() -> false
          returnType == StringType -> this == that
          this.contains(".") || that.contains(".") -> this.toDouble() == that.toDouble()
          this.isJavaBoolean() && that.isJavaBoolean() -> this.toBoolean() == that.toBoolean()
          else -> this.toInt() == that.toInt()
        }
      result to (if (result) "" else deriveHint())
    } catch (e: Exception) {
      false to deriveHint()
    }
  }

  private fun String.equalsAsPythonScalar(correctAnswer: String, returnType: ReturnType): Pair<Boolean, String> {
    fun deriveHint() =
      when {
        returnType == BooleanType ->
          when {
            isJavaBoolean() -> "Python boolean values are either True or False"
            !isPythonBoolean() -> "Answer should be either True or False"
            else -> ""
          }
        returnType == StringType && isNotQuoted() -> "Python strings are either single or double quoted"
        returnType == IntType && isNotInt() -> "Answer should be an int value"
        else -> ""
      }

    return try {
      val result =
        when {
          isEmpty() || correctAnswer.isEmpty() -> false
          isDoubleQuoted() -> this == correctAnswer
          isSingleQuoted() -> singleToDoubleQuoted() == correctAnswer
          contains(".") || correctAnswer.contains(".") -> toDouble() == correctAnswer.toDouble()
          isPythonBoolean() && correctAnswer.isPythonBoolean() -> toBoolean() == correctAnswer.toBoolean()
          else -> toInt() == correctAnswer.toInt()
        }
      result to (if (result) "" else deriveHint())
    } catch (e: Exception) {
      false to deriveHint()
    }
  }

  private suspend fun String.equalsAsJvmList(correctAnswer: String): Pair<Boolean, String> {
    fun deriveHint() = if (isNotBracketed()) "Answer should be bracketed" else ""

    val compareExpr =
      "listOf(${if (isBracketed()) trimEnds() else this}) == listOf(${if (correctAnswer.isBracketed()) correctAnswer.trimEnds() else correctAnswer})"
    logger.debug { "Check answers expression: $compareExpr" }
    val kotlinScript = kotlinScriptPool.borrow()
    return try {
      val result = kotlinScript.eval(compareExpr) as Boolean
      result to (if (result) "" else deriveHint())
    } catch (e: ScriptException) {
      logger.info { "Caught exception comparing $this and $correctAnswer: ${e.message} in $compareExpr" }
      false to deriveHint()
    } catch (e: Exception) {
      false to deriveHint()
    } finally {
      kotlinScriptPool.recycle(kotlinScript)
    }
  }

  private suspend fun String.equalsAsPythonList(correctAnswer: String): Pair<Boolean, String> {
    fun deriveHint() = if (isNotBracketed()) "Answer should be bracketed" else ""
    val compareExpr = "${trim()} == ${correctAnswer.trim()}"
    val pythonScript = pythonScriptPool.borrow()
    return try {
      logger.debug { "Check answers expression: $compareExpr" }
      val result = pythonScript.eval(compareExpr) as Boolean
      result to (if (result) "" else deriveHint())
    } catch (e: ScriptException) {
      logger.info { "Caught exception comparing $this and $correctAnswer: ${e.message} in: $compareExpr" }
      false to deriveHint()
    } catch (e: Exception) {
      false to deriveHint()
    } finally {
      pythonScriptPool.recycle(pythonScript)
    }
  }

  suspend fun PipelineCall.checkAnswers(content: ReadingBatContent, user: User?, redis: Jedis?) {
    val params = call.receiveParameters()
    val paramMap = params.entries().map { it.key to it.value[0] }.toMap()
    val names = ChallengeNames(paramMap)
    val userResponses = params.entries().filter { it.key.startsWith(RESP) }
    val challenge = content.findChallenge(names.languageName, names.groupName, names.challengeName)
    val funcInfo = challenge.funcInfo(content)

    logger.debug("Found ${userResponses.size} user responses in $paramMap")

    val results =
      userResponses.indices
        .map { i ->
          val languageName = names.languageName
          val userResponse = paramMap[RESP + i]?.trim() ?: throw InvalidConfigurationException("Missing user response")
          val correctAnswer = funcInfo.correctAnswers[i]
          val answered = userResponse.isNotBlank()
          val correctAndHint =
            if (answered) {
              logger.debug("""Comparing user response: "$userResponse" with correct answer: "$correctAnswer"""")
              if (languageName.isJvm) {
                if (correctAnswer.isBracketed())
                  userResponse.equalsAsJvmList(correctAnswer)
                else
                  userResponse.equalsAsJvmScalar(correctAnswer, funcInfo.returnType, languageName)
              }
              else {
                if (correctAnswer.isBracketed())
                  userResponse.equalsAsPythonList(correctAnswer)
                else
                  userResponse.equalsAsPythonScalar(correctAnswer, funcInfo.returnType)
              }
            }
            else {
              false to ""
            }

          ChallengeResults(invocation = funcInfo.invocations[i],
                           userResponse = userResponse,
                           answered = answered,
                           correct = correctAndHint.first,
                           hint = correctAndHint.second)
        }


    // Save whether all the answers for the challenge were correct
    if (redis.isNotNull())
      user.saveChallengeAnswers(content, names, paramMap, funcInfo, userResponses, results, redis)

    // Return values: 0 = not answered, 1 = correct, 2 = incorrect
    val answerMapping =
      results
        .map {
          when {
            !it.answered -> listOf(0, "".toDoubleQuoted())
            it.correct -> listOf(1, "".toDoubleQuoted())
            else -> listOf(2, it.hint.toDoubleQuoted())
          }
        }
    logger.debug { "Answers: $answerMapping" }
    call.respondText(answerMapping.toString())
  }

  suspend fun PipelineCall.clearChallengeAnswers(content: ReadingBatContent, user: User?, redis: Jedis): String {
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

    user?.resetHistory(funcInfo, languageName, groupName, challengeName, content.maxHistoryLength, redis)

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
        user.resetHistory(funcInfo, languageName, groupName, challenge.challengeName, content.maxHistoryLength, redis)
      }
    }

    throw RedirectException("$path?$MSG=${"Answers cleared".encode()}")
  }

  suspend fun PipelineCall.likeDislike(content: ReadingBatContent, user: User?, redis: Jedis?) {
    val params = call.receiveParameters()
    val paramMap = params.entries().map { it.key to it.value[0] }.toMap()
    val names = ChallengeNames(paramMap)
    val challenge = content.findChallenge(names.languageName, names.groupName, names.challengeName)

    val likeArg = paramMap[LIKE_DESC]?.trim() ?: throw InvalidConfigurationException("Missing like/dislike argument")

    // Return values: 0 = not answered, 1 = like selected, 2 = dislike selected
    val likeVal =
      when (likeArg) {
        LIKE_CLEAR -> 1
        LIKE_COLOR,
        DISLIKE_COLOR -> 0
        DISLIKE_CLEAR -> 2
        else -> throw InvalidConfigurationException("Invalid like/dislike argument: $likeArg")
      }

    logger.debug { "Like/dislike arg -- response: $likeArg -- $likeVal" }

    if (redis.isNotNull()) {
      val browserSession = call.sessions.get<BrowserSession>()
      user.saveLikeDislike(browserSession, names, likeVal, redis)
    }

    call.respondText(likeVal.toString())
  }
}