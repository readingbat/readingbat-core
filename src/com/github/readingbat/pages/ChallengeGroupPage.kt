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

package com.github.readingbat.pages

import com.github.pambrose.common.util.decode
import com.github.pambrose.common.util.join
import com.github.readingbat.PipelineCall
import com.github.readingbat.config.fetchPrincipal
import com.github.readingbat.dsl.Challenge
import com.github.readingbat.dsl.ChallengeGroup
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.BrowserSession
import com.github.readingbat.misc.CSSNames.funcItem
import com.github.readingbat.misc.CSSNames.tabs
import com.github.readingbat.misc.Constants.CHALLENGE_ROOT
import com.github.readingbat.misc.Constants.GREEN_CHECK
import com.github.readingbat.misc.Constants.STATIC_ROOT
import com.github.readingbat.misc.Constants.WHITE_CHECK
import com.github.readingbat.misc.RedisUtils.withRedisPool
import com.github.readingbat.misc.UserId
import com.github.readingbat.posts.lookupUserId
import io.ktor.application.call
import io.ktor.sessions.get
import io.ktor.sessions.sessions
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import redis.clients.jedis.Jedis

internal fun Challenge.isCorrect(redis: Jedis?, userId: UserId?, browserSession: BrowserSession?): Boolean {
  val correctAnswersKey =
    userId?.correctAnswersKey(languageName, groupName, challengeName)
      ?: browserSession?.correctAnswersKey(languageName, groupName, challengeName)
      ?: ""
  return if (correctAnswersKey.isNotEmpty()) redis?.get(correctAnswersKey)?.toBoolean() == true else false
}

internal fun PipelineCall.challengeGroupPage(content: ReadingBatContent,
                                             challengeGroup: ChallengeGroup<*>,
                                             loginAttempt: Boolean) =
  createHTML()
    .html {
      val languageType = challengeGroup.languageType
      val languageName = languageType.lowerName
      val groupName = challengeGroup.groupName
      val challenges = challengeGroup.challenges
      val principal = fetchPrincipal(loginAttempt)
      val browserSession = call.sessions.get<BrowserSession>()
      val loginPath = listOf(languageName, groupName).join()

      fun TR.funcCall(redis: Jedis?, userId: UserId?, challenge: Challenge) {
        val challengeName = challenge.challengeName
        val allCorrect = challenge.isCorrect(redis, userId, browserSession)

        td(classes = funcItem) {
          img { src = "$STATIC_ROOT/${if (allCorrect) GREEN_CHECK else WHITE_CHECK}" }
          a {
            style = "font-Size:110%; padding-left:2px;"
            href = listOf(CHALLENGE_ROOT, languageName, groupName, challengeName).join()
            +challengeName
          }
        }
      }

      head {
        headDefault(content)
      }

      body {
        bodyHeader(principal, loginAttempt, content, languageType, loginPath)

        div(classes = tabs) {

          h2 { +groupName.decode() }

          table {
            val cols = 3
            val size = challenges.size
            val rows = size.rows(cols)

            withRedisPool { redis ->
              val userId = lookupUserId(redis, principal)

              (0 until rows).forEach { i ->
                tr {
                  style = "height:30"
                  challenges.apply {
                    elementAt(i).also { funcCall(redis, userId, it) }
                    elementAtOrNull(i + rows)?.also { funcCall(redis, userId, it) } ?: td {}
                    elementAtOrNull(i + (2 * rows))?.also { funcCall(redis, userId, it) } ?: td {}
                  }
                }
              }
            }
          }
        }

        backLink(CHALLENGE_ROOT, languageName)
      }
    }