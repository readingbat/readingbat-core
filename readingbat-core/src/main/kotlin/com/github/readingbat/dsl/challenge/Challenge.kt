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
import com.github.pambrose.common.redis.RedisUtils.withNonNullRedisPool
import com.github.pambrose.common.redis.RedisUtils.withRedisPool
import com.github.pambrose.common.util.AbstractRepo
import com.github.pambrose.common.util.FileSystemSource
import com.github.pambrose.common.util.ensureSuffix
import com.github.pambrose.common.util.isNotNull
import com.github.pambrose.common.util.md5Of
import com.github.pambrose.common.util.pathOf
import com.github.pambrose.common.util.toDoubleQuoted
import com.github.pambrose.common.util.toSingleQuoted
import com.github.pambrose.common.util.withLineNumbers
import com.github.readingbat.common.BrowserSession
import com.github.readingbat.common.FunctionInfo
import com.github.readingbat.common.KeyConstants.SOURCE_CODE_KEY
import com.github.readingbat.common.KeyConstants.keyOf
import com.github.readingbat.common.User
import com.github.readingbat.dsl.ChallengeGroup
import com.github.readingbat.dsl.LanguageType.Java
import com.github.readingbat.dsl.LanguageType.Kotlin
import com.github.readingbat.dsl.LanguageType.Python
import com.github.readingbat.dsl.ReadingBatDslMarker
import com.github.readingbat.dsl.ReturnType
import com.github.readingbat.dsl.TextFormatter
import com.github.readingbat.dsl.agentLaunchId
import com.github.readingbat.dsl.isContentCachingEnabled
import com.github.readingbat.dsl.isDbmsEnabled
import com.github.readingbat.dsl.parse.JavaParse
import com.github.readingbat.dsl.parse.JavaParse.convertToScript
import com.github.readingbat.dsl.parse.JavaParse.deriveJavaReturnType
import com.github.readingbat.dsl.parse.JavaParse.extractJavaInvocations
import com.github.readingbat.dsl.parse.KotlinParse
import com.github.readingbat.dsl.parse.KotlinParse.convertToKotlinScript
import com.github.readingbat.dsl.parse.KotlinParse.extractKotlinFunction
import com.github.readingbat.dsl.parse.KotlinParse.extractKotlinInvocations
import com.github.readingbat.dsl.parse.PythonParse
import com.github.readingbat.dsl.parse.PythonParse.convertToPythonScript
import com.github.readingbat.dsl.parse.PythonParse.extractPythonFunction
import com.github.readingbat.dsl.parse.PythonParse.extractPythonInvocations
import com.github.readingbat.server.ChallengeName
import com.github.readingbat.server.Invocation
import com.github.readingbat.server.ReadingBatServer.redisPool
import com.github.readingbat.server.ScriptPools
import com.github.readingbat.server.SessionChallengeInfoTable
import com.github.readingbat.server.UserChallengeInfoTable
import com.github.readingbat.utils.StringUtils.toCapitalized
import com.pambrose.common.exposed.get
import kotlinx.coroutines.runBlocking
import mu.KLogging
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.net.URL
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.typeOf
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

