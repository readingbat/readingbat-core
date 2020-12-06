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

import com.github.pambrose.common.util.md5Of
import com.github.readingbat.common.KeyConstants.CHALLENGE_ANSWERS_KEY
import com.github.readingbat.common.KeyConstants.CORRECT_ANSWERS_KEY
import com.github.readingbat.common.KeyConstants.NO_AUTH_KEY
import com.github.readingbat.common.KeyConstants.keyOf
import com.github.readingbat.dsl.Challenge
import com.github.readingbat.dsl.MissingBrowserSessionException
import com.github.readingbat.posts.ChallengeHistory
import com.github.readingbat.server.BrowserSessions
import com.github.readingbat.server.ChallengeName
import com.github.readingbat.server.GroupName
import com.github.readingbat.server.Invocation
import com.github.readingbat.server.LanguageName
import com.github.readingbat.server.SessionAnswerHistory
import com.github.readingbat.server.SessionChallengeInfo
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.sessions.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import mu.KLogging
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

internal data class UserPrincipal(val userId: String, val created: Long = Instant.now().toEpochMilli()) : Principal

/*internal*/ data class BrowserSession(val id: String, val created: Long = Instant.now().toEpochMilli()) {

  fun sessionDbmsId() =
    try {
      querySessionDbmsId(id)
    } catch (e: MissingBrowserSessionException) {
      logger.debug { "Creating BrowserSession for ${e.message}" }
      createBrowserSession(id)
    }

  fun correctAnswersKey(languageName: LanguageName, groupName: GroupName, challengeName: ChallengeName) =
    keyOf(CORRECT_ANSWERS_KEY, NO_AUTH_KEY, id, md5Of(languageName, groupName, challengeName))

  fun challengeAnswerKey(languageName: LanguageName, groupName: GroupName, challengeName: ChallengeName) =
    keyOf(CHALLENGE_ANSWERS_KEY, NO_AUTH_KEY, id, md5Of(languageName, groupName, challengeName))

  fun likeDislike(challenge: Challenge) =
    transaction {
      val likeDislike = SessionChallengeInfo.likeDislike
      val sessionRef = SessionChallengeInfo.sessionRef
      val md5 = SessionChallengeInfo.md5
      SessionChallengeInfo
        .slice(likeDislike)
        .select { (sessionRef eq sessionDbmsId()) and (md5 eq challenge.md5()) }
        .map { it[likeDislike].toInt() }
        .firstOrNull() ?: 0
    }

  fun answerHistory(md5: String, invocation: Invocation) =
    SessionAnswerHistory
      .slice(SessionAnswerHistory.invocation,
             SessionAnswerHistory.correct,
             SessionAnswerHistory.incorrectAttempts,
             SessionAnswerHistory.historyJson)
      .select { (SessionAnswerHistory.sessionRef eq sessionDbmsId()) and (SessionAnswerHistory.md5 eq md5) }
      .map {
        val json = it[SessionAnswerHistory.historyJson]
        val history = Json.decodeFromString<List<String>>(json).toMutableList()
        ChallengeHistory(Invocation(it[SessionAnswerHistory.invocation]),
                         it[SessionAnswerHistory.correct],
                         it[SessionAnswerHistory.incorrectAttempts].toInt(),
                         history)
      }
      .firstOrNull() ?: ChallengeHistory(invocation)

  companion object : KLogging() {
    fun createBrowserSession(id: String) =
      BrowserSessions
        .insertAndGetId { row ->
          row[sessionId] = id
        }.value

    fun querySessionDbmsId(id: String) =
      transaction {
        BrowserSessions
          .slice(BrowserSessions.id)
          .select { BrowserSessions.sessionId eq id }
          .map { it[BrowserSessions.id].value }
          .firstOrNull() ?: throw MissingBrowserSessionException(id)
      }
  }
}

internal val ApplicationCall.browserSession get() = sessions.get<BrowserSession>()

internal val ApplicationCall.userPrincipal get() = sessions.get<UserPrincipal>()