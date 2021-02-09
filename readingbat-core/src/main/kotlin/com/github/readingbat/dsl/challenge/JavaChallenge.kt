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

import ch.obermuhlner.scriptengine.java.Isolation
import com.github.pambrose.common.util.withLineNumbers
import com.github.readingbat.common.FunctionInfo
import com.github.readingbat.dsl.ChallengeGroup
import com.github.readingbat.dsl.LanguageType
import com.github.readingbat.dsl.parse.JavaParse
import com.github.readingbat.server.ChallengeName
import com.github.readingbat.server.ScriptPools
import kotlin.time.measureTimedValue

class JavaChallenge(challengeGroup: ChallengeGroup<*>,
                    challengeName: ChallengeName,
                    replaceable: Boolean) :
  Challenge(challengeGroup, challengeName, replaceable) {

  override suspend fun computeFunctionInfo(code: String): FunctionInfo {
    val lines =
      code.lines()
        .filterNot { it.startsWith("//") && it.contains(DESC) }
        .filterNot { it.trimStart().startsWith("package") }
    val funcCode = JavaParse.extractJavaFunction(lines)
    val invocations = JavaParse.extractJavaInvocations(lines, JavaParse.svmRegex, JavaParse.javaEndRegex)
    val returnType = JavaParse.deriveJavaReturnType(challengeName, lines)
    val script = JavaParse.convertToScript(lines)

    logger.debug { "$challengeName return type: $returnType script: \n${script.withLineNumbers()}" }

    description = deriveDescription(code, "//")

    val timedValue =
      measureTimedValue {
        ScriptPools.javaScriptPool
          .eval {
            assignIsolation(Isolation.IsolatedClassLoader)   // https://github.com/eobermuhlner/java-scriptengine
            import(List::class.java)
            import(ArrayList::class.java)
            evalScript(script)
          }
      }

    val correctAnswers = timedValue.value
    logger.debug { "$challengeName computed answers in ${timedValue.duration}" }

    if (correctAnswers !is List<*>)
      error("Invalid type returned for $challengeName [${correctAnswers::class.java.simpleName}]")

    return FunctionInfo(this, LanguageType.Java.languageName, code, funcCode, invocations, returnType, correctAnswers)
  }

  override fun toString() = "JavaChallenge(packageName='$packageNameAsPath', fileName='$fileName')"
}
