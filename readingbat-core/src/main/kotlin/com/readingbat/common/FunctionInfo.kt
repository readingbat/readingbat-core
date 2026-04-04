/*
 * Copyright © 2026 Paul Ambrose (pambrose@mac.com)
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

package com.readingbat.common

import com.pambrose.common.util.asBracketed
import com.pambrose.common.util.isBracketed
import com.pambrose.common.util.isDoubleQuoted
import com.pambrose.common.util.isNotBracketed
import com.pambrose.common.util.isNotDoubleQuoted
import com.pambrose.common.util.isNotFloat
import com.pambrose.common.util.isNotInt
import com.pambrose.common.util.isNotQuoted
import com.pambrose.common.util.isNotSingleQuoted
import com.pambrose.common.util.isSingleQuoted
import com.pambrose.common.util.singleToDoubleQuoted
import com.pambrose.common.util.toDoubleQuoted
import com.pambrose.common.util.toSingleQuoted
import com.readingbat.dsl.LanguageType.Java
import com.readingbat.dsl.LanguageType.Kotlin
import com.readingbat.dsl.LanguageType.Python
import com.readingbat.dsl.ReturnType
import com.readingbat.dsl.ReturnType.BooleanArrayType
import com.readingbat.dsl.ReturnType.BooleanListType
import com.readingbat.dsl.ReturnType.BooleanType
import com.readingbat.dsl.ReturnType.CharType
import com.readingbat.dsl.ReturnType.FloatArrayType
import com.readingbat.dsl.ReturnType.FloatListType
import com.readingbat.dsl.ReturnType.FloatType
import com.readingbat.dsl.ReturnType.IntArrayType
import com.readingbat.dsl.ReturnType.IntListType
import com.readingbat.dsl.ReturnType.IntType
import com.readingbat.dsl.ReturnType.Runtime
import com.readingbat.dsl.ReturnType.StringArrayType
import com.readingbat.dsl.ReturnType.StringListType
import com.readingbat.dsl.ReturnType.StringType
import com.readingbat.dsl.challenge.Challenge
import com.readingbat.posts.ChallengeResults
import com.readingbat.server.ChallengeMd5
import com.readingbat.server.Invocation
import com.readingbat.server.LanguageName
import com.readingbat.server.ScriptPools.pythonEvaluatorPool
import com.readingbat.utils.toCapitalized
import io.github.oshai.kotlinlogging.KotlinLogging
import javax.script.ScriptException

/**
 * Metadata and answer-checking logic for a single challenge function.
 *
 * Encapsulates the parsed challenge source code, its expected invocations and correct answers,
 * and the logic to compare user responses against correct answers. Handles language-specific
 * answer comparison for Java, Kotlin, and Python, including scalar values, arrays, and lists.
 *
 * @property challenge The challenge definition this function belongs to.
 * @property languageName The programming language of this challenge.
 * @property originalCode The unmodified source code of the challenge function.
 * @property codeSnippet The displayable code snippet shown to users.
 * @property invocations The list of function invocations (test cases) for this challenge.
 * @property returnType The expected return type of the challenge function.
 * @param rawAnswers The raw computed answers from evaluating the function against each invocation.
 */
