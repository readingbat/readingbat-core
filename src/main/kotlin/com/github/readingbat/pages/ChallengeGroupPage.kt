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
import com.github.pambrose.common.util.isNotNull
import com.github.pambrose.common.util.pluralize
import com.github.readingbat.common.BrowserSession
import com.github.readingbat.common.CSSNames.FUNC_ITEM
import com.github.readingbat.common.ClassCode
import com.github.readingbat.common.CommonUtils.pathOf
import com.github.readingbat.common.Constants.CHALLENGE_ROOT
import com.github.readingbat.common.Constants.COLUMN_CNT
import com.github.readingbat.common.Constants.GREEN_CHECK
import com.github.readingbat.common.Constants.MSG
import com.github.readingbat.common.Constants.STATIC_ROOT
import com.github.readingbat.common.Constants.WHITE_CHECK
import com.github.readingbat.common.Endpoints.CHALLENGE_GROUP_ENDPOINT
import com.github.readingbat.common.Endpoints.CLEAR_GROUP_ANSWERS_ENDPOINT
import com.github.readingbat.common.FormFields.CHALLENGE_ANSWERS_KEY
import com.github.readingbat.common.FormFields.GROUP_NAME_KEY
import com.github.readingbat.common.FormFields.LANGUAGE_NAME_KEY
import com.github.readingbat.common.KeyConstants.CORRECT_ANSWERS_KEY
import com.github.readingbat.common.Message
import com.github.readingbat.common.User
import com.github.readingbat.common.User.Companion.challengeAnswersKey
import com.github.readingbat.common.User.Companion.correctAnswersKey
import com.github.readingbat.common.User.Companion.fetchActiveClassCode
import com.github.readingbat.common.User.Companion.gson
import com.github.readingbat.dsl.Challenge
import com.github.readingbat.dsl.ChallengeGroup
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.pages.PageUtils.backLink
import com.github.readingbat.pages.PageUtils.bodyHeader
import com.github.readingbat.pages.PageUtils.encodeUriElems
import com.github.readingbat.pages.PageUtils.headDefault
import com.github.readingbat.pages.PageUtils.rawHtml
import com.github.readingbat.server.GroupName
import com.github.readingbat.server.LanguageName
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.ServerUtils.queryParam
import com.github.readingbat.server.ServerUtils.rows
import io.ktor.application.*
import io.ktor.sessions.*
import kotlinx.html.BODY
import kotlinx.html.FormMethod
import kotlinx.html.InputType
import kotlinx.html.TR
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.id
import kotlinx.html.img
import kotlinx.html.input
import kotlinx.html.onSubmit
import kotlinx.html.p
import kotlinx.html.script
import kotlinx.html.span
import kotlinx.html.stream.createHTML
import kotlinx.html.style
import kotlinx.html.table
import kotlinx.html.td
import kotlinx.html.tr
import mu.KLogging
import redis.clients.jedis.Jedis

internal object ChallengeGroupPage : KLogging() {

  fun Challenge.isCorrect(user: User?, browserSession: BrowserSession?, redis: Jedis?): Boolean {
    val correctAnswersKey = user.correctAnswersKey(browserSession, languageName, groupName, challengeName)
    return if (correctAnswersKey.isNotEmpty()) redis?.get(correctAnswersKey)?.toBoolean() == true else false
  }

