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

package com.readingbat.posts

import com.pambrose.common.exposed.upsert
import com.pambrose.common.util.encode
import com.pambrose.common.util.maxLength
import com.pambrose.common.util.md5Of
import com.pambrose.common.util.pathOf
import com.readingbat.common.AnswerPublisher
import com.readingbat.common.Constants
import com.readingbat.common.Constants.CHALLENGE_SRC
import com.readingbat.common.Constants.GROUP_SRC
import com.readingbat.common.Constants.LANG_SRC
import com.readingbat.common.Constants.LIKE_DESC
import com.readingbat.common.Constants.MSG
import com.readingbat.common.Constants.RESP
import com.readingbat.common.Endpoints.CHALLENGE_ROOT
import com.readingbat.common.FormFields.CHALLENGE_ANSWERS_PARAM
import com.readingbat.common.FormFields.CHALLENGE_NAME_PARAM
import com.readingbat.common.FormFields.CORRECT_ANSWERS_PARAM
import com.readingbat.common.FormFields.GROUP_NAME_PARAM
import com.readingbat.common.FormFields.LANGUAGE_NAME_PARAM
import com.readingbat.common.FunctionInfo
import com.readingbat.common.KeyConstants.AUTH_KEY
import com.readingbat.common.KeyConstants.KEY_SEP
import com.readingbat.common.ParameterIds.DISLIKE_CLEAR
import com.readingbat.common.ParameterIds.DISLIKE_COLOR
import com.readingbat.common.ParameterIds.LIKE_CLEAR
import com.readingbat.common.ParameterIds.LIKE_COLOR
import com.readingbat.common.User
import com.readingbat.common.User.Companion.fetchUserDbmsIdFromCache
import com.readingbat.common.WsProtocol
import com.readingbat.common.nowInstant
import com.readingbat.dsl.ReadingBatContent
import com.readingbat.dsl.isDbmsEnabled
import com.readingbat.posts.AnswerStatus.CORRECT
import com.readingbat.posts.AnswerStatus.INCORRECT
import com.readingbat.posts.AnswerStatus.NOT_ANSWERED
import com.readingbat.server.ChallengeName
import com.readingbat.server.ChallengeName.Companion.getChallengeName
import com.readingbat.server.GroupName
import com.readingbat.server.GroupName.Companion.getGroupName
import com.readingbat.server.Invocation
import com.readingbat.server.LanguageName
import com.readingbat.server.LanguageName.Companion.getLanguageName
import com.readingbat.server.PageResult
import com.readingbat.server.ServerUtils.paramMap
import com.readingbat.server.UserAnswerHistoryTable
import com.readingbat.server.UserChallengeInfoTable
import com.readingbat.server.userAnswerHistoryIndex
import com.readingbat.server.userChallengeInfoIndex
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.ContentType
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingContext
import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.buildJsonArray
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

internal data class StudentInfo(val studentId: String, val firstName: String, val lastName: String)

@Suppress("unused")
internal data class ClassEnrollment(
  val sessionId: String,
  val students: List<StudentInfo> = emptyList(),
)

/** Result of checking a single invocation's user response against the expected answer. */
data class ChallengeResults(
  val invocation: Invocation,
  val userResponse: String = "",
  val answered: Boolean = false,
  val correct: Boolean = false,
  val hint: String = "",
)

@Serializable
internal data class LikeDislikeInfo(
  @SerialName(WsProtocol.USER_ID_FIELD) val userId: String,
  @SerialName(WsProtocol.LIKE_DISLIKE_FIELD) val likeDislike: String,
  @SerialName(WsProtocol.TYPE_FIELD) @Required val type: String = Constants.LIKE_DISLIKE_CODE,
)

@Suppress("unused")
@Serializable
internal data class DashboardInfo(
  @SerialName(WsProtocol.USER_ID_FIELD) val userId: String,
  @SerialName(WsProtocol.COMPLETE_FIELD) val complete: Boolean,
  @SerialName(WsProtocol.NUM_CORRECT_FIELD) val numCorrect: Int,
  @SerialName(WsProtocol.HISTORY_FIELD) val history: DashboardHistory,
)

@Serializable
internal data class DashboardHistory(
  @SerialName(WsProtocol.INVOCATION_FIELD) val invocation: String,
  @SerialName(WsProtocol.CORRECT_FIELD) val correct: Boolean = false,
  @SerialName(WsProtocol.ANSWERS_FIELD) val answers: String,
)

/** Tracks the answer history for a single invocation, including correctness and past attempts. */
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

/** Status of a user's answer for a single invocation: not answered, correct, or incorrect. */
enum class AnswerStatus(val value: Int) {
  NOT_ANSWERED(0),
  CORRECT(1),
  INCORRECT(2),
  ;

  companion object {
    fun Int.toAnswerStatus() = entries.firstOrNull { this == it.value } ?: error("Invalid AnswerStatus value: $this")
  }
}

