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

package com.github.readingbat.common

import com.github.pambrose.common.util.*
import com.github.readingbat.dsl.Challenge
import com.github.readingbat.dsl.LanguageType.Java
import com.github.readingbat.dsl.LanguageType.Kotlin
import com.github.readingbat.dsl.LanguageType.Python
import com.github.readingbat.dsl.ReturnType
import com.github.readingbat.dsl.ReturnType.*
import com.github.readingbat.posts.ChallengeResults
import com.github.readingbat.server.ChallengeMd5
import com.github.readingbat.server.Invocation
import com.github.readingbat.server.LanguageName
import com.github.readingbat.server.ScriptPools.kotlinScriptPool
import com.github.readingbat.server.ScriptPools.pythonScriptPool
import mu.KLogging
import javax.script.ScriptException

/*internal*/ class FunctionInfo(val challenge: Challenge,
                                val languageName: LanguageName,
                                val originalCode: String,
                                val codeSnippet: String,
                                val invocations: List<Invocation>,
                                val returnType: ReturnType,
                                rawAnswers: List<*>) {
  val challengeName get() = challenge.challengeName
  val groupName get() = challenge.challengeGroup.groupName
  val languageType get() = challenge.challengeGroup.languageType
  val invocationCount get() = invocations.size
  val questionCount get() = correctAnswers.size
  val challengeMd5 by lazy { ChallengeMd5(languageType.languageName, groupName, challengeName) }

  @Suppress("UNCHECKED_CAST")
  val correctAnswers by lazy {
    List(rawAnswers.size) { i ->
      val raw = rawAnswers[i]

      fun Any?.pythonAdjust(quoteIt: Boolean) =
        toString().trim()
          .removeSurrounding("[", "]").trim()
          .let { if (it.isEmpty()) emptyList() else it.split(",") }
          .map { it.trim().removeSurrounding("'") }
          .joinToString { if (quoteIt) it.toDoubleQuoted() else it }
          .asBracketed()

      when (returnType) {
        BooleanType ->
          when (languageType) {
            Python -> raw.toString().capitalize()
            Java, Kotlin -> raw.toString()
          }

        IntType, FloatType -> raw.toString()

        StringType -> raw.toString().toDoubleQuoted()

        BooleanArrayType ->
          when (languageType) {
            Python -> raw.pythonAdjust(false)
            Java, Kotlin -> (raw as BooleanArray).map { it }.joinToString().asBracketed()
          }

        IntArrayType ->
          when (languageType) {
            Python -> raw.pythonAdjust(false)
            Java, Kotlin -> (raw as IntArray).map { it }.joinToString().asBracketed()
          }

        FloatArrayType ->
          when (languageType) {
            Python -> raw.pythonAdjust(false)
            Java, Kotlin -> (raw as FloatArray).map { it }.joinToString().asBracketed()
          }

        StringArrayType ->
          when (languageType) {
            Python -> raw.pythonAdjust(true)
            Java, Kotlin -> (raw as Array<String>).joinToString { it.toDoubleQuoted() }.asBracketed()
          }

        BooleanListType ->
          when (languageType) {
            Python -> raw.pythonAdjust(false)
            Java, Kotlin -> (raw as List<Boolean>).toString()
          }

        IntListType ->
          when (languageType) {
            Python -> raw.pythonAdjust(false)
            Java, Kotlin -> (raw as List<Int>).toString()
          }

        FloatListType ->
          when (languageType) {
            Python -> raw.pythonAdjust(false)
            Java, Kotlin -> (raw as List<Float>).toString()
          }

        StringListType ->
          when (languageType) {
            Python -> raw.pythonAdjust(true)
            Java, Kotlin -> "[${(raw as List<String>).joinToString { it.toDoubleQuoted() }}]"
          }

        Runtime -> error("Invalid return type")
      }
    }
  }

  val placeHolder by lazy {
    when (returnType) {
      BooleanType -> if (languageType.isPython) "True" else "true"
      IntType -> "0"
      FloatType -> "0.0"
      StringType -> if (languageType.isPython) "''" else """"""""
      BooleanListType, BooleanArrayType -> if (languageType.isPython) "[True, False]" else "[true, false]"
      IntListType, IntArrayType -> "[0, 1]"
      FloatListType, FloatArrayType -> "[0.0, 1.0]"
      StringListType, StringArrayType -> if (languageType.isPython) "['', '']" else """["", ""]"""
      Runtime -> error("Invalid return type")
    }
  }

  init {
    logger.debug { "In $challengeName return type: $returnType invocations: $invocations computed answers: $correctAnswers" }
    validate()
  }

  private fun validate() {
    if (questionCount != invocationCount)
      error("Mismatch between $questionCount answers and $invocationCount invocations in $challengeName")
  }

  /*internal*/ suspend fun checkResponse(index: Int, userResponse: String): ChallengeResults {
    val correctAnswer = correctAnswers[index]
    val answered = userResponse.isNotBlank()
    val correctAndHint =
      if (answered) {
        logger.debug("""Comparing user response: "$userResponse" with correct answer: "$correctAnswer"""")
        if (languageName.isJvm) {
          if (correctAnswer.isBracketed())
            userResponse.equalsAsJvmList(correctAnswer)
          else
            userResponse.equalsAsJvmScalar(correctAnswer, returnType, languageName)
        }
        else {
          if (correctAnswer.isBracketed())
            userResponse.equalsAsPythonList(correctAnswer)
          else
            userResponse.equalsAsPythonScalar(correctAnswer, returnType)
        }
      }
      else {
        false to ""
      }

    return ChallengeResults(invocation = invocations[index],
                            userResponse = userResponse,
                            answered = answered,
                            correct = correctAndHint.first,
                            hint = correctAndHint.second)
  }

  companion object : KLogging() {
    private fun String.isJavaBoolean() = this == "true" || this == "false"

    private fun String.isPythonBoolean() = this == "True" || this == "False"

    private suspend fun String.equalsAsJvmList(correctAnswer: String): Pair<Boolean, String> {
      fun deriveHint() = if (isNotBracketed()) "Answer should be bracketed" else ""
      val lho = if (isBracketed()) removeSurrounding("[", "]") else this
      val rho = if (correctAnswer.isBracketed()) correctAnswer.removeSurrounding("[", "]") else correctAnswer
      // Use <String> here because a type is required. It doesn't matter which type is used.
      val lhs = if (lho.isBlank()) "emptyList<String>()" else "listOf($lho)"
      val rhs = if (rho.isBlank()) "emptyList<String>()" else "listOf($rho)"
      val compareExpr = "$lhs == $rhs"
      logger.debug { "Check answers expression: $compareExpr" }
      return try {
        val result = kotlinScriptPool.eval { eval(compareExpr) } as Boolean
        result to (if (result) "" else deriveHint())
      } catch (e: ScriptException) {
        logger.info { "Caught exception comparing $this and $correctAnswer: ${e.message} in $compareExpr" }
        false to deriveHint()
      } catch (e: Exception) {
        false to deriveHint()
      }
    }

    private suspend fun String.equalsAsPythonList(correctAnswer: String): Pair<Boolean, String> {
      fun deriveHint() = if (isNotBracketed()) "Answer should be bracketed" else ""
      val compareExpr = "${trim()} == ${correctAnswer.trim()}"
      return try {
        logger.debug { "Check answers expression: $compareExpr" }
        val result = pythonScriptPool.eval { eval(compareExpr) } as Boolean
        result to (if (result) "" else deriveHint())
      } catch (e: ScriptException) {
        logger.info { "Caught exception comparing $this and $correctAnswer: ${e.message} in: $compareExpr" }
        false to deriveHint()
      } catch (e: Exception) {
        false to deriveHint()
      }
    }

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
          returnType == FloatType && isNotFloat() -> "Answer should be a float value"
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
          returnType == FloatType && isNotFloat() -> "Answer should be a float value"
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
  }
}