  fun PipelineCall.challengeGroupPage(content: ReadingBatContent,
                                      user: User?,
                                      challengeGroup: ChallengeGroup<*>,
                                      loginAttempt: Boolean,
                                      redis: Jedis?) =
    createHTML()
      .html {
        val browserSession = call.sessions.get<BrowserSession>()
        val languageType = challengeGroup.languageType
        val languageName = languageType.languageName
        val groupName = challengeGroup.groupName
        val challenges = challengeGroup.challenges
        val loginPath = pathOf(CHALLENGE_ROOT, languageName, groupName)
        val activeClassCode = user.fetchActiveClassCode(redis)
        val enrollees = activeClassCode.fetchEnrollees(redis)

        fun TR.displayFunctionCall(user: User?, challenge: Challenge, redis: Jedis?) {
          val challengeName = challenge.challengeName
          val allCorrect = challenge.isCorrect(user, browserSession, redis)

          td(classes = FUNC_ITEM) {
            if (activeClassCode.isNotEnabled)
              img { src = "$STATIC_ROOT/${if (allCorrect) GREEN_CHECK else WHITE_CHECK}" }

            a {
              style = "font-Size:110%; padding-left:2px;"
              href = pathOf(CHALLENGE_ROOT, languageName, groupName, challengeName)
              +challengeName.value
            }

            // This element is dynamically populated by the websocket data
            if (enrollees.isNotEmpty())
              span { id = challengeName.value; +"" }
          }
        }

        head { headDefault(content) }

        body {
          bodyHeader(content,
                     user,
                     languageType,
                     loginAttempt,
                     loginPath,
                     false,
                     activeClassCode,
                     redis,
                     Message(queryParam(MSG)))

          h2 { +groupName.value.decode() }

          if (activeClassCode.isEnabled)
            displayClassDescription(activeClassCode, enrollees, redis)

          if (enrollees.isNotEmpty())
            p { +"(# of questions | # that started | # completed | Avg correct | Incorrect attempts | Likes/Dislikes)" }

          table {
            val size = challenges.size
            val rows = size.rows(COLUMN_CNT)

            //val width = if (enrollees.isNotEmpty()) 1200 else 800
            //style = "width:${width}px"
            style = "width:100%"

            repeat(rows) { i ->
              tr {
                style = "height:30"
                challenges.apply {
                  displayFunctionCall(user, elementAt(i), redis)
                  elementAtOrNull(i + rows)?.also { displayFunctionCall(user, it, redis) } ?: td {}
                  elementAtOrNull(i + (2 * rows))?.also { displayFunctionCall(user, it, redis) } ?: td {}
                }
              }
            }
          }

          if (redis.isNotNull() && activeClassCode.isNotEnabled && challenges.isNotEmpty())
            clearGroupAnswerHistoryOption(user, browserSession, languageName, groupName, challenges)

          backLink(CHALLENGE_ROOT, languageName.value)

          if (enrollees.isNotEmpty())
            enableWebSockets(languageName, groupName, activeClassCode)
        }
      }

  fun BODY.displayClassDescription(activeClassCode: ClassCode, enrollees: List<User>, redis: Jedis?) {
    val classDesc = if (redis.isNotNull()) activeClassCode.fetchClassDesc(redis, true) else "Description unavailable"
    val studentCount = if (enrollees.isEmpty()) "No" else enrollees.count().toString()
    h3 {
      style = "margin-left: 5px; color: ${ChallengePage.headerColor}"
      +"$studentCount ${"student".pluralize(enrollees.count())} enrolled in $classDesc [$activeClassCode]"
    }
  }

  private fun BODY.enableWebSockets(languageName: LanguageName, groupName: GroupName, classCode: ClassCode) {
    script {
      rawHtml(
        """
          
          var wshost = location.origin;
          if (wshost.startsWith('https:'))
            wshost = wshost.replace(/^https:/, 'wss:');
          else
            wshost = wshost.replace(/^http:/, 'ws:');

          var wsurl = wshost + '${CHALLENGE_GROUP_ENDPOINT}/' + ${encodeUriElems(languageName, groupName, classCode)};
          var ws = new WebSocket(wsurl);

          ws.onopen = function (event) {
            ws.send("$classCode"); 
          };
          
          ws.onmessage = function (event) {
            //console.log(event.data);
            var obj = JSON.parse(event.data)
            document.getElementById(obj.challengeName).innerHTML = obj.msg;
          };
        """.trimIndent())
    }
  }

  private fun BODY.clearGroupAnswerHistoryOption(user: User?,
                                                 browserSession: BrowserSession?,
                                                 languageName: LanguageName,
                                                 groupName: GroupName,
                                                 challenges: List<Challenge>) {

    val correctAnswersKeys = challenges.map { user.correctAnswersKey(browserSession, it) }
    val challengeAnswerKeys = challenges.map { user.challengeAnswersKey(browserSession, it) }
    val correctAnswersKey = gson.toJson(correctAnswersKeys)
    val challengeAnswersKey = gson.toJson(challengeAnswerKeys)

    p {
      form {
        style = "margin:0;"
        action = CLEAR_GROUP_ANSWERS_ENDPOINT
        method = FormMethod.post
        onSubmit = """return confirm('Are you sure you want to clear your previous answers for group "$groupName"?');"""
        input { type = InputType.hidden; name = LANGUAGE_NAME_KEY; value = languageName.value }
        input { type = InputType.hidden; name = GROUP_NAME_KEY; value = groupName.value }
        input { type = InputType.hidden; name = CORRECT_ANSWERS_KEY; value = correctAnswersKey }
        input { type = InputType.hidden; name = CHALLENGE_ANSWERS_KEY; value = challengeAnswersKey }
        input {
          style = "vertical-align:middle; margin-top:1; margin-bottom:0;"
          type = InputType.submit; value = "Clear answer history"
        }
      }
    }
  }
}