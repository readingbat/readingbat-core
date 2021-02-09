/*
 * Copyright © 2021 Paul Ambrose (pambrose@mac.com)
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

package com.github.readingbat.dsl.challenge

import com.github.pambrose.common.util.withLineNumbers
import com.github.readingbat.common.FunctionInfo
import com.github.readingbat.dsl.ChallengeGroup
import com.github.readingbat.dsl.LanguageType
import com.github.readingbat.dsl.ReturnType
import com.github.readingbat.dsl.parse.KotlinParse
import com.github.readingbat.server.ChallengeName
import com.github.readingbat.server.ScriptPools
import kotlin.reflect.typeOf
import kotlin.time.measureTime

class KotlinChallenge(challengeGroup: ChallengeGroup<*>,
                      challengeName: ChallengeName,
                      replaceable: Boolean) :
  Challenge(challengeGroup, challengeName, replaceable) {

  // User properties
  lateinit var returnType: ReturnType

  override fun validate() {
    super.validate()

    if (!this::returnType.isInitialized)
      error("$challengeName missing returnType value")
  }

  override suspend fun computeFunctionInfo(code: String): FunctionInfo {
    val lines =
      code.lines()
        .filterNot { it.startsWith("//") && it.contains(DESC) }
        .filterNot { it.trimStart().startsWith("package") }
    val strippedCode = lines.joinToString("\n")
    val funcCode = "\n${KotlinParse.extractKotlinFunction(lines)}\n\n"
    val invocations = KotlinParse.extractKotlinInvocations(lines, KotlinParse.funMainRegex, KotlinParse.kotlinEndRegex)
    val script = KotlinParse.convertToKotlinScript(lines).also { logger.debug { "Kotlin: $it" } }
    val correctAnswers = mutableListOf<Any>()

    logger.debug { "$challengeName return type: $returnType script: \n${script.withLineNumbers()}" }

    description = deriveDescription(code, "//")

    measureTime {
      ScriptPools.kotlinScriptPool
        .eval {
          add(KotlinParse.varName, correctAnswers, typeOf<Any>())
          eval(script)
        }
    }.also {
      logger.debug { "$challengeName computed answers in $it for: $correctAnswers" }
    }

    return FunctionInfo(this,
                        LanguageType.Kotlin.languageName,
                        strippedCode,
                        funcCode,
                        invocations,
                        returnType,
                        correctAnswers)
  }

  override fun toString() =
    "KotlinChallenge(packageName='$packageNameAsPath', fileName='$fileName', returnType=$returnType)"
}