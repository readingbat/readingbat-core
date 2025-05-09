/*
 * Copyright © 2023 Paul Ambrose (pambrose@mac.com)
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

import com.github.pambrose.common.util.encode
import com.github.pambrose.common.util.isNotNull
import com.github.pambrose.common.util.maxLength
import com.github.pambrose.common.util.md5Of
import com.github.pambrose.common.util.pathOf
import com.github.pambrose.common.util.toDoubleQuoted
import com.github.readingbat.common.BrowserSession
import com.github.readingbat.common.BrowserSession.Companion.findOrCreateSessionDbmsId
import com.github.readingbat.common.Constants
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
import com.github.readingbat.common.User
import com.github.readingbat.common.User.Companion.fetchUserDbmsIdFromCache
import com.github.readingbat.common.browserSession
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.dsl.isDbmsEnabled
import com.github.readingbat.posts.AnswerStatus.CORRECT
import com.github.readingbat.posts.AnswerStatus.INCORRECT
import com.github.readingbat.posts.AnswerStatus.NOT_ANSWERED
import com.github.readingbat.server.ChallengeName
import com.github.readingbat.server.ChallengeName.Companion.getChallengeName
import com.github.readingbat.server.GroupName
import com.github.readingbat.server.GroupName.Companion.getGroupName
import com.github.readingbat.server.Invocation
import com.github.readingbat.server.LanguageName
import com.github.readingbat.server.LanguageName.Companion.getLanguageName
import com.github.readingbat.server.RedirectException
import com.github.readingbat.server.ServerUtils.paramMap
import com.github.readingbat.server.SessionAnswerHistoryTable
import com.github.readingbat.server.SessionChallengeInfoTable
import com.github.readingbat.server.UserAnswerHistoryTable
import com.github.readingbat.server.UserChallengeInfoTable
import com.github.readingbat.server.sessionAnswerHistoryIndex
import com.github.readingbat.server.sessionChallengeInfoIndex
import com.github.readingbat.server.userAnswerHistoryIndex
import com.github.readingbat.server.userChallengeInfoIndex
import com.pambrose.common.exposed.upsert
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingContext
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.joda.time.DateTimeZone

internal data class StudentInfo(val studentId: String, val firstName: String, val lastName: String)

@Suppress("unused")
internal data class ClassEnrollment(
  val sessionId: String,
  val students: List<StudentInfo> = mutableListOf(),
)

data class ChallengeResults(
  val invocation: Invocation,
  val userResponse: String = "",
  val answered: Boolean = false,
  val correct: Boolean = false,
  val hint: String = "",
)

@Serializable
internal class LikeDislikeInfo(
  val userId: String,
  val likeDislike: String,
) {
  @Required
  val type: String = Constants.LIKE_DISLIKE_CODE

  fun toJson() = Json.encodeToString(serializer(), this)
}

@Suppress("unused")
@Serializable
internal class DashboardInfo(
  val userId: String,
  val complete: Boolean,
  val numCorrect: Int,
  val history: DashboardHistory,
) {
  fun toJson() = Json.encodeToString(serializer(), this)
}

@Serializable
internal class DashboardHistory(
  val invocation: String,
  val correct: Boolean = false,
  val answers: String,
)

@Serializable
data class ChallengeHistory(
  var invocation: Invocation,
  var correct: Boolean = false,
  var incorrectAttempts: Int = 0,
  @Required val answers: MutableList<String> = mutableListOf(),
) {
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

enum class AnswerStatus(val value: Int) {
  NOT_ANSWERED(0),
  CORRECT(1),
  INCORRECT(2),
  ;

  companion object {
    fun Int.toAnswerStatus() = entries.firstOrNull { this == it.value } ?: error("Invalid AnswerStatus value: $this")
  }
}

internal class ChallengeNames(paramMap: Map<String, String>) {
  val languageName = LanguageName(paramMap[LANG_SRC] ?: error("Missing language"))
  val groupName = GroupName(paramMap[GROUP_SRC] ?: error("Missing group name"))
  val challengeName = ChallengeName(paramMap[CHALLENGE_SRC] ?: error("Missing challenge name"))

  fun md5() = md5Of(languageName, groupName, challengeName)

  fun md5(invocation: Invocation) = md5Of(languageName, groupName, challengeName, invocation)
}

private val EMPTY_STRING = "".toDoubleQuoted()

internal object ChallengePost {
  private val logger = KotlinLogging.logger {}

  suspend fun RoutingContext.checkAnswers(content: ReadingBatContent, user: User?) {
    val params = call.receiveParameters()
    val paramMap = params.entries().associate { it.key to it.value[0] }
    val names = ChallengeNames(paramMap)
    val challenge = content.findChallenge(names.languageName, names.groupName, names.challengeName)
    val funcInfo = challenge.functionInfo()
    val userResponses = params.entries().filter { it.key.startsWith(RESP) }

    logger.debug { "Found ${userResponses.size} user responses in $paramMap" }

    val results =
      userResponses.indices
        .map { paramMap[RESP + it]?.trim() ?: error("Missing user response") }
        .mapIndexed { i, userResponse -> funcInfo.checkResponse(i, userResponse) }

    if (isDbmsEnabled())
      saveChallengeAnswers(user, call.browserSession, content, names, paramMap, funcInfo, userResponses, results)

    val answerMapping =
      results
        .map {
          when {
            !it.answered -> listOf(NOT_ANSWERED.value, EMPTY_STRING)
            it.correct -> listOf(CORRECT.value, EMPTY_STRING)
            else -> listOf(INCORRECT.value, it.hint.toDoubleQuoted())
          }
        }
    call.respondText(answerMapping.toString())
  }

  private fun deleteChallengeInfo(type: String, id: String, md5Val: String) =
    when (type) {
      AUTH_KEY ->
        transaction {
          with(UserChallengeInfoTable) {
            deleteWhere { (userRef eq fetchUserDbmsIdFromCache(id)) and (md5 eq md5Val) }
          }
        }

      NO_AUTH_KEY ->
        transaction {
          val sessionDbmsId = findOrCreateSessionDbmsId(id, false)
          with(SessionChallengeInfoTable) {
            deleteWhere { (sessionRef eq sessionDbmsId) and (md5 eq md5Val) }
          }
        }

      else -> error("Invalid type: $type")
    }

  private fun deleteAnswerHistory(type: String, id: String, md5Val: String) =
    when (type) {
      AUTH_KEY ->
        transaction {
          with(UserAnswerHistoryTable) {
            deleteWhere { (userRef eq fetchUserDbmsIdFromCache(id)) and (md5 eq md5Val) }
          }
        }

      NO_AUTH_KEY ->
        transaction {
          val sessionDbmsId = findOrCreateSessionDbmsId(id, false)
          with(SessionAnswerHistoryTable) {
            deleteWhere { (sessionRef eq sessionDbmsId) and (md5 eq md5Val) }
          }
        }

      else -> error("Invalid type: $type")
    }

  suspend fun RoutingContext.clearChallengeAnswers(content: ReadingBatContent, user: User?): String {
    val params = call.receiveParameters()

    val languageName = params.getLanguageName(LANGUAGE_NAME_PARAM)
    val groupName = params.getGroupName(GROUP_NAME_PARAM)
    val challengeName = params.getChallengeName(CHALLENGE_NAME_PARAM)

    val correctAnswersKey = params[CORRECT_ANSWERS_PARAM] ?: ""
    val challengeAnswersKey = params[CHALLENGE_ANSWERS_PARAM] ?: ""

    val challenge = content[languageName, groupName, challengeName]
    val funcInfo = challenge.functionInfo()
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

  suspend fun RoutingContext.clearGroupAnswers(content: ReadingBatContent, user: User?): String {
    val parameters = call.receiveParameters()

    val languageName = parameters.getLanguageName(LANGUAGE_NAME_PARAM)
    val groupName = parameters.getGroupName(GROUP_NAME_PARAM)

    val correctJson = parameters[CORRECT_ANSWERS_PARAM] ?: ""
    val challengeJson = parameters[CHALLENGE_ANSWERS_PARAM] ?: ""

    val correctAnswersKeys = Json.decodeFromString<List<String>>(correctJson)
    val challengeAnswersKeys = Json.decodeFromString<List<String>>(challengeJson)

    val path = pathOf(CHALLENGE_ROOT, languageName, groupName)

    if (!isDbmsEnabled())
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
          val funcInfo = challenge.functionInfo()
          user.resetHistory(funcInfo, challenge, content.maxHistoryLength)
        }
    }

    throw RedirectException("$path?$MSG=${"Answers cleared".encode()}")
  }

  suspend fun RoutingContext.likeDislike(user: User?) {
    val paramMap = call.paramMap()
    val names = ChallengeNames(paramMap)
    // val challenge = content.findChallenge(names.languageName, names.groupName, names.challengeName)

    val likeArg = paramMap[LIKE_DESC]?.trim() ?: error("Missing like/dislike argument")

    // Return values: 0 = not answered, 1 = like selected, 2 = dislike selected
    val likeVal =
      when (likeArg) {
        LIKE_CLEAR -> 1

        LIKE_COLOR,
        DISLIKE_COLOR,
          -> 0

        DISLIKE_CLEAR -> 2
        else -> error("Invalid like/dislike argument: $likeArg")
      }

    logger.debug { "Like/dislike arg -- response: $likeArg -- $likeVal" }

    val browserSession = call.browserSession
    saveLikeDislike(user, browserSession, names, likeVal)

    call.respondText(likeVal.toString())
  }

  private suspend fun saveChallengeAnswers(
    user: User?,
    browserSession: BrowserSession?,
    content: ReadingBatContent,
    names: ChallengeNames,
    paramMap: Map<String, String>,
    funcInfo: FunctionInfo,
    userResponses: List<Map.Entry<String, List<String>>>,
    results: List<ChallengeResults>,
  ) {
    // Save the last answers given
    val invokeList =
      userResponses.indices
        .map { i ->
          val userResponse = paramMap[RESP + i]?.trim()?.maxLength(256) ?: error("Missing user response")
          funcInfo.invocations[i] to userResponse
        }

    val challengeMd5 = names.md5()
    val shouldPublish = user?.shouldPublish() ?: false
    val complete = results.all { it.correct }
    val numCorrect = results.count { it.correct }
    val invokeMap = invokeList.associate { it.first.value to it.second }
    val invokeStr = Json.encodeToString(invokeMap)
    val historyList = mutableListOf<ChallengeHistory>()

    transaction {
      when {
        user.isNotNull() ->
          with(UserChallengeInfoTable) {
            upsert(conflictIndex = userChallengeInfoIndex) { row ->
              row[userRef] = user.userDbmsId
              row[md5] = challengeMd5
              row[updated] = DateTime.now(DateTimeZone.UTC)
              row[allCorrect] = complete
              row[answersJson] = invokeStr
            }
          }

        browserSession.isNotNull() ->
          with(SessionChallengeInfoTable) {
            upsert(conflictIndex = sessionChallengeInfoIndex) { row ->
              row[sessionRef] = browserSession.queryOrCreateSessionDbmsId()
              row[md5] = challengeMd5
              row[updated] = DateTime.now(DateTimeZone.UTC)
              row[allCorrect] = complete
              row[answersJson] = invokeStr
            }
          }

        else ->
          logger.warn { "ChallengeInfo not updated" }
      }

      // Save the history of each answer on a per-invocation basis
      for (result in results) {
        val historyMd5 = names.md5(result.invocation)
        val history =
          when {
            user.isNotNull() -> user.answerHistory(historyMd5, result.invocation)
            browserSession.isNotNull() -> browserSession.answerHistory(historyMd5, result.invocation)
            else -> ChallengeHistory(result.invocation)
          }
        historyList += history

        when {
          !result.answered -> history.markUnanswered()
          result.correct -> history.markCorrect(result.userResponse)
          else -> history.markIncorrect(result.userResponse)
        }

        val invocationVal = history.invocation.value
        val updatedVal = DateTime.now(DateTimeZone.UTC)
        val correctVal = history.correct
        val incorrectAttemptsVal = history.incorrectAttempts
        val json = Json.encodeToString(history.answers)

        when {
          user.isNotNull() ->
            with(UserAnswerHistoryTable) {
              upsert(conflictIndex = userAnswerHistoryIndex) { row ->
                row[userRef] = user.userDbmsId
                row[md5] = historyMd5
                row[invocation] = invocationVal
                row[updated] = updatedVal
                row[correct] = correctVal
                row[incorrectAttempts] = incorrectAttemptsVal
                row[historyJson] = json
              }
            }

          browserSession.isNotNull() ->
            with(SessionAnswerHistoryTable) {
              upsert(conflictIndex = sessionAnswerHistoryIndex) { row ->
                row[sessionRef] = browserSession.queryOrCreateSessionDbmsId()
                row[md5] = historyMd5
                row[invocation] = invocationVal
                row[updated] = updatedVal
                row[correct] = correctVal
                row[incorrectAttempts] = incorrectAttemptsVal
                row[historyJson] = json
              }
            }

          else ->
            logger.warn { "Answer history not updated" }
        }
      }
    }

    // This is done oustide the transaction
    if (shouldPublish) {
      historyList.forEach {
        user?.publishAnswers(challengeMd5, content.maxHistoryLength, complete, numCorrect, it)
      }
    }
  }

  private suspend fun saveLikeDislike(
    user: User?,
    browserSession: BrowserSession?,
    names: ChallengeNames,
    likeDislikeVal: Int,
  ) {
    val challengeMd5 = names.md5()
    val shouldPublish = user?.shouldPublish() ?: false
    when {
      user.isNotNull() -> {
        transaction {
          with(UserChallengeInfoTable) {
            upsert(conflictIndex = userChallengeInfoIndex) { row ->
              row[userRef] = user.userDbmsId
              row[md5] = challengeMd5
              row[updated] = DateTime.now(DateTimeZone.UTC)
              row[likeDislike] = likeDislikeVal.toShort()
            }
          }
        }
        if (shouldPublish)
          user.publishLikeDislike(challengeMd5, likeDislikeVal)
      }

      browserSession.isNotNull() ->
        transaction {
          with(SessionChallengeInfoTable) {
            upsert(conflictIndex = sessionChallengeInfoIndex) { row ->
              row[sessionRef] = browserSession.queryOrCreateSessionDbmsId()
              row[md5] = challengeMd5
              row[updated] = DateTime.now(DateTimeZone.UTC)
              row[likeDislike] = likeDislikeVal.toShort()
            }
          }
        }

      else -> {
        // Do nothing
      }
    }
  }
}
