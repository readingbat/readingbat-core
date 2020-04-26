package com.github.readingbat

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
import kotlin.time.milliseconds

suspend fun PipelineContext<Unit, ApplicationCall>.checkAnswers() {
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

fun checkWithSolution(isJava: Boolean, userResp: String?, solution: String?) =
  try {
    fun String.isJavaBoolean() = this == "true" || this == "false"
    fun String.isPythonBoolean() = this == "True" || this == "False"

    //println("[$userResp] and [$solution]")

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
