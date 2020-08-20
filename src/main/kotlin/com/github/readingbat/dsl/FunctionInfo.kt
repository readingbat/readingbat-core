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

package com.github.readingbat.dsl

import com.github.pambrose.common.util.asBracketed
import com.github.pambrose.common.util.toDoubleQuoted
import com.github.readingbat.dsl.ReturnType.*
import com.github.readingbat.server.ChallengeMd5
import com.github.readingbat.server.ChallengeName
import com.github.readingbat.server.Invocation
import mu.KLogging

internal class FunctionInfo(val languageType: LanguageType,
                            private val challengeGroup: ChallengeGroup<*>,
                            val challengeName: ChallengeName,
                            val originalCode: String,
                            val codeSnippet: String,
                            val invocations: List<Invocation>,
                            val returnType: ReturnType,
                            rawAnswers: List<*>) {

  val correctAnswers = mutableListOf<String>()

  init {
    rawAnswers.forEach { raw ->
      correctAnswers +=
        when (returnType) {
          BooleanType -> {
            if (languageType.isPython())
              raw.toString().capitalize()
            else
              raw.toString()
          }
          IntType -> raw.toString()
          StringType -> raw.toString().toDoubleQuoted()
          BooleanArrayType -> (raw as BooleanArray).map { it }.joinToString().asBracketed()
          IntArrayType -> (raw as IntArray).map { it }.joinToString().asBracketed()
          StringArrayType -> (raw as Array<String>).joinToString { it.toDoubleQuoted() }.asBracketed()
          BooleanListType -> (raw as List<Boolean>).toString()
          IntListType -> (raw as List<Int>).toString()
          StringListType -> "[${(raw as List<String>).joinToString { it.toDoubleQuoted() }}]"
          Runtime -> throw InvalidConfigurationException("Invalid return type")
        }
    }

    logger.info { "In $challengeName return type: $returnType invocations: $invocations computed answers: $correctAnswers" }

    validate()
  }

  val challengeMd5: ChallengeMd5 by lazy {
    ChallengeMd5(languageType.languageName, challengeGroup.groupName, challengeName)
  }

  fun placeHolder(): String {
    return when (returnType) {
      BooleanType -> if (languageType.isPython()) "True" else "true"
      IntType -> "0"
      StringType -> if (languageType.isPython()) "''" else """"""""
      BooleanListType, BooleanArrayType -> if (languageType.isPython()) "[True, False]" else "[true, false]"
      IntListType, IntArrayType -> "[0, 1]"
      StringListType, StringArrayType -> if (languageType.isPython()) "['', '']" else """["", ""]"""
      Runtime -> throw InvalidConfigurationException("Invalid return type")
    }
  }

  private fun validate() {
    if (correctAnswers.size != invocations.size)
      throw InvalidConfigurationException("Mismatch between ${correctAnswers.size} answers and ${invocations.size} invocations in $challengeName")
  }

  companion object : KLogging()
}