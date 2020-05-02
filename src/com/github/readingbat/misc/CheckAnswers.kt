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

import com.github.pambrose.common.util.isDoubleQuoted
import com.github.pambrose.common.util.isSingleQuoted
import com.github.pambrose.common.util.singleToDoubleQuoted
import com.github.readingbat.Constants.answer
import com.github.readingbat.Constants.langSrc
import com.github.readingbat.Constants.solution
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
    val answers = params.entries().filter { it.key.startsWith(answer) }
    val results =
      answers.indices.map { i ->
        val userResp = compareMap[answer + i]?.trim()
        val sol = compareMap[solution + i]?.trim()
        checkWithSolution(compareMap[langSrc] == "java", userResp, sol)
      }

    delay(200.milliseconds.toLongMilliseconds())
    call.respondText(results.toString())
  }

  private fun checkWithSolution(isJava: Boolean, userResp: String?, solution: String?) =
    try {
      fun String.isJavaBoolean() = this == "true" || this == "false"
      fun String.isPythonBoolean() = this == "True" || this == "False"

      logger.info("Comparing solution [$solution] with user response [$userResp]")

      if (isJava)
        when {
          userResp.isNullOrEmpty() || solution.isNullOrEmpty() -> false
          userResp.isDoubleQuoted() || solution.isDoubleQuoted() -> userResp == solution
          userResp.contains(".") || solution.contains(".") -> userResp.toDouble() == solution.toDouble()
          userResp.isJavaBoolean() && solution.isJavaBoolean() -> userResp.toBoolean() == solution.toBoolean()
          else -> userResp.toInt() == solution.toInt()
        }
      else
        when {
          userResp.isNullOrEmpty() || solution.isNullOrEmpty() -> false
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