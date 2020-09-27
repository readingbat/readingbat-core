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

import com.github.pambrose.common.util.isNotNull
import com.github.readingbat.common.*
import com.github.readingbat.common.CSSNames.FUNC_ITEM1
import com.github.readingbat.common.CSSNames.FUNC_ITEM2
import com.github.readingbat.common.CSSNames.UNDERLINE
import com.github.readingbat.common.CommonUtils.md5Of
import com.github.readingbat.common.CommonUtils.pathOf
import com.github.readingbat.common.Constants.COLUMN_CNT
import com.github.readingbat.common.Constants.MSG
import com.github.readingbat.common.Endpoints.CHALLENGE_GROUP_ENDPOINT
import com.github.readingbat.common.Endpoints.CHALLENGE_ROOT
import com.github.readingbat.common.Endpoints.CLEAR_GROUP_ANSWERS_ENDPOINT
import com.github.readingbat.common.Endpoints.STATIC_ROOT
import com.github.readingbat.common.Endpoints.classSummaryEndpoint
import com.github.readingbat.common.FormFields.CHALLENGE_ANSWERS_PARAM
import com.github.readingbat.common.FormFields.CORRECT_ANSWERS_PARAM
import com.github.readingbat.common.FormFields.GROUP_NAME_PARAM
import com.github.readingbat.common.FormFields.LANGUAGE_NAME_PARAM
import com.github.readingbat.common.StaticFileNames.GREEN_CHECK
import com.github.readingbat.common.StaticFileNames.WHITE_CHECK
import com.github.readingbat.common.User.Companion.fetchActiveClassCode
import com.github.readingbat.common.User.Companion.gson
import com.github.readingbat.dsl.Challenge
import com.github.readingbat.dsl.ChallengeGroup
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.pages.ChallengePage.headerColor
import com.github.readingbat.pages.PageUtils.backLink
import com.github.readingbat.pages.PageUtils.bodyHeader
import com.github.readingbat.pages.PageUtils.encodeUriElems
import com.github.readingbat.pages.PageUtils.enrolleesDesc
import com.github.readingbat.pages.PageUtils.headDefault
import com.github.readingbat.pages.PageUtils.loadPingdomScript
import com.github.readingbat.pages.PageUtils.rawHtml
import com.github.readingbat.server.GroupName
import com.github.readingbat.server.LanguageName
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.ReadingBatServer.usePostgres
import com.github.readingbat.server.ServerUtils.queryParam
import com.github.readingbat.server.ServerUtils.rows
import io.ktor.application.*
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import mu.KLogging
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import redis.clients.jedis.Jedis

internal object ChallengeGroupPage : KLogging() {

  fun Challenge.isCorrect(user: User?, browserSession: BrowserSession?, redis: Jedis?) =
    if (usePostgres) {
      val challengeMd5 = md5Of(languageName, groupName, challengeName)
      when {
        user.isNotNull() ->
          transaction {
            UserChallengeInfo
              .slice(UserChallengeInfo.allCorrect)
              .select { (UserChallengeInfo.userRef eq user.userDbmsId) and (UserChallengeInfo.md5 eq challengeMd5) }
              .map { it[UserChallengeInfo.allCorrect] }
              .firstOrNull() ?: false
          }
        browserSession.isNotNull() ->
          transaction {
            SessionChallengeInfo
              .slice(SessionChallengeInfo.allCorrect)
              .select { (SessionChallengeInfo.sessionRef eq browserSession.sessionDbmsId()) and (SessionChallengeInfo.md5 eq challengeMd5) }
              .map { it[SessionChallengeInfo.allCorrect] }
              .firstOrNull() ?: false
          }
        else -> false
      }
    }
    else {
      val correctAnswersKey = correctAnswersKey(user, browserSession, languageName, groupName, challengeName)
      if (correctAnswersKey.isNotEmpty()) redis?.get(correctAnswersKey)?.toBoolean() == true else false
    }

  fun PipelineCall.challengeGroupPage(content: ReadingBatContent,
                                      user: User?,
                                      challengeGroup: ChallengeGroup<*>,
                                      loginAttempt: Boolean,
                                      redis: Jedis?) =
    createHTML()
      .html {
        val browserSession = call.browserSession
        val languageType = challengeGroup.languageType
        val languageName = languageType.languageName
        val groupName = challengeGroup.groupName
        val challenges = challengeGroup.challenges
        val loginPath = pathOf(CHALLENGE_ROOT, languageName, groupName)
        val activeClassCode = fetchActiveClassCode(user, redis)
        val enrollees = activeClassCode.fetchEnrollees(redis)
        val msg = Message(queryParam(MSG))

        fun TR.displayFunctionCall(user: User?, challenge: Challenge, redis: Jedis?) {
          val challengeName = challenge.challengeName
          val allCorrect = challenge.isCorrect(user, browserSession, redis)

          td(classes = if (activeClassCode.isEnabled && enrollees.isNotEmpty()) FUNC_ITEM1 else FUNC_ITEM2) {
            if (activeClassCode.isNotEnabled)
              img { src = pathOf(STATIC_ROOT, if (allCorrect) GREEN_CHECK else WHITE_CHECK) }

            a {
              style = "font-Size:110%; padding-left:2px"
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
          bodyHeader(content, user, languageType, loginAttempt, loginPath, false, activeClassCode, redis, msg)

          h2 { +groupName.toString() }

          if (activeClassCode.isEnabled)
            displayClassDescription(activeClassCode, languageName, groupName, enrollees, redis)

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

          loadPingdomScript()
        }
      }

  fun BODY.displayClassDescription(classCode: ClassCode,
                                   languageName: LanguageName,
                                   groupName: GroupName,
                                   enrollees: List<User>,
                                   redis: Jedis?) {
    h3 {
      style = "margin-left: 5px; color: $headerColor"
      a(classes = UNDERLINE) {
        href =
          if (groupName.isNotValid())
            classSummaryEndpoint(classCode)
          else
            classSummaryEndpoint(classCode, languageName, groupName)
        +classCode.toDisplayString(redis)
      }
      +enrolleesDesc(enrollees)
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

    val correctAnswersKeys = challenges.map { correctAnswersKey(user, browserSession, it) }
    val challengeAnswerKeys = challenges.map { challengeAnswersKey(user, browserSession, it) }

    p {
      form {
        style = "margin:0"
        action = CLEAR_GROUP_ANSWERS_ENDPOINT
        method = FormMethod.post
        onSubmit = """return confirm('Are you sure you want to clear your previous answers for group "$groupName"?')"""
        hiddenInput { name = LANGUAGE_NAME_PARAM; value = languageName.value }
        hiddenInput { name = GROUP_NAME_PARAM; value = groupName.value }
        hiddenInput { name = CORRECT_ANSWERS_PARAM; value = gson.toJson(correctAnswersKeys) }
        hiddenInput { name = CHALLENGE_ANSWERS_PARAM; value = gson.toJson(challengeAnswerKeys) }
        submitInput {
          style = "vertical-align:middle; margin-top:1; margin-bottom:0"
          value = "Clear answer history"
        }
      }
    }
  }
}
