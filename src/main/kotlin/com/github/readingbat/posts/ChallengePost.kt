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

package com.github.readingbat.posts

import com.github.pambrose.common.util.*
import com.github.readingbat.common.BrowserSession
import com.github.readingbat.common.BrowserSession.Companion.querySessionDbmsId
import com.github.readingbat.common.ClassCode
import com.github.readingbat.common.CommonUtils.md5Of
import com.github.readingbat.common.CommonUtils.pathOf
import com.github.readingbat.common.Constants.CHALLENGE_SRC
import com.github.readingbat.common.Constants.GROUP_SRC
import com.github.readingbat.common.Constants.LANG_SRC
import com.github.readingbat.common.Constants.LIKE_DESC
import com.github.readingbat.common.Constants.MSG
import com.github.readingbat.common.Constants.RESP
import com.github.readingbat.common.Endpoints.CHALLENGE_ROOT
import com.github.readingbat.common.FormFields.CHALLENGE_ANSWERS_PARAM
import com.github.readingbat.common.FormFields.CHALLENGE_NAME_PARAM
import com.github.readingbat.common.FormFields.CORRECT_ANSWERS_PARAM
import com.github.readingbat.common.FormFields.GROUP_NAME_PARAM
import com.github.readingbat.common.FormFields.LANGUAGE_NAME_PARAM
import com.github.readingbat.common.FunctionInfo
import com.github.readingbat.common.KeyConstants.AUTH_KEY
import com.github.readingbat.common.KeyConstants.KEY_SEP
import com.github.readingbat.common.KeyConstants.NO_AUTH_KEY
import com.github.readingbat.common.ParameterIds.DISLIKE_CLEAR
import com.github.readingbat.common.ParameterIds.DISLIKE_COLOR
import com.github.readingbat.common.ParameterIds.LIKE_CLEAR
import com.github.readingbat.common.ParameterIds.LIKE_COLOR
import com.github.readingbat.common.ScriptPools.kotlinScriptPool
import com.github.readingbat.common.ScriptPools.pythonScriptPool
import com.github.readingbat.common.User
import com.github.readingbat.common.User.Companion.fetchUserDbmsIdFromCache
import com.github.readingbat.common.User.Companion.shouldPublish
import com.github.readingbat.common.browserSession
import com.github.readingbat.dsl.InvalidConfigurationException
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.dsl.ReturnType
import com.github.readingbat.dsl.ReturnType.BooleanType
import com.github.readingbat.dsl.ReturnType.IntType
import com.github.readingbat.dsl.ReturnType.StringType
import com.github.readingbat.dsl.isPostgresEnabled
import com.github.readingbat.server.*
import com.github.readingbat.server.ChallengeName.Companion.getChallengeName
import com.github.readingbat.server.GroupName.Companion.getGroupName
import com.github.readingbat.server.LanguageName.Companion.getLanguageName
import io.ktor.application.*
import io.ktor.request.*
import io.ktor.response.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KLogging
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import javax.script.ScriptException

internal data class StudentInfo(val studentId: String, val firstName: String, val lastName: String)

internal data class ClassEnrollment(val sessionId: String,
                                    val students: List<StudentInfo> = mutableListOf())

internal data class ChallengeResults(val invocation: Invocation,
                                     val userResponse: String = "",
                                     val answered: Boolean = false,
                                     val correct: Boolean = false,
                                     val hint: String = "")

@Serializable
internal class DashboardInfo(val userId: String,
                             val complete: Boolean,
                             val numCorrect: Int,
                             val history: DashboardHistory) {
  fun toJson() = Json.encodeToString(serializer(), this)
}

@Serializable
internal class DashboardHistory(val invocation: String,
                                val correct: Boolean = false,
                                val answers: String)

@Serializable
internal data class ChallengeHistory(var invocation: Invocation,
                                     var correct: Boolean = false,
                                     var incorrectAttempts: Int = 0,
                                     val answers: MutableList<String> = mutableListOf()) {

  fun markCorrect(userResponse: String) {
    correct = true
    if (userResponse.isNotBlank()) {
      if (answers.isEmpty() || answers.last() != userResponse)
        answers += userResponse
    }
  }

  fun markIncorrect(userResponse: String) {
    correct = false
    if (userResponse.isNotBlank()) {
      if (answers.isEmpty() || answers.last() != userResponse) {
        incorrectAttempts++
        answers += userResponse
      }
    }
  }

  fun markUnanswered() {
    correct = false
  }
}

