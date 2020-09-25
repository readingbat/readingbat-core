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

import com.github.readingbat.common.CommonUtils.keyOf
import com.github.readingbat.common.CommonUtils.md5Of
import com.github.readingbat.common.KeyConstants.ANSWER_HISTORY_KEY
import com.github.readingbat.common.KeyConstants.CHALLENGE_ANSWERS_KEY
import com.github.readingbat.common.KeyConstants.CORRECT_ANSWERS_KEY
import com.github.readingbat.common.KeyConstants.LIKE_DISLIKE_KEY
import com.github.readingbat.common.KeyConstants.NO_AUTH_KEY
import com.github.readingbat.common.User.Companion.gson
import com.github.readingbat.dsl.InvalidConfigurationException
import com.github.readingbat.posts.ChallengeHistory
import com.github.readingbat.posts.ChallengeNames
import com.github.readingbat.server.ChallengeName
import com.github.readingbat.server.GroupName
import com.github.readingbat.server.Invocation
import com.github.readingbat.server.LanguageName
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.sessions.*
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import java.time.Instant

internal data class UserPrincipal(val userId: String, val created: Long = Instant.now().toEpochMilli()) : Principal

internal data class BrowserSession(val id: String, val created: Long = Instant.now().toEpochMilli()) {

  internal val sessionDbmsId: Long get() = this.id.sessionDbmsId

  fun correctAnswersKey(names: ChallengeNames) =
    correctAnswersKey(names.languageName, names.groupName, names.challengeName)

  fun correctAnswersKey(languageName: LanguageName, groupName: GroupName, challengeName: ChallengeName) =
    keyOf(CORRECT_ANSWERS_KEY, NO_AUTH_KEY, id, md5Of(languageName, groupName, challengeName))

  fun likeDislikeKey(names: ChallengeNames) =
    likeDislikeKey(names.languageName, names.groupName, names.challengeName)

  fun likeDislikeKey(languageName: LanguageName, groupName: GroupName, challengeName: ChallengeName) =
    keyOf(LIKE_DISLIKE_KEY, NO_AUTH_KEY, id, md5Of(languageName, groupName, challengeName))

  fun challengeAnswerKey(names: ChallengeNames) =
    challengeAnswerKey(names.languageName, names.groupName, names.challengeName)

  fun challengeAnswerKey(languageName: LanguageName, groupName: GroupName, challengeName: ChallengeName) =
    keyOf(CHALLENGE_ANSWERS_KEY, NO_AUTH_KEY, id, md5Of(languageName, groupName, challengeName))

  fun answerHistoryKey(names: ChallengeNames, invocation: Invocation) =
    answerHistoryKey(names.languageName, names.groupName, names.challengeName, invocation)

  private fun answerHistoryKey(languageName: LanguageName,
                               groupName: GroupName,
                               challengeName: ChallengeName,
                               invocation: Invocation) =
    keyOf(ANSWER_HISTORY_KEY, NO_AUTH_KEY, id, md5Of(languageName, groupName, challengeName, invocation))

  fun answerHistory(md5: String, invocation: Invocation) =
    SessionAnswerHistory
      .slice(SessionAnswerHistory.invocation,
             SessionAnswerHistory.correct,
             SessionAnswerHistory.incorrectAttempts,
             SessionAnswerHistory.historyJson)
      .select { (SessionAnswerHistory.sessionRef eq sessionDbmsId) and (SessionAnswerHistory.md5 eq md5) }
      .map {
        val json = it[SessionAnswerHistory.historyJson]
        val history =
          mutableListOf<String>().apply { addAll(gson.fromJson(json, List::class.java) as List<String>) }

        ChallengeHistory(Invocation(it[SessionAnswerHistory.invocation]),
                         it[SessionAnswerHistory.correct],
                         it[SessionAnswerHistory.incorrectAttempts].toInt(),
                         history)
      }
      .firstOrNull() ?: ChallengeHistory(invocation)

}

internal val String.sessionDbmsId: Long
  get() =
    BrowserSessions
      .slice(BrowserSessions.id)
      .select { BrowserSessions.session_id eq this@sessionDbmsId }
      .map { it[BrowserSessions.id].value }
      .firstOrNull() ?: throw InvalidConfigurationException("Invalid browser session id: ${this@sessionDbmsId}")


internal val ApplicationCall.browserSession get() = sessions.get<BrowserSession>()

internal val ApplicationCall.userPrincipal get() = sessions.get<UserPrincipal>()
