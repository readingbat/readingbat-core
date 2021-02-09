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
import com.github.readingbat.dsl.parse.PythonParse
import com.github.readingbat.server.ChallengeName
import com.github.readingbat.server.ScriptPools
import kotlin.time.measureTime

class PythonChallenge(challengeGroup: ChallengeGroup<*>,
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
    val lines = code.lines().filterNot { it.startsWith("#") && it.contains(DESC) }
    val funcCode = PythonParse.extractPythonFunction(lines)
    val invocations = PythonParse.extractPythonInvocations(lines, PythonParse.defMainRegex, PythonParse.ifMainEndRegex)
    val script = PythonParse.convertToPythonScript(lines)
    val correctAnswers = mutableListOf<Any>()

    logger.debug { "$challengeName return type: $returnType script: \n${script.withLineNumbers()}" }

    description = deriveDescription(code, "#")

    measureTime {
      ScriptPools.pythonScriptPool
        .eval {
          add(KotlinParse.varName, correctAnswers)
          eval(script)
        }
    }.also {
      logger.debug { "$challengeName computed answers in $it for: $correctAnswers" }
    }

    return FunctionInfo(this, LanguageType.Python.languageName, code, funcCode, invocations, returnType, correctAnswers)
  }

  override fun toString() =
    "PythonChallenge(packageName='$packageNameAsPath', fileName='$fileName', returnType=$returnType)"
}
