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
import com.github.pambrose.common.util.pluralize
import com.github.readingbat.dsl.Challenge
import com.github.readingbat.dsl.ChallengeGroup
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.BrowserSession
import com.github.readingbat.misc.CSSNames.FUNC_ITEM
import com.github.readingbat.misc.ClassCode.Companion.EMPTY_CLASS_CODE
import com.github.readingbat.misc.Constants.CHALLENGE_ROOT
import com.github.readingbat.misc.Constants.GREEN_CHECK
import com.github.readingbat.misc.Constants.MSG
import com.github.readingbat.misc.Constants.STATIC_ROOT
import com.github.readingbat.misc.Constants.WHITE_CHECK
import com.github.readingbat.misc.PageUtils.pathOf
import com.github.readingbat.misc.User
import com.github.readingbat.misc.User.Companion.correctAnswersKey
import com.github.readingbat.misc.User.Companion.gson
import com.github.readingbat.pages.PageCommon.backLink
import com.github.readingbat.pages.PageCommon.bodyHeader
import com.github.readingbat.pages.PageCommon.headDefault
import com.github.readingbat.pages.PageCommon.rows
import com.github.readingbat.posts.ChallengeHistory
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.ServerUtils.fetchPrincipal
import com.github.readingbat.server.ServerUtils.queryParam
import io.ktor.application.call
import io.ktor.sessions.get
import io.ktor.sessions.sessions
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import mu.KLogging
import redis.clients.jedis.Jedis

internal object ChallengeGroupPage : KLogging() {

  fun Challenge.isCorrect(redis: Jedis?, user: User?, browserSession: BrowserSession?): Boolean {
    val correctAnswersKey = correctAnswersKey(user, browserSession, languageName, groupName, challengeName)
    return if (correctAnswersKey.isNotEmpty()) redis?.get(correctAnswersKey)?.toBoolean() == true else false
  }

  fun PipelineCall.challengeGroupPage(content: ReadingBatContent,
                                      redis: Jedis?,
                                      challengeGroup: ChallengeGroup<*>,
                                      loginAttempt: Boolean) =
    createHTML()
      .html {
        val principal = fetchPrincipal(loginAttempt)
        val browserSession = call.sessions.get<BrowserSession>()
        val languageType = challengeGroup.languageType
        val languageName = languageType.languageName
        val groupName = challengeGroup.groupName
        val challenges = challengeGroup.challenges
        val loginPath = pathOf(CHALLENGE_ROOT, languageName.value, groupName.value)
        val user = principal?.toUser()
        val activeClassCode = user?.fetchActiveClassCode(redis) ?: EMPTY_CLASS_CODE
        val enrollees =
          if (redis != null && activeClassCode.isEnabled)
            activeClassCode.fetchEnrollees(redis)
          else
            emptyList()

        fun TR.funcCall(redis: Jedis?, user: User?, challenge: Challenge) {
          val challengeName = challenge.challengeName
          val allCorrect = challenge.isCorrect(redis, user, browserSession)

          td(classes = FUNC_ITEM) {
            if (activeClassCode.isNotEnabled)
              img { src = "$STATIC_ROOT/${if (allCorrect) GREEN_CHECK else WHITE_CHECK}" }
            a {
              style = "font-Size:110%; padding-left:2px;"
              href = pathOf(CHALLENGE_ROOT, languageName.value, groupName.value, challengeName.value)
              +challengeName.value
            }

            if (redis != null && activeClassCode.isEnabled && enrollees.isNotEmpty()) {
              val funcInfo = challenge.funcInfo(content)
              val numCalls = funcInfo.invocations.size
              var totAttemptedAtLeastOne = 0
              var totAllCorrect = 0
              var totCorrect = 0

              enrollees.forEach { enrollee ->
                var attempted = 0
                var numCorrect = 0

                funcInfo.invocations
                  .forEach { invocation ->
                    val answerHistoryKey =
                      enrollee.answerHistoryKey(languageName, groupName, challengeName, invocation)
                    if (redis.exists(answerHistoryKey)) {
                      attempted++
                      val json = redis[answerHistoryKey]
                      val history = gson.fromJson(json, ChallengeHistory::class.java) ?: ChallengeHistory(invocation)
                      if (history.correct)
                        numCorrect++
                    }
                  }

                if (attempted > 0)
                  totAttemptedAtLeastOne++

                if (numCorrect == numCalls)
                  totAllCorrect++

                totCorrect += numCorrect
              }

              val avgCorrect = if (totAttemptedAtLeastOne > 0) totCorrect / totAttemptedAtLeastOne.toFloat() else 0.0f

              +" ($numCalls | $totAttemptedAtLeastOne | $totAllCorrect | ${"%.1f".format(avgCorrect)})"
            }
          }
        }

        head { headDefault(content) }

        body {
          bodyHeader(redis, principal, loginAttempt, content, languageType, loginPath, false, queryParam(MSG) ?: "")

          h2 { +groupName.value.decode() }

          if (redis != null && activeClassCode.isEnabled) {
            val classDesc = activeClassCode.fetchClassDesc(redis)
            val studentCount = if (enrollees.isEmpty()) "No" else enrollees.count().toString()
            h3 {
              style = "margin-left: 5px; color: ${ChallengePage.headerColor}"
              +"$studentCount ${"student".pluralize(enrollees.count())} enrolled in $classDesc [$activeClassCode]"
            }
            p { +"(# of questions | # students that started | # completed | Avg correct answers)" }
          }

          table {
            val cols = 3
            val size = challenges.size
            val rows = size.rows(cols)

            (0 until rows).forEach { i ->
              tr {
                style = "height:30"
                challenges.apply {
                  elementAt(i).also { funcCall(redis, user, it) }
                  elementAtOrNull(i + rows)?.also { funcCall(redis, user, it) } ?: td {}
                  elementAtOrNull(i + (2 * rows))?.also { funcCall(redis, user, it) } ?: td {}
                }
              }
            }
          }

          backLink(CHALLENGE_ROOT, languageName.value)
        }
      }
}