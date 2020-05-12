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

package com.github.readingbat.misc

import com.github.pambrose.common.script.KotlinScript
import com.github.pambrose.common.script.PythonScript
import com.github.pambrose.common.util.*
import com.github.readingbat.InvalidConfigurationException
import com.github.readingbat.RedisPool.gson
import com.github.readingbat.RedisPool.pool
import com.github.readingbat.config.ChallengeAnswers
import com.github.readingbat.config.ClientSession
import com.github.readingbat.dsl.LanguageType.Companion.toLanguageType
import com.github.readingbat.dsl.LanguageType.Java
import com.github.readingbat.dsl.LanguageType.Kotlin
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.Constants.challengeSrc
import com.github.readingbat.misc.Constants.groupSrc
import com.github.readingbat.misc.Constants.langSrc
import com.github.readingbat.misc.Constants.userResp
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.request.receiveParameters
import io.ktor.response.respondText
import io.ktor.util.pipeline.PipelineContext
import kotlinx.coroutines.delay
import mu.KLogging
import javax.script.ScriptException
import kotlin.time.milliseconds

object CheckAnswers : KLogging() {

  internal suspend fun PipelineContext<Unit, ApplicationCall>.checkUserAnswers(readingBatContent: ReadingBatContent,
                                                                               clientSession: ClientSession?) {
    val params = call.receiveParameters()
    val compareMap = params.entries().map { it.key to it.value[0] }.toMap()
    val languageName = compareMap[langSrc] ?: throw InvalidConfigurationException("Missing language")
    val groupName = compareMap[groupSrc] ?: throw InvalidConfigurationException("Missing group name")
    val challengeName = compareMap[challengeSrc] ?: throw InvalidConfigurationException("Missing challenge name")
    val isJvm = languageName in listOf(Java.lowerName, Kotlin.lowerName)
    val userResps = params.entries().filter { it.key.startsWith(userResp) }
    val challenge =
      readingBatContent.findLanguage(languageName.toLanguageType()).findChallenge(groupName, challengeName)

    logger.debug("Found ${userResps.size} user responses in $compareMap")

    val results =
      userResps.indices.map { i ->
        val userResp = compareMap[userResp + i]?.trim() ?: throw InvalidConfigurationException("Missing user response")
        val funcInfo = challenge.funcInfo()
        val answer = funcInfo.answers[i]
        Triple(funcInfo.arguments[i], userResp.isNotEmpty(), checkWithAnswer(isJvm, userResp, answer))
      }

    if (clientSession != null) {
      val answerMap = mutableMapOf<String, String>()
      userResps.indices.forEach { i ->
        val argumentKey = challenge.funcInfo().arguments[i]
        val userResp = compareMap[userResp + i]?.trim() ?: throw InvalidConfigurationException("Missing user response")
        if (userResp.isNotEmpty())
          answerMap[argumentKey] = userResp
      }

      pool.resource
        .use { redis ->
          val key = clientSession.redisKey(languageName, groupName, challengeName)
          val challengeAnswers = ChallengeAnswers(clientSession.id, answerMap)
          val json = gson.toJson(challengeAnswers)
          logger.debug { "Assigning: $key to $json" }
          redis.set(key, json)
        }
    }

    results
      .filter { it.second }
      .forEach {
        logger.info { "Item with args; ${it.first} was correct: ${it.third}" }
      }

    delay(200.milliseconds.toLongMilliseconds())
    call.respondText(results.map { it.third }.toString())
  }

  private infix fun String.equalsAsKotlinList(other: String): Boolean {
    val compareExpr = "listOf(${this.trimEnds()}) == listOf(${other.trimEnds()})"
    logger.debug { "Check answers expression: $compareExpr" }
    return try {
      KotlinScript().eval(compareExpr) as Boolean
    } catch (e: ScriptException) {
      logger.info { "Caught exception comparing $this and $other: ${e.message} in $compareExpr" }
      false
    }
  }

  private infix fun String.equalsAsPythonList(other: String): Boolean {
    val compareExpr = "${this@equalsAsPythonList.trim()} == ${other.trim()}"
    logger.debug { "Check answers expression: $compareExpr" }
    return try {
      PythonScript().eval(compareExpr) as Boolean
    } catch (e: ScriptException) {
      logger.info { "Caught exception comparing $this and $other: ${e.message} in: $compareExpr" }
      false
    }
  }

  private fun checkWithAnswer(isJvm: Boolean, userResp: String, answer: String) =
    try {
      fun String.isJavaBoolean() = this == "true" || this == "false"
      fun String.isPythonBoolean() = this == "True" || this == "False"

      logger.debug("""Comparing user response: "$userResp" with answer: "$answer"""")

      if (isJvm) {
        if (answer.isBracketed())
          answer equalsAsKotlinList userResp
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
          answer equalsAsPythonList userResp
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
}