class FunctionInfo(
  val challenge: Challenge,
  val languageName: LanguageName,
  val originalCode: String,
  val codeSnippet: String,
  val invocations: List<Invocation>,
  val returnType: ReturnType,
  rawAnswers: List<*>,
) {
  val challengeName get() = challenge.challengeName
  val groupName get() = challenge.challengeGroup.groupName
  val languageType get() = challenge.challengeGroup.languageType

  /** The number of test case invocations for this challenge. */
  val invocationCount get() = invocations.size

  /** The number of correct answers (should equal [invocationCount]). */
  val questionCount get() = correctAnswers.size

  /** MD5 hash uniquely identifying this challenge by language, group, and name. */
  val challengeMd5 by lazy { ChallengeMd5(languageType.languageName, groupName, challengeName) }

  /**
   * The formatted correct answers for each invocation, computed lazily from raw answers.
   * Formatting is language-specific (e.g., Python capitalizes booleans, Java/Kotlin uses lowercase).
   */
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
        BooleanType -> {
          when (languageType) {
            Python -> raw.toString().toCapitalized()
            Java, Kotlin -> raw.toString()
          }
        }

        IntType, FloatType -> {
          raw.toString()
        }

        StringType -> {
          raw.toString().toDoubleQuoted()
        }

        CharType -> {
          raw.toString().toSingleQuoted()
        }

        BooleanArrayType -> {
          when (languageType) {
            Python -> raw.pythonAdjust(false)
            Java, Kotlin -> (raw as BooleanArray).map { it }.joinToString().asBracketed()
          }
        }

        IntArrayType -> {
          when (languageType) {
            Python -> raw.pythonAdjust(false)
            Java, Kotlin -> (raw as IntArray).map { it }.joinToString().asBracketed()
          }
        }

        FloatArrayType -> {
          when (languageType) {
            Python -> raw.pythonAdjust(false)
            Java, Kotlin -> (raw as FloatArray).map { it }.joinToString().asBracketed()
          }
        }

        StringArrayType -> {
          when (languageType) {
            Python -> raw.pythonAdjust(true)
            Java, Kotlin -> (raw as Array<String>).joinToString { it.toDoubleQuoted() }.asBracketed()
          }
        }

        BooleanListType -> {
          when (languageType) {
            Python -> raw.pythonAdjust(false)
            Java, Kotlin -> (raw as List<Boolean>).toString()
          }
        }

        IntListType -> {
          when (languageType) {
            Python -> raw.pythonAdjust(false)
            Java, Kotlin -> (raw as List<Int>).toString()
          }
        }

        FloatListType -> {
          when (languageType) {
            Python -> raw.pythonAdjust(false)
            Java, Kotlin -> (raw as List<Float>).toString()
          }
        }

        StringListType -> {
          when (languageType) {
            Python -> raw.pythonAdjust(true)
            Java, Kotlin -> "[${(raw as List<String>).joinToString { it.toDoubleQuoted() }}]"
          }
        }

        Runtime -> {
          error("Invalid return type")
        }
      }
    }
  }

  /** Example placeholder text shown in the answer input field, based on the return type and language. */
  val placeHolder by lazy {
    when (returnType) {
      BooleanType -> if (languageType.isPython) "True" else "true"
      IntType -> "0"
      FloatType -> "0.0"
      StringType -> if (languageType.isPython) "''" else """"""""
      CharType -> "' '"
      BooleanListType, BooleanArrayType -> if (languageType.isPython) "[True, False]" else "[true, false]"
      IntListType, IntArrayType -> "[0, 1]"
      FloatListType, FloatArrayType -> "[0.0, 1.0]"
      StringListType, StringArrayType -> if (languageType.isPython) "['', '']" else """["", ""]"""
      Runtime -> error("Invalid return type")
    }
  }

  init {
    logger.debug {
      "In $challengeName return type: $returnType invocations: $invocations computed answers: $correctAnswers"
    }
    validate()
  }

  private fun validate() {
    if (questionCount != invocationCount)
      error("Mismatch between $questionCount answers and $invocationCount invocations in $challengeName")
  }

  /**
   * Checks a user's response against the correct answer for the invocation at the given [index].
   *
   * Returns a [ChallengeResults] containing whether the answer was correct and an optional hint
   * (e.g., "Answer should be bracketed", "Python booleans are either True or False").
   */
  suspend fun checkResponse(index: Int, userResponse: String): ChallengeResults {
    val correctAnswer = correctAnswers[index]
    val answered = userResponse.isNotBlank()
    val correctAndHint =
      if (answered) {
        logger.debug { """Comparing user response: "$userResponse" with correct answer: "$correctAnswer"""" }
        if (languageName.isJvm) {
          if (correctAnswer.isBracketed())
            userResponse.equalsAsJvmList(correctAnswer)
          else
            userResponse.equalsAsJvmScalar(correctAnswer, returnType, languageName)
        } else {
          if (correctAnswer.isBracketed())
            userResponse.equalsAsPythonList(correctAnswer)
          else
            userResponse.equalsAsPythonScalar(correctAnswer, returnType)
        }
      } else {
        false to ""
      }

    return ChallengeResults(
      invocation = invocations[index],
      userResponse = userResponse,
      answered = answered,
      correct = correctAndHint.first,
      hint = correctAndHint.second,
    )
  }

  companion object {
    private val logger = KotlinLogging.logger {}

    private fun String.isJavaBoolean() = this == "true" || this == "false"

    private fun String.isPythonBoolean() = this == "True" || this == "False"

    private fun String.equalsAsJvmList(correctAnswer: String): Pair<Boolean, String> {
      fun deriveHint() = if (isNotBracketed()) "Answer should be bracketed" else ""
      val lho = if (isBracketed()) removeSurrounding("[", "]") else this
      val rho = if (correctAnswer.isBracketed()) correctAnswer.removeSurrounding("[", "]") else correctAnswer

      // Compare parsed list elements directly instead of using the Kotlin script engine,
      // which has a psi2ir bug that corrupts engine pool state on repeated evaluations
      val lhElements = parseListElements(lho)
      val rhElements = parseListElements(rho)
      val result = lhElements == rhElements
      logger.debug { "Check answers list comparison: $lhElements == $rhElements -> $result" }
      return result to (if (result) "" else deriveHint())
    }

    private fun parseListElements(csv: String): List<String> {
      if (csv.isBlank()) return emptyList()
      return csv.split(",").map { it.trim() }
    }

    private suspend fun String.equalsAsPythonList(correctAnswer: String): Pair<Boolean, String> {
      fun deriveHint() = if (isNotBracketed()) "Answer should be bracketed" else ""
      val compareExpr = "${trim()} == ${correctAnswer.trim()}"
      return runCatching {
        logger.debug { "Check answers expression: $compareExpr" }
        val result = pythonEvaluatorPool.eval(compareExpr) as Boolean
        result to (if (result) "" else deriveHint())
      }.getOrElse { e ->
        when (e) {
          is ScriptException -> {
            logger.info { "Caught exception comparing $this and $correctAnswer: ${e.message} in: $compareExpr" }
            false to deriveHint()
          }

          else -> {
            false to deriveHint()
          }
        }
      }
    }

    private fun String.equalsAsJvmScalar(
      that: String,
      returnType: ReturnType,
      languageName: LanguageName,
    ): Pair<Boolean, String> {
      val languageType = languageName.toLanguageType()

      fun deriveHint() =
        when (returnType) {
          BooleanType -> {
            "$languageType booleans are either true or false"
          }

          StringType if isNotDoubleQuoted() -> {
            "$languageType strings are double quoted"
          }

          CharType if isNotSingleQuoted() -> {
            "$languageType chars are single quoted"
          }

          IntType if isNotInt() -> {
            "Answer should be an int value"
          }

          FloatType if isNotFloat() -> {
            "Answer should be a float value"
          }

          else -> {
            ""
          }
        }

      return runCatching {
        val result =
          when {
            this.isEmpty() || that.isEmpty() -> false
            returnType == StringType -> this == that
            returnType == CharType -> this == that
            this.contains(".") || that.contains(".") -> this.toDouble() == that.toDouble()
            this.isJavaBoolean() && that.isJavaBoolean() -> this.toBoolean() == that.toBoolean()
            else -> this.toInt() == that.toInt()
          }
        result to (if (result) "" else deriveHint())
      }.getOrElse { _ ->
        false to deriveHint()
      }
    }

    private fun String.equalsAsPythonScalar(correctAnswer: String, returnType: ReturnType): Pair<Boolean, String> {
      fun deriveHint() =
        when (returnType) {
          BooleanType -> {
            "Python boolean values are either True or False"
          }

          StringType if isNotQuoted() -> {
            "Python strings are either single or double quoted"
          }

          IntType if isNotInt() -> {
            "Answer should be an int value"
          }

          FloatType if isNotFloat() -> {
            "Answer should be a float value"
          }

          // CharType does not have a Python equivalent
          else -> {
            ""
          }
        }

      return runCatching {
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
      }.getOrElse { _ ->
        false to deriveHint()
      }
    }
  }
}