internal class ChallengeNames(paramMap: Map<String, String>) {
  val languageName = LanguageName(paramMap[LANG_SRC] ?: throw InvalidConfigurationException("Missing language"))
  val groupName = GroupName(paramMap[GROUP_SRC] ?: throw InvalidConfigurationException("Missing group name"))
  val challengeName =
    ChallengeName(paramMap[CHALLENGE_SRC] ?: throw InvalidConfigurationException("Missing challenge name"))
}

internal object ChallengePost : KLogging() {

  private fun String.isJavaBoolean() = this == "true" || this == "false"
  private fun String.isPythonBoolean() = this == "True" || this == "False"

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

  private suspend fun String.equalsAsJvmList(correctAnswer: String): Pair<Boolean, String> {
    fun deriveHint() = if (isNotBracketed()) "Answer should be bracketed" else ""

    val compareExpr =
      "listOf(${if (isBracketed()) trimEnds() else this}) == listOf(${if (correctAnswer.isBracketed()) correctAnswer.trimEnds() else correctAnswer})"
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

  suspend fun PipelineCall.checkAnswers(content: ReadingBatContent, user: User?) {
    val params = call.receiveParameters()
    val paramMap = params.entries().map { it.key to it.value[0] }.toMap()
    val names = ChallengeNames(paramMap)
    val userResponses = params.entries().filter { it.key.startsWith(RESP) }
    val challenge = content.findChallenge(names.languageName, names.groupName, names.challengeName)
    val funcInfo = challenge.functionInfo(content)

    logger.debug("Found ${userResponses.size} user responses in $paramMap")

    val results =
      userResponses.indices
        .map { i ->
          val languageName = names.languageName
          val userResponse = paramMap[RESP + i]?.trim() ?: throw InvalidConfigurationException("Missing user response")
          val correctAnswer = funcInfo.correctAnswers[i]
          val returnType = funcInfo.returnType
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

          ChallengeResults(invocation = funcInfo.invocations[i],
                           userResponse = userResponse,
                           answered = answered,
                           correct = correctAndHint.first,
                           hint = correctAndHint.second)
        }


    // Save whether all the answers for the challenge were correct
    if (isPostgresEnabled())
      transaction {
        val browserSession = call.browserSession
        saveChallengeAnswers(user, browserSession, content, names, paramMap, funcInfo, userResponses, results)
      }

    // Return values: 0 = not answered, 1 = correct, 2 = incorrect
    val answerMapping =
      results
        .map {
          when {
            !it.answered -> listOf(0, "".toDoubleQuoted())
            it.correct -> listOf(1, "".toDoubleQuoted())
            else -> listOf(2, it.hint.toDoubleQuoted())
          }
        }
    logger.debug { "Answers: $answerMapping" }
    call.respondText(answerMapping.toString())
  }

  private fun deleteChallengeInfo(type: String, id: String, md5: String) =
    when (type) {
      AUTH_KEY ->
        transaction {
          UserChallengeInfo
            .deleteWhere { (UserChallengeInfo.userRef eq fetchUserDbmsIdFromCache(id)) and (UserChallengeInfo.md5 eq md5) }
        }
      NO_AUTH_KEY ->
        transaction {
          SessionChallengeInfo
            .deleteWhere { (SessionChallengeInfo.sessionRef eq querySessionDbmsId(id)) and (SessionChallengeInfo.md5 eq md5) }
        }
      else -> throw InvalidConfigurationException("Invalid type: $type")
    }

  private fun deleteAnswerHistory(type: String, id: String, md5: String) =
    when (type) {
      AUTH_KEY ->
        transaction {
          UserAnswerHistory
            .deleteWhere { (UserAnswerHistory.userRef eq fetchUserDbmsIdFromCache(id)) and (UserAnswerHistory.md5 eq md5) }
        }
      NO_AUTH_KEY ->
        transaction {
          SessionAnswerHistory
            .deleteWhere { (SessionAnswerHistory.sessionRef eq querySessionDbmsId(id)) and (SessionAnswerHistory.md5 eq md5) }
        }
      else -> throw InvalidConfigurationException("Invalid type: $type")
    }

  suspend fun PipelineCall.clearChallengeAnswers(content: ReadingBatContent, user: User?): String {
    val params = call.receiveParameters()

    val languageName = params.getLanguageName(LANGUAGE_NAME_PARAM)
    val groupName = params.getGroupName(GROUP_NAME_PARAM)
    val challengeName = params.getChallengeName(CHALLENGE_NAME_PARAM)

    val correctAnswersKey = params[CORRECT_ANSWERS_PARAM] ?: ""
    val challengeAnswersKey = params[CHALLENGE_ANSWERS_PARAM] ?: ""

    val challenge = content[languageName, groupName, challengeName]
    val funcInfo = challenge.functionInfo(content)
    val path = pathOf(CHALLENGE_ROOT, languageName, groupName, challengeName)

    // Clears answer history
    if (correctAnswersKey.isNotEmpty()) {
      correctAnswersKey.split(KEY_SEP).let { deleteChallengeInfo(it[1], it[2], it[3]) }
        .also {
          logger.info { "Deleted $it correctAnswers vals for ${challenge.challengeName} $correctAnswersKey" }
        }
    }

    if (challengeAnswersKey.isNotEmpty()) {
      challengeAnswersKey.split(KEY_SEP).let { deleteAnswerHistory(it[1], it[2], it[3]) }
        .also {
          logger.info { "Deleted $it challengeAnswers for ${challenge.challengeName} $challengeAnswersKey" }
        }
    }

    user?.resetHistory(funcInfo, challenge, content.maxHistoryLength)

    throw RedirectException("$path?$MSG=${"Answers cleared".encode()}")
  }

  suspend fun PipelineCall.clearGroupAnswers(content: ReadingBatContent, user: User?): String {
    val parameters = call.receiveParameters()

    val languageName = parameters.getLanguageName(LANGUAGE_NAME_PARAM)
    val groupName = parameters.getGroupName(GROUP_NAME_PARAM)

    val correctJson = parameters[CORRECT_ANSWERS_PARAM] ?: ""
    val challengeJson = parameters[CHALLENGE_ANSWERS_PARAM] ?: ""

    val correctAnswersKeys = Json.decodeFromString<List<String>>(correctJson)
    val challengeAnswersKeys = Json.decodeFromString<List<String>>(challengeJson)

    val path = pathOf(CHALLENGE_ROOT, languageName, groupName)

    if (!isPostgresEnabled())
      throw RedirectException("$path?$MSG=${"Database not enabled"}")

    correctAnswersKeys
      .forEach { correctAnswersKey ->
        if (correctAnswersKey.isNotEmpty()) {
          correctAnswersKey.split(KEY_SEP).let { deleteChallengeInfo(it[1], it[2], it[3]) }
            .also {
              logger.info { "Deleted $it correctAnswers vals for $correctAnswersKey" }
            }
        }
      }

    challengeAnswersKeys
      .forEach { challengeAnswersKey ->
        if (challengeAnswersKey.isNotEmpty()) {
          challengeAnswersKey.split(KEY_SEP).let { deleteAnswerHistory(it[1], it[2], it[3]) }
            .also {
              logger.info { "Deleted $it challengeAnswers for $challengeAnswersKey" }
            }
        }
      }

    if (user.isNotNull()) {
      content.findGroup(languageName, groupName).challenges
        .forEach { challenge ->
          logger.info { "Clearing answers for challengeName ${challenge.challengeName}" }
          val funcInfo = challenge.functionInfo(content)
          user.resetHistory(funcInfo, challenge, content.maxHistoryLength)
        }
    }

    throw RedirectException("$path?$MSG=${"Answers cleared".encode()}")
  }

  suspend fun PipelineCall.likeDislike(content: ReadingBatContent, user: User?) {
    val params = call.receiveParameters()
    val paramMap = params.entries().map { it.key to it.value[0] }.toMap()
    val names = ChallengeNames(paramMap)
    //val challenge = content.findChallenge(names.languageName, names.groupName, names.challengeName)

    val likeArg = paramMap[LIKE_DESC]?.trim() ?: throw InvalidConfigurationException("Missing like/dislike argument")

    // Return values: 0 = not answered, 1 = like selected, 2 = dislike selected
    val likeVal =
      when (likeArg) {
        LIKE_CLEAR -> 1
        LIKE_COLOR,
        DISLIKE_COLOR -> 0
        DISLIKE_CLEAR -> 2
        else -> throw InvalidConfigurationException("Invalid like/dislike argument: $likeArg")
      }

    logger.debug { "Like/dislike arg -- response: $likeArg -- $likeVal" }

    val browserSession = call.browserSession
    saveLikeDislike(user, browserSession, names, likeVal)

    call.respondText(likeVal.toString())
  }

  private fun saveChallengeAnswers(user: User?,
                                   browserSession: BrowserSession?,
                                   content: ReadingBatContent,
                                   names: ChallengeNames,
                                   paramMap: Map<String, String>,
                                   funcInfo: FunctionInfo,
                                   userResponses: List<Map.Entry<String, List<String>>>,
                                   results: List<ChallengeResults>) {
    val challengeMd5 = md5Of(names.languageName, names.groupName, names.challengeName)

    val complete = results.all { it.correct }
    val numCorrect = results.count { it.correct }

    // Save the last answers given
    val invokeList =
      userResponses.indices
        .map { i ->
          val userResponse =
            paramMap[RESP + i]?.trim() ?: throw InvalidConfigurationException("Missing user response")
          funcInfo.invocations[i] to userResponse
        }

    val invokeMap = invokeList.map { it.first.value to it.second }.toMap()
    val invokeStr = Json.encodeToString(invokeMap)
    when {
      user.isNotNull() ->
        UserChallengeInfo
          .upsert(conflictIndex = userChallengeInfoIndex) { row ->
            row[userRef] = user.userDbmsId
            row[md5] = challengeMd5
            row[updated] = DateTime.now(DateTimeZone.UTC)
            row[allCorrect] = complete
            row[answersJson] = invokeStr
          }
      browserSession.isNotNull() ->
        SessionChallengeInfo
          .upsert(conflictIndex = sessionChallengeInfoIndex) { row ->
            row[sessionRef] = browserSession.sessionDbmsId()
            row[md5] = challengeMd5
            row[updated] = DateTime.now(DateTimeZone.UTC)
            row[allCorrect] = complete
            row[answersJson] = invokeStr
          }
      else ->
        logger.warn { "ChallengeInfo not updated" }
    }

    val classCode = user?.enrolledClassCode ?: ClassCode.DISABLED_CLASS_CODE
    val shouldPublish = user.shouldPublish(classCode)

    // Save the history of each answer on a per-invocation basis
    for (result in results) {
      val historyMd5 = md5Of(names.languageName, names.groupName, names.challengeName, result.invocation)

      val history =
        when {
          user.isNotNull() -> user.answerHistory(historyMd5, result.invocation)
          browserSession.isNotNull() -> browserSession.answerHistory(historyMd5, result.invocation)
          else -> ChallengeHistory(result.invocation)
        }

      when {
        !result.answered -> history.markUnanswered()
        result.correct -> history.markCorrect(result.userResponse)
        else -> history.markIncorrect(result.userResponse)
      }

      when {
        user.isNotNull() ->
          UserAnswerHistory
            .upsert(conflictIndex = userAnswerHistoryIndex) { row ->
              row[userRef] = user.userDbmsId
              row[md5] = historyMd5
              row[invocation] = history.invocation.value
              row[updated] = DateTime.now(DateTimeZone.UTC)
              row[correct] = history.correct
              row[incorrectAttempts] = history.incorrectAttempts
              row[historyJson] = Json.encodeToString(history.answers)
            }
        browserSession.isNotNull() ->
          SessionAnswerHistory
            .upsert(conflictIndex = sessionAnswerHistoryIndex) { row ->
              row[sessionRef] = browserSession.sessionDbmsId()
              row[md5] = historyMd5
              row[invocation] = history.invocation.value
              row[updated] = DateTime.now(DateTimeZone.UTC)
              row[correct] = history.correct
              row[incorrectAttempts] = history.incorrectAttempts
              row[historyJson] = Json.encodeToString(history.answers)
            }
        else ->
          logger.warn { "Answer history not updated" }
      }

      if (shouldPublish) {
        val maxLength = content.maxHistoryLength
        user?.publishAnswers(classCode, funcInfo.challengeMd5, maxLength, complete, numCorrect, history)
      }
    }
  }

  private fun saveLikeDislike(user: User?,
                              browserSession: BrowserSession?,
                              names: ChallengeNames,
                              likeVal: Int) {
    val challengeMd5 = md5Of(names.languageName, names.groupName, names.challengeName)
    when {
      user.isNotNull() ->
        transaction {
          UserChallengeInfo
            .upsert(conflictIndex = userChallengeInfoIndex) { row ->
              row[userRef] = user.userDbmsId
              row[md5] = challengeMd5
              row[updated] = DateTime.now(DateTimeZone.UTC)
              row[likeDislike] = likeVal.toShort()
            }
        }
      browserSession.isNotNull() ->
        transaction {
          SessionChallengeInfo
            .upsert(conflictIndex = sessionChallengeInfoIndex) { row ->
              row[sessionRef] = browserSession.sessionDbmsId()
              row[md5] = challengeMd5
              row[updated] = DateTime.now(DateTimeZone.UTC)
              row[likeDislike] = likeVal.toShort()
            }
        }
      else -> {
        // Do nothing
      }
    }
  }
}