@ReadingBatDslMarker
sealed class Challenge(val challengeGroup: ChallengeGroup<*>,
                       val challengeName: ChallengeName,
                       val replaceable: Boolean) {
  internal val challengeId = counter.incrementAndGet()
  private val fqName by lazy { packageNameAsPath.ensureSuffix("/") + fileName.ensureSuffix(".${languageType.suffix}") }

  // Allow description updates only if not found in the Content.kt decl
  private val isDescriptionSetInDsl by lazy { description.isNotBlank() }
  internal val gitpodUrl by lazy { pathOf(repo.sourcePrefix, "blob/$branchName", srcPath, fqName) }
  internal val parsedDescription by lazy { TextFormatter.renderText(description) }
  internal val path by lazy { pathOf(languageName, groupName, challengeName) }

  private val languageGroup get() = challengeGroup.languageGroup
  private val metrics get() = challengeGroup.languageGroup.metrics
  private val repo get() = languageGroup.repo
  private val branchName get() = languageGroup.branchName
  private val srcPath get() = languageGroup.srcPath
  internal val languageType get() = challengeGroup.languageType
  internal val languageName get() = languageType.languageName
  internal val groupName get() = challengeGroup.groupName
  protected val packageNameAsPath get() = challengeGroup.packageNameAsPath
  private val content get() = challengeGroup.languageGroup.content

  val correctAnswers get() = functionInfo().correctAnswers

  // User properties
  var fileName = "$challengeName.${languageType.suffix}"
  var codingBatEquiv = ""
  var description = ""

  internal abstract suspend fun computeFunctionInfo(code: String): FunctionInfo

  fun md5() = md5Of(languageName, groupName, challengeName)

  fun md5(invocation: Invocation) = md5Of(languageName, groupName, challengeName, invocation)

  private fun measureParsing(code: String) =
    metrics.challengeParseDuration.labels(agentLaunchId(), languageType.toString()).startTimer()
      .let {
        try {
          runBlocking { computeFunctionInfo(code) }
        } finally {
          it.observeDuration()
        }
      }

  private val sourceCodeKey by lazy { keyOf(SOURCE_CODE_KEY, languageType.name, md5()) }

  private fun fetchCodeFromRedis() =
    if (isContentCachingEnabled()) redisPool?.withRedisPool { redis -> redis?.get(sourceCodeKey) } else null

  fun functionInfo() =
    if (repo.remote) {
      content.functionInfoMap
        .computeIfAbsent(challengeId) {
          val timer = metrics.challengeRemoteReadDuration.labels(agentLaunchId()).startTimer()
          val code =
            fetchCodeFromRedis() ?: try {
              val path = pathOf((repo as AbstractRepo).rawSourcePrefix, branchName, srcPath, fqName)
              val (text, dur) = measureTimedValue { URL(path).readText() }
              logger.debug { """Fetched "${pathOf(groupName, fileName)}" in: $dur from: $path""" }

              if (isContentCachingEnabled()) {
                redisPool?.withNonNullRedisPool(true) { redis ->
                  redis.set(sourceCodeKey, text)
                  logger.debug { """Saved "${pathOf(groupName, fileName)}" to redis""" }
                }
              }
              text
            } finally {
              timer.observeDuration()
            }
          measureParsing(code)
        }
    }
    else {
      fun parseCode(): FunctionInfo {
        val fs = repo as FileSystemSource
        val file = fs.file(pathOf(fs.pathPrefix, srcPath, packageNameAsPath, fileName))
        logger.info { """Fetching "${file.fileName}" from "${Paths.get("").toAbsolutePath()}""" }
        return measureParsing(file.content)
      }

      if (content.cacheChallenges)
        content.functionInfoMap.computeIfAbsent(challengeId) { parseCode() }
      else
        parseCode()
    }

  internal open fun validate() {
    if (challengeName.value.isEmpty())
      error(""""$challengeName" is empty""")
  }

  @Suppress("unused")
  private fun Any?.prettyQuote(capitalizePythonBooleans: Boolean = true, useDoubleQuotes: Boolean = false) =
    when {
      this is String -> if (languageType.useDoubleQuotes || useDoubleQuotes) toDoubleQuoted() else toSingleQuoted()
      capitalizePythonBooleans && this is Boolean && languageType.isPython -> toString().toCapitalized()
      else -> toString()
    }

  protected fun deriveDescription(code: String, commentPrefix: String) =
    if (isDescriptionSetInDsl) {
      description
    }
    else {
      code.lines().asSequence()
        .filter { it.startsWith(commentPrefix) && it.contains(DESC) }
        .map { it.replaceFirst(commentPrefix, "") }  // Remove comment prefix
        .map { it.replaceFirst(DESC, "") }          // Remove @desc
        .map { it.trim() }                                    // Strip leading and trailing spaces
        .joinToString("\n")
        .also { logger.debug { """Assigning $challengeName description = "$it"""" } }
    }

  internal fun isCorrect(user: User?, browserSession: BrowserSession?): Boolean {
    val challengeMd5 = md5()
    return when {
      !isDbmsEnabled() -> false
      user.isNotNull() ->
        transaction {
          UserChallengeInfoTable
            .slice(UserChallengeInfoTable.allCorrect)
            .select { (UserChallengeInfoTable.userRef eq user.userDbmsId) and (UserChallengeInfoTable.md5 eq challengeMd5) }
            .map { it[0] as Boolean }
            .firstOrNull() ?: false
        }
      browserSession.isNotNull() ->
        transaction {
          SessionChallengeInfoTable
            .slice(SessionChallengeInfoTable.allCorrect)
            .select { (SessionChallengeInfoTable.sessionRef eq browserSession.sessionDbmsId()) and (SessionChallengeInfoTable.md5 eq challengeMd5) }
            .map { it[0] as Boolean }
            .firstOrNull() ?: false
        }
      else -> false
    }
  }

  override fun toString() = "$languageName $groupName $challengeName"

  companion object : KLogging() {
    internal val counter = AtomicInteger(0)
    internal const val DESC = "@desc "

    internal fun challenge(challengeGroup: ChallengeGroup<*>,
                           challengeName: ChallengeName,
                           replaceable: Boolean) =
      when (challengeGroup.languageType) {
        Python -> PythonChallenge(challengeGroup, challengeName, replaceable)
        Java -> JavaChallenge(challengeGroup, challengeName, replaceable)
        Kotlin -> KotlinChallenge(challengeGroup, challengeName, replaceable)
      }
  }
}

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
    val invocations = extractJavaInvocations(lines, JavaParse.svmRegex, JavaParse.javaEndRegex)
    val returnType = deriveJavaReturnType(challengeName, lines)
    val script = convertToScript(lines)

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

    return FunctionInfo(this, Java.languageName, code, funcCode, invocations, returnType, correctAnswers)
  }

  override fun toString() = "JavaChallenge(packageName='$packageNameAsPath', fileName='$fileName')"
}

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
    val funcCode = "\n${extractKotlinFunction(lines)}\n\n"
    val invocations = extractKotlinInvocations(lines, KotlinParse.funMainRegex, KotlinParse.kotlinEndRegex)
    val script = convertToKotlinScript(lines).also { logger.debug { "Kotlin: $it" } }
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
                        Kotlin.languageName,
                        strippedCode,
                        funcCode,
                        invocations,
                        returnType,
                        correctAnswers)
  }

  override fun toString() =
    "KotlinChallenge(packageName='$packageNameAsPath', fileName='$fileName', returnType=$returnType)"
}

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
    val funcCode = extractPythonFunction(lines)
    val invocations = extractPythonInvocations(lines, PythonParse.defMainRegex, PythonParse.ifMainEndRegex)
    val script = convertToPythonScript(lines)
    val correctAnswers = mutableListOf<Any>()

    logger.debug { "$challengeName return type: $returnType script: \n${script.withLineNumbers()}" }

    description = deriveDescription(code, "#")

    measureTime {
      ScriptPools.pythonScriptPool
        .eval {
          add(PythonParse.varName, correctAnswers)
          eval(script)
        }
    }.also {
      logger.debug { "$challengeName computed answers in $it for: $correctAnswers" }
    }

    return FunctionInfo(this, Python.languageName, code, funcCode, invocations, returnType, correctAnswers)
  }

  override fun toString() =
    "PythonChallenge(packageName='$packageNameAsPath', fileName='$fileName', returnType=$returnType)"
}