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

package com.github.readingbat.common

import com.github.pambrose.common.util.md5Of
import com.github.readingbat.common.KeyConstants.CHALLENGE_ANSWERS_KEY
import com.github.readingbat.common.KeyConstants.CORRECT_ANSWERS_KEY
import com.github.readingbat.common.KeyConstants.NO_AUTH_KEY
import com.github.readingbat.common.KeyConstants.keyOf
import com.github.readingbat.dsl.MissingBrowserSessionException
import com.github.readingbat.dsl.challenge.Challenge
import com.github.readingbat.posts.ChallengeHistory
import com.github.readingbat.server.BrowserSessionsTable
import com.github.readingbat.server.ChallengeName
import com.github.readingbat.server.GroupName
import com.github.readingbat.server.Invocation
import com.github.readingbat.server.LanguageName
import com.github.readingbat.server.SessionAnswerHistoryTable
import com.github.readingbat.server.SessionChallengeInfoTable
import com.pambrose.common.exposed.readonlyTx
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.ApplicationCall
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json.Default.decodeFromString
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

@Serializable
internal data class UserPrincipal(val userId: String, val created: Long = Instant.now().toEpochMilli())

@Serializable
data class BrowserSession(val id: String, val created: Long = Instant.now().toEpochMilli()) {
  fun queryOrCreateSessionDbmsId() =
    try {
      querySessionDbmsId(id)
    } catch (e: MissingBrowserSessionException) {
      logger.info { "Creating BrowserSession in sessionDbmsId() - ${e.message}" }
      createBrowserSession(id)
    }

  fun correctAnswersKey(languageName: LanguageName, groupName: GroupName, challengeName: ChallengeName) =
    keyOf(CORRECT_ANSWERS_KEY, NO_AUTH_KEY, id, md5Of(languageName, groupName, challengeName))

  fun challengeAnswerKey(languageName: LanguageName, groupName: GroupName, challengeName: ChallengeName) =
    keyOf(CHALLENGE_ANSWERS_KEY, NO_AUTH_KEY, id, md5Of(languageName, groupName, challengeName))

  fun likeDislike(challenge: Challenge) =
    transaction {
      with(SessionChallengeInfoTable) {
        select(likeDislike)
          .where { (sessionRef eq queryOrCreateSessionDbmsId()) and (md5 eq challenge.md5()) }
          .map { it[likeDislike].toInt() }
          .firstOrNull() ?: 0
      }
    }

  fun answerHistory(md5Val: String, invocationVal: Invocation) =
    with(SessionAnswerHistoryTable) {
      select(invocation, correct, incorrectAttempts, historyJson)
        .where { (sessionRef eq queryOrCreateSessionDbmsId()) and (md5 eq md5Val) }
        .map {
          val json = it[historyJson]
          val history = decodeFromString<List<String>>(json).toMutableList()
          ChallengeHistory(
            Invocation(it[invocation]),
            it[correct],
            it[incorrectAttempts].toInt(),
            history,
          )
        }
    }.firstOrNull() ?: ChallengeHistory(invocationVal)

  companion object {
    private val logger = KotlinLogging.logger {}

    fun createBrowserSession(id: String) =
      with(BrowserSessionsTable) {
        insertAndGetId { row -> row[sessionId] = id }.value
      }

    fun findOrCreateSessionDbmsId(id: String, createIfMissing: Boolean) =
      try {
        querySessionDbmsId(id)
      } catch (e: MissingBrowserSessionException) {
        if (createIfMissing) {
          logger.info { "Creating BrowserSession in findSessionDbmsId() - ${e.message}" }
          createBrowserSession(id)
        } else {
          -1
        }
      }

    fun querySessionDbmsId(idVal: String) =
      readonlyTx {
        with(BrowserSessionsTable) {
          select(id)
            .where { sessionId eq idVal }
            .map { it[id].value }
            .firstOrNull() ?: throw MissingBrowserSessionException(idVal)
        }
      }
  }
}

internal val ApplicationCall.browserSession get() = sessions.get<BrowserSession>()

internal val ApplicationCall.userPrincipal get() = sessions.get<UserPrincipal>()