enum class LikeDislike(val value: Short) {
  NONE(0),
  LIKE(1),
  DISLIKE(2),
}

internal class ChallengeNames(paramMap: Map<String, String>) {
  val languageName = LanguageName(paramMap[LANG_SRC] ?: error("Missing language"))
  val groupName = GroupName(paramMap[GROUP_SRC] ?: error("Missing group name"))
  val challengeName = ChallengeName(paramMap[CHALLENGE_SRC] ?: error("Missing challenge name"))

  fun md5() = md5Of(languageName, groupName, challengeName)

  fun md5(invocation: Invocation) = md5Of(languageName, groupName, challengeName, invocation)
}

/**
 * Handles challenge-related POST submissions including answer checking, answer history clearing,
 * and like/dislike actions.
 *
 * Answer submissions are validated against correct answers, persisted per-user via [UserAnswerQueue],
 * and published to enrolled class WebSocket listeners for real-time teacher dashboard updates.
 */
internal object ChallengePost {
  private val logger = KotlinLogging.logger {}

  /** Validates user-submitted answers against correct answers and returns JSON results. */
  suspend fun RoutingContext.checkAnswers(content: ReadingBatContent, user: User?) {
    val params = call.receiveParameters()
    val paramMap = params.entries().associate { it.key to it.value[0] }
    val names = ChallengeNames(paramMap)
    val challenge = content.findChallenge(names.languageName, names.groupName, names.challengeName)
    val funcInfo = challenge.functionInfo()
    val userResponses = params.entries().filter { it.key.startsWith(RESP) }

    logger.debug { "Found ${userResponses.size} user responses in $paramMap" }

    val results =
      userResponses.indices.map { i ->
        val userResponse = paramMap[RESP + i]?.trim() ?: error("Missing user response")
        funcInfo.checkResponse(i, userResponse)
      }

    if (isDbmsEnabled())
      saveChallengeAnswers(user, content, names, paramMap, funcInfo, userResponses, results)

    val answerMapping =
      buildJsonArray {
        results.forEach {
          val status =
            when {
              !it.answered -> NOT_ANSWERED.value
              it.correct -> CORRECT.value
              else -> INCORRECT.value
            }
          val hint = if (!it.answered || it.correct) "" else it.hint
          addJsonArray {
            add(JsonPrimitive(status))
            add(JsonPrimitive(hint))
          }
        }
      }
    call.respondText(answerMapping.toString(), ContentType.Application.Json)
  }

  private fun deleteFromTable(
    table: LongIdTable,
    userRefCol: Column<Long>,
    md5Col: Column<String>,
    type: String,
    id: String,
    md5Val: String,
  ) = when (type) {
    AUTH_KEY -> {
      transaction {
        table.deleteWhere { (userRefCol eq fetchUserDbmsIdFromCache(id)) and (md5Col eq md5Val) }
      }
    }

    else -> {
      error("Invalid type: $type")
    }
  }

  private fun deleteChallengeInfo(type: String, id: String, md5Val: String) =
    deleteFromTable(
      UserChallengeInfoTable,
      UserChallengeInfoTable.userRef,
      UserChallengeInfoTable.md5,
      type,
      id,
      md5Val,
    )

  private fun deleteAnswerHistory(type: String, id: String, md5Val: String) =
    deleteFromTable(
      UserAnswerHistoryTable,
      UserAnswerHistoryTable.userRef,
      UserAnswerHistoryTable.md5,
      type,
      id,
      md5Val,
    )

  private fun splitKeyAndDelete(key: String, label: String, user: User?, action: (String, String, String) -> Int) {
    if (key.isNotEmpty()) {
      val parts = key.split(KEY_SEP)
      if (parts.size >= 4) {
        val keyUserId = parts[2]
        if (user == null || user.userId != keyUserId) {
          logger.warn { "Authorization mismatch for $label: key userId=$keyUserId does not match authenticated user" }
          return
        }
        action(parts[1], keyUserId, parts[3])
          .also { logger.info { "Deleted $it $label for $key" } }
      } else {
        logger.warn { "Malformed $label: $key" }
      }
    }
  }

  /** Clears the answer history for a single challenge and redirects back to the challenge page. */
  suspend fun RoutingContext.clearChallengeAnswers(content: ReadingBatContent, user: User?): PageResult {
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
    splitKeyAndDelete(correctAnswersKey, "correctAnswers", user, ::deleteChallengeInfo)
    splitKeyAndDelete(challengeAnswersKey, "challengeAnswers", user, ::deleteAnswerHistory)

    user?.resetHistory(funcInfo, challenge, content.maxHistoryLength)

    return PageResult.Redirect("$path?$MSG=${"Answers cleared".encode()}")
  }

