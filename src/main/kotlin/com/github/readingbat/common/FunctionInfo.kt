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

import com.github.pambrose.common.util.asBracketed
import com.github.pambrose.common.util.toDoubleQuoted
import com.github.readingbat.dsl.Challenge
import com.github.readingbat.dsl.InvalidConfigurationException
import com.github.readingbat.dsl.LanguageType.Java
import com.github.readingbat.dsl.LanguageType.Kotlin
import com.github.readingbat.dsl.LanguageType.Python
import com.github.readingbat.dsl.ReturnType
import com.github.readingbat.dsl.ReturnType.*
import com.github.readingbat.server.ChallengeMd5
import com.github.readingbat.server.Invocation
import mu.KLogging

internal class FunctionInfo(val challenge: Challenge,
                            val originalCode: String,
                            val codeSnippet: String,
                            val invocations: List<Invocation>,
                            val returnType: ReturnType,
                            rawAnswers: List<*>) {
  val challengeName get() = challenge.challengeName
  val groupName get() = challenge.challengeGroup.groupName
  val languageType get() = challenge.challengeGroup.languageType

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
            Java,
            Kotlin -> raw.toString()
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
            Python -> raw.pythonAdjust(false)
            Java, Kotlin -> "[${(raw as List<String>).joinToString { it.toDoubleQuoted() }}]"
          }

        Runtime -> throw InvalidConfigurationException("Invalid return type")
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
      Runtime -> throw InvalidConfigurationException("Invalid return type")
    }
  }

  init {
    logger.debug { "In $challengeName return type: $returnType invocations: $invocations computed answers: $correctAnswers" }
    validate()
  }

  private fun validate() {
    if (correctAnswers.size != invocations.size)
      throw InvalidConfigurationException("Mismatch between ${correctAnswers.size} answers and ${invocations.size} invocations in $challengeName")
  }

  companion object : KLogging()
}