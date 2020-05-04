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
import com.github.readingbat.InvalidConfigurationException
import com.github.readingbat.dsl.ReturnType.*
import mu.KLogging

class FunctionInfo(val languageType: LanguageType,
                   val name: String,
                   val originalCode: String,
                   val codeSnippet: String,
                   val arguments: List<String>,
                   returnType: ReturnType,
                   rawAnswers: List<*>) {

  val answers = mutableListOf<String>()

  init {
    rawAnswers.forEach { raw ->
      answers +=
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
        }
    }

    logger.debug { "In $name arguments: $arguments computed answers: $answers" }

    validate()
  }

  private fun validate() {
    if (answers.size != arguments.size)
      throw InvalidConfigurationException("Mismatch between ${answers.size} answers and ${arguments.size} arguments in $name")
  }

  companion object : KLogging()
}