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
import com.github.readingbat.misc.ClassCode
import com.github.readingbat.misc.Constants.CHALLENGE_ROOT
import com.github.readingbat.misc.Constants.GREEN_CHECK
import com.github.readingbat.misc.Constants.MSG
import com.github.readingbat.misc.Constants.STATIC_ROOT
import com.github.readingbat.misc.Constants.WHITE_CHECK
import com.github.readingbat.misc.Endpoints.CHALLENGE_GROUP_ENDPOINT
import com.github.readingbat.misc.Endpoints.CLEAR_GROUP_ANSWERS_ENDPOINT
import com.github.readingbat.misc.FormFields.CHALLENGE_ANSWERS_KEY
import com.github.readingbat.misc.FormFields.GROUP_NAME_KEY
import com.github.readingbat.misc.FormFields.LANGUAGE_NAME_KEY
import com.github.readingbat.misc.PageUtils.pathOf
import com.github.readingbat.misc.User
import com.github.readingbat.misc.User.Companion.challengeAnswersKey
import com.github.readingbat.misc.User.Companion.correctAnswersKey
import com.github.readingbat.misc.User.Companion.fetchActiveClassCode
import com.github.readingbat.misc.User.Companion.gson
import com.github.readingbat.pages.PageCommon.backLink
import com.github.readingbat.pages.PageCommon.bodyHeader
import com.github.readingbat.pages.PageCommon.headDefault
import com.github.readingbat.pages.PageCommon.rawHtml
import com.github.readingbat.pages.PageCommon.rows
import com.github.readingbat.server.GroupName
import com.github.readingbat.server.LanguageName
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
    val correctAnswersKey = user.correctAnswersKey(browserSession, languageName, groupName, challengeName)
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
        val loginPath = pathOf(CHALLENGE_ROOT, languageName, groupName)
        val user = principal?.toUser()
        val activeClassCode = user.fetchActiveClassCode(redis)
        val enrollees = activeClassCode.fetchEnrollees(redis)

        fun TR.funcCall(redis: Jedis?, user: User?, challenge: Challenge) {
          val challengeName = challenge.challengeName
          val allCorrect = challenge.isCorrect(redis, user, browserSession)

          td(classes = FUNC_ITEM) {
            if (activeClassCode.isStudentMode)
              img { src = "$STATIC_ROOT/${if (allCorrect) GREEN_CHECK else WHITE_CHECK}" }
            a {
              style = "font-Size:110%; padding-left:2px;"
              href = pathOf(CHALLENGE_ROOT, languageName.value, groupName, challengeName)
              +challengeName.value
            }

            if (enrollees.isNotEmpty())
              span { id = challengeName.value; +"" }
          }
        }

        head { headDefault(content) }

        body {
          bodyHeader(redis, principal, loginAttempt, content, languageType, loginPath, false, queryParam(MSG) ?: "")

          h2 { +groupName.value.decode() }

          if (activeClassCode.isTeacherMode)
            displayClassDescription(activeClassCode, enrollees, redis)

          if (enrollees.isNotEmpty())
            p { +"(# of questions | # students that started | # completed | Avg correct answers)" }

          table {
            val cols = 3
            val size = challenges.size
            val rows = size.rows(cols)
            val width = if (enrollees.isNotEmpty()) 1200 else 800
            style = "width:${width}px"

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

          if (activeClassCode.isStudentMode)
            clearGroupAnswerHistory(user, browserSession, languageName, groupName, challenges)

          backLink(CHALLENGE_ROOT, languageName.value)

          if (enrollees.isNotEmpty())
            addWebSockets(content, languageName, groupName, activeClassCode)
        }
      }

  fun BODY.displayClassDescription(activeClassCode: ClassCode,
                                   enrollees: List<User>,
                                   redis: Jedis?) {
    val classDesc = if (redis != null) activeClassCode.fetchClassDesc(redis) else "Description unavailable"
    val studentCount = if (enrollees.isEmpty()) "No" else enrollees.count().toString()
    h3 {
      style = "margin-left: 5px; color: ${ChallengePage.headerColor}"
      +"$studentCount ${"student".pluralize(enrollees.count())} enrolled in $classDesc [$activeClassCode]"
    }
  }

  private fun BODY.addWebSockets(content: ReadingBatContent,
                                 languageName: LanguageName,
                                 groupName: GroupName,
                                 classCode: ClassCode) {
    script {
      rawHtml(
        """
          var wshost = location.origin.replace(${if (content.production) "/^https:/, 'wss:'" else "/^http:/, 'ws:'"})
          var wsurl = wshost + '$CHALLENGE_GROUP_ENDPOINT/$languageName/$groupName/$classCode'
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

  private fun BODY.clearGroupAnswerHistory(user: User?,
                                           browserSession: BrowserSession?,
                                           languageName: LanguageName,
                                           groupName: GroupName,
                                           challenges: List<Challenge>) {
    val challengeAnswerKeys = challenges.map { user.challengeAnswersKey(browserSession, it) }
    val challengeAnswersKey = gson.toJson(challengeAnswerKeys)

    p {
      form {
        style = "margin:0;"
        action = CLEAR_GROUP_ANSWERS_ENDPOINT
        method = FormMethod.post
        onSubmit = """return confirm('Are you sure you want to clear your previous answers for group "$groupName"?');"""
        input { type = InputType.hidden; name = LANGUAGE_NAME_KEY; value = languageName.value }
        input { type = InputType.hidden; name = GROUP_NAME_KEY; value = groupName.value }
        input { type = InputType.hidden; name = CHALLENGE_ANSWERS_KEY; value = challengeAnswersKey }
        input {
          style = "vertical-align:middle; margin-top:1; margin-bottom:0;"
          type = InputType.submit; value = "Clear answer history"
        }
      }
    }
  }

}