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
import com.github.pambrose.common.util.*
import com.github.readingbat.Constants.langSrc
import com.github.readingbat.Constants.solution
import com.github.readingbat.Constants.userResp
import com.github.readingbat.dsl.InvalidConfigurationException
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.request.receiveParameters
import io.ktor.response.respondText
import io.ktor.util.pipeline.PipelineContext
import kotlinx.coroutines.delay
import mu.KLogging
import kotlin.time.milliseconds

object CheckAnswers : KLogging() {

  internal suspend fun PipelineContext<Unit, ApplicationCall>.checkUserAnswers() {
    val params = call.receiveParameters()
    val compareMap = params.entries().map { it.key to it.value[0] }.toMap()
    val isJava = compareMap[langSrc] == "java"
    val userResps = params.entries().filter { it.key.startsWith(userResp) }
    logger.info("Found ${userResps.size} user responses in $compareMap")
    val results =
      userResps.indices.map { i ->
        val userResp = compareMap[userResp + i]?.trim() ?: throw InvalidConfigurationException("Missing user response")
        val solution = compareMap[solution + i]?.trim() ?: throw InvalidConfigurationException("Missing solution")

        checkWithSolution(isJava, userResp, solution)
      }

    delay(200.milliseconds.toLongMilliseconds())
    call.respondText(results.toString())
  }

  internal infix fun String.equalsAsList(other: String) =
    try {
      KotlinScript().eval("listOf(${this.trimEnds()} == listOf(${other.trimEnds()}") as Boolean
    } catch (e: Exception) {
      logger.info { "Caught exception comparing $this and $other: ${e.message}" }
      false
    }

  private fun checkWithSolution(isJava: Boolean, userResp: String, solution: String) =
    try {
      fun String.isJavaBoolean() = this == "true" || this == "false"
      fun String.isPythonBoolean() = this == "True" || this == "False"

      logger.info("""Comparing solution "$solution" with user response "$userResp"""")

      if (isJava) {
        if (solution.isBracketed()) {
          solution equalsAsList userResp
        }
        else {
          when {
            userResp.isEmpty() || solution.isEmpty() -> false
            userResp.isDoubleQuoted() || solution.isDoubleQuoted() -> userResp == solution
            userResp.contains(".") || solution.contains(".") -> userResp.toDouble() == solution.toDouble()
            userResp.isJavaBoolean() && solution.isJavaBoolean() -> userResp.toBoolean() == solution.toBoolean()
            else -> userResp.toInt() == solution.toInt()
          }
        }
      }
      else
        when {
          userResp.isEmpty() || solution.isEmpty() -> false
          userResp.isDoubleQuoted() -> userResp == solution
          userResp.isSingleQuoted() -> userResp.singleToDoubleQuoted() == solution
          userResp.contains(".") || solution.contains(".") -> userResp.toDouble() == solution.toDouble()
          userResp.isPythonBoolean() && solution.isPythonBoolean() -> {
            userResp.toBoolean() == solution.toBoolean()
          }
          else -> userResp.toInt() == solution.toInt()
        }
    } catch (e: Exception) {
      false
    }
}