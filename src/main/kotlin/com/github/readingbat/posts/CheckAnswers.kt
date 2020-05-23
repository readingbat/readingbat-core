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

import com.github.pambrose.common.redis.RedisUtils.withRedisPool
import com.github.pambrose.common.script.KotlinScript
import com.github.pambrose.common.script.PythonScript
import com.github.pambrose.common.util.*
import com.github.readingbat.dsl.InvalidConfigurationException
import com.github.readingbat.dsl.LanguageType.Companion.toLanguageType
import com.github.readingbat.dsl.LanguageType.Java
import com.github.readingbat.dsl.LanguageType.Kotlin
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.Answers.challengeSrc
import com.github.readingbat.misc.Answers.groupSrc
import com.github.readingbat.misc.Answers.langSrc
import com.github.readingbat.misc.BrowserSession
import com.github.readingbat.misc.CSSNames.userResp
import com.github.readingbat.misc.UserId
import com.github.readingbat.misc.UserPrincipal
import com.github.readingbat.misc.userIdKey
import com.github.readingbat.server.PipelineCall
import com.google.gson.Gson
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

private data class ChallengeResults(val arguments: String,
                                    val userResponse: String,
                                    val answered: Boolean,
                                    val correct: Boolean)

private data class ChallengeHistory(var argument: String,
                                    var correct: Boolean = false,
                                    var attempts: Int = 0,
                                    val answers: MutableList<String> = mutableListOf()) {
  fun markCorrect() {
    correct = true
  }

  fun markIncorrect(userResp: String) {
    correct = false
    if (userResp.isNotEmpty() && userResp !in answers) {
      attempts++
      answers += userResp
    }
  }
}

object CheckAnswers : KLogging() {

  private val gson = Gson()

  private fun String.isJavaBoolean() = this == "true" || this == "false"
  private fun String.isPythonBoolean() = this == "True" || this == "False"

  internal suspend fun PipelineCall.checkUserAnswers(content: ReadingBatContent, principal: UserPrincipal?) {
    val params = call.receiveParameters()
    val compareMap = params.entries().map { it.key to it.value[0] }.toMap()
    val languageName = compareMap[langSrc] ?: throw InvalidConfigurationException("Missing language")
    val groupName = compareMap[groupSrc] ?: throw InvalidConfigurationException("Missing group name")
    val challengeName = compareMap[challengeSrc] ?: throw InvalidConfigurationException("Missing challenge name")
    val isJvm = languageName in listOf(Java.lowerName, Kotlin.lowerName)
    val userResps = params.entries().filter { it.key.startsWith(userResp) }
    val challenge = content.findLanguage(languageName.toLanguageType()).findChallenge(groupName, challengeName)
    val funcInfo = challenge.funcInfo(content)
    val kotlinScriptEngine by lazy { KotlinScript() }
    val pythonScriptEngine by lazy { PythonScript() }
    val browserSession by lazy { call.sessions.get<BrowserSession>() }

    fun checkWithAnswer(isJvm: Boolean, userResp: String, answer: String) =
      try {
        logger.debug("""Comparing user response: "$userResp" with answer: "$answer"""")
        if (isJvm) {
          if (answer.isBracketed())
            answer.equalsAsKotlinList(userResp, kotlinScriptEngine)
          else
            when {
              userResp.isEmpty() || answer.isEmpty() -> false
              userResp.isDoubleQuoted() || answer.isDoubleQuoted() -> userResp == answer
              userResp.contains(".") || answer.contains(".") -> userResp.toDouble() == answer.toDouble()
              userResp.isJavaBoolean() && answer.isJavaBoolean() -> userResp.toBoolean() == answer.toBoolean()
              else -> userResp.toInt() == answer.toInt()
            }
        }
        else
          if (answer.isBracketed())
            answer.equalsAsPythonList(userResp, pythonScriptEngine)
          else
            when {
              userResp.isEmpty() || answer.isEmpty() -> false
              userResp.isDoubleQuoted() -> userResp == answer
              userResp.isSingleQuoted() -> userResp.singleToDoubleQuoted() == answer
              userResp.contains(".") || answer.contains(".") -> userResp.toDouble() == answer.toDouble()
              userResp.isPythonBoolean() && answer.isPythonBoolean() -> userResp.toBoolean() == answer.toBoolean()
              else -> userResp.toInt() == answer.toInt()
            }
      } catch (e: Exception) {
        false
      }

    logger.debug("Found ${userResps.size} user responses in $compareMap")

    val results =
      userResps.indices.map { i ->
        val userResponse =
          compareMap[userResp + i]?.trim() ?: throw InvalidConfigurationException("Missing user response")
        val answer = funcInfo.answers[i]
        val answered = userResponse.isNotEmpty()
        ChallengeResults(arguments = funcInfo.arguments[i],
                         userResponse = userResponse,
                         answered = answered,
                         correct = if (answered) checkWithAnswer(isJvm,
                                                                 userResponse,
                                                                 answer)
                         else false)
      }

    val answerMap = mutableMapOf<String, String>()
    userResps.indices.forEach { i ->
      val argumentKey = funcInfo.arguments[i]
      val userResp = compareMap[userResp + i]?.trim() ?: throw InvalidConfigurationException("Missing user response")
      if (userResp.isNotEmpty())
        answerMap[argumentKey] = userResp
    }

    // Save whether all the answers for the challenge were correct
    withRedisPool { redis ->
      val userId = lookupUserId(redis, principal)

      // Save if all answers were correct
      val correctAnswersKey =
        userId?.correctAnswersKey(languageName, groupName, challengeName)
          ?: browserSession?.correctAnswersKey(languageName, groupName, challengeName)
          ?: ""

      if (correctAnswersKey.isNotEmpty()) {
        val allCorrect = results.all { it.correct }
        redis?.set(correctAnswersKey, allCorrect.toString())
      }

      val challengeKey =
        userId?.challengeKey(languageName, groupName, challengeName)
          ?: browserSession?.challengeKey(languageName, groupName, challengeName)
          ?: ""

      if (redis != null && challengeKey.isNotEmpty()) {
        logger.debug { "Storing: $challengeKey" }
        answerMap.forEach { (args, userResp) ->
          redis.hset(challengeKey, args, userResp)
          redis.publish("channel", userResp)
        }
      }

      // Save the history of each answer on a per-arguments basis
      results
        .filter { it.answered }
        .forEach { result ->
          val argumentKey =
            userId?.argumentKey(languageName, groupName, challengeName, result.arguments)
              ?: browserSession?.argumentKey(languageName, groupName, challengeName, result.arguments)
              ?: ""

          if (redis != null && argumentKey.isNotEmpty()) {
            val history =
              gson.fromJson(redis[argumentKey], ChallengeHistory::class.java) ?: ChallengeHistory(
                result.arguments)
            logger.debug { "Before: $history" }
            history.apply { if (result.correct) markCorrect() else markIncorrect(result.userResponse) }
            logger.debug { "After: $history" }
            redis.set(argumentKey, gson.toJson(history))
          }
        }
    }

    results
      .filter { it.answered }
      .forEach {
        logger.debug { "Item with args; ${it.arguments} was correct: ${it.correct}" }
      }

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

internal fun lookupUserId(redis: Jedis?, principal: UserPrincipal?) =
  if (principal != null) {
    val userIdKey = userIdKey(principal.userId)
    val id = redis?.get(userIdKey) ?: ""
    if (id.isNotEmpty()) UserId(id) else null
  }
  else {
    null
  }