  /** Clears the answer history for all challenges in a group and redirects back to the group page. */
  suspend fun RoutingContext.clearGroupAnswers(content: ReadingBatContent, user: User?): PageResult {
    val parameters = call.receiveParameters()

    val languageName = parameters.getLanguageName(LANGUAGE_NAME_PARAM)
    val groupName = parameters.getGroupName(GROUP_NAME_PARAM)

    val correctJson = parameters[CORRECT_ANSWERS_PARAM] ?: ""
    val challengeJson = parameters[CHALLENGE_ANSWERS_PARAM] ?: ""

    val correctAnswersKeys = Json.decodeFromString<List<String>>(correctJson)
    val challengeAnswersKeys = Json.decodeFromString<List<String>>(challengeJson)

    val path = pathOf(CHALLENGE_ROOT, languageName, groupName)

    if (!isDbmsEnabled())
      return PageResult.Redirect("$path?$MSG=${"Database not enabled"}")

    correctAnswersKeys.forEach { splitKeyAndDelete(it, "correctAnswers", user, ::deleteChallengeInfo) }
    challengeAnswersKeys.forEach { splitKeyAndDelete(it, "challengeAnswers", user, ::deleteAnswerHistory) }

    if (user != null) {
      content.findGroup(languageName, groupName).challenges
        .forEach { challenge ->
          logger.info { "Clearing answers for challengeName ${challenge.challengeName}" }
          val funcInfo = challenge.functionInfo()
          user.resetHistory(funcInfo, challenge, content.maxHistoryLength)
        }
    }

    return PageResult.Redirect("$path?$MSG=${"Answers cleared".encode()}")
  }

  /** Processes a like/dislike toggle for a challenge and responds with the new like state. */
  suspend fun RoutingContext.likeDislike(user: User?) {
    val paramMap = call.paramMap()
    val names = ChallengeNames(paramMap)

    val likeArg = paramMap[LIKE_DESC]?.trim() ?: error("Missing like/dislike argument")

    val likeVal =
      when (likeArg) {
        LIKE_CLEAR -> LikeDislike.LIKE
        DISLIKE_CLEAR -> LikeDislike.DISLIKE
        LIKE_COLOR, DISLIKE_COLOR -> LikeDislike.NONE
        else -> error("Invalid like/dislike argument: $likeArg")
      }

    logger.debug { "Like/dislike arg -- response: $likeArg -- $likeVal" }

    saveLikeDislike(user, names, likeVal.value.toInt())

    call.respondText(likeVal.value.toString())
  }

  private suspend fun saveChallengeAnswers(
    user: User?,
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
    if (user != null) {
      // Serialize DB writes per user via a coroutine channel to prevent
      // concurrent requests from overwriting each other's incorrectAttempts counts.
      val historyPairs =
        UserAnswerQueue.submitForUser(user.userDbmsId) {
          transaction {
            val pairs =
              results.map { result ->
                val historyMd5 = names.md5(result.invocation)
                val history = user.answerHistoryInTransaction(historyMd5, result.invocation)

                when {
                  !result.answered -> history.markUnanswered()
                  result.correct -> history.markCorrect(result.userResponse)
                  else -> history.markIncorrect(result.userResponse)
                }

                historyMd5 to history
              }

            with(UserChallengeInfoTable) {
              upsert(conflictIndex = userChallengeInfoIndex) { row ->
                row[userRef] = user.userDbmsId
                row[md5] = challengeMd5
                row[updated] = nowInstant()
                row[allCorrect] = complete
                row[answersJson] = invokeStr
              }
            }

            // Save the history of each answer on a per-invocation basis
            pairs.forEach { (historyMd5, history) ->
              with(UserAnswerHistoryTable) {
                upsert(conflictIndex = userAnswerHistoryIndex) { row ->
                  row[userRef] = user.userDbmsId
                  row[md5] = historyMd5
                  row[invocation] = history.invocation.value
                  row[updated] = nowInstant()
                  row[correct] = history.correct
                  row[incorrectAttempts] = history.incorrectAttempts
                  row[historyJson] = Json.encodeToString(history.answers)
                }
              }
            }

            pairs
          }
        }

      // This is done outside the transaction
      if (shouldPublish) {
        historyPairs.forEach { (_, history) ->
          AnswerPublisher.publishAnswers(user, challengeMd5, content.maxHistoryLength, complete, numCorrect, history)
        }
      }
    }
  }

  private suspend fun saveLikeDislike(
    user: User?,
    names: ChallengeNames,
    likeDislikeVal: Int,
  ) {
    if (user != null) {
      val challengeMd5 = names.md5()
      transaction {
        with(UserChallengeInfoTable) {
          upsert(conflictIndex = userChallengeInfoIndex) { row ->
            row[userRef] = user.userDbmsId
            row[md5] = challengeMd5
            row[updated] = nowInstant()
            row[likeDislike] = likeDislikeVal.toShort()
          }
        }
      }
      if (user.shouldPublish())
        AnswerPublisher.publishLikeDislike(user, challengeMd5, likeDislikeVal)
    }
  }
}
