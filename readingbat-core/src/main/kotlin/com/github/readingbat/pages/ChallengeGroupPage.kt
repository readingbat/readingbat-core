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

package com.github.readingbat.pages

import com.github.pambrose.common.util.pathOf
import com.github.readingbat.common.BrowserSession
import com.github.readingbat.common.ClassCode
import com.github.readingbat.common.Constants.COLUMN_CNT
import com.github.readingbat.common.Constants.MSG
import com.github.readingbat.common.CssNames.FUNC_ITEM1
import com.github.readingbat.common.CssNames.FUNC_ITEM2
import com.github.readingbat.common.CssNames.UNDERLINE
import com.github.readingbat.common.Endpoints.CHALLENGE_GROUP_ENDPOINT
import com.github.readingbat.common.Endpoints.CHALLENGE_ROOT
import com.github.readingbat.common.Endpoints.CLEAR_GROUP_ANSWERS_ENDPOINT
import com.github.readingbat.common.Endpoints.STATIC_ROOT
import com.github.readingbat.common.Endpoints.WS_ROOT
import com.github.readingbat.common.Endpoints.classSummaryEndpoint
import com.github.readingbat.common.FormFields.CHALLENGE_ANSWERS_PARAM
import com.github.readingbat.common.FormFields.CORRECT_ANSWERS_PARAM
import com.github.readingbat.common.FormFields.GROUP_NAME_PARAM
import com.github.readingbat.common.FormFields.LANGUAGE_NAME_PARAM
import com.github.readingbat.common.Message
import com.github.readingbat.common.StaticFileNames.GREEN_CHECK
import com.github.readingbat.common.StaticFileNames.WHITE_CHECK
import com.github.readingbat.common.User
import com.github.readingbat.common.User.Companion.queryActiveTeachingClassCode
import com.github.readingbat.common.browserSession
import com.github.readingbat.common.challengeAnswersKey
import com.github.readingbat.common.correctAnswersKey
import com.github.readingbat.dsl.ChallengeGroup
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.dsl.challenge.Challenge
import com.github.readingbat.dsl.isDbmsEnabled
import com.github.readingbat.pages.ChallengePage.HEADER_COLOR
import com.github.readingbat.pages.PageUtils.backLink
import com.github.readingbat.pages.PageUtils.bodyHeader
import com.github.readingbat.pages.PageUtils.encodeUriElems
import com.github.readingbat.pages.PageUtils.enrolleesDesc
import com.github.readingbat.pages.PageUtils.headDefault
import com.github.readingbat.pages.PageUtils.loadPingdomScript
import com.github.readingbat.pages.PageUtils.rawHtml
import com.github.readingbat.server.GroupName
import com.github.readingbat.server.LanguageName
import com.github.readingbat.server.ServerUtils.queryParam
import com.github.readingbat.server.ServerUtils.rows
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.routing.RoutingContext
import kotlinx.html.BODY
import kotlinx.html.FormMethod
import kotlinx.html.TR
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.head
import kotlinx.html.hiddenInput
import kotlinx.html.html
import kotlinx.html.id
import kotlinx.html.img
import kotlinx.html.onSubmit
import kotlinx.html.p
import kotlinx.html.script
import kotlinx.html.span
import kotlinx.html.stream.createHTML
import kotlinx.html.style
import kotlinx.html.submitInput
import kotlinx.html.table
import kotlinx.html.td
import kotlinx.html.tr
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal object ChallengeGroupPage {
  private val logger = KotlinLogging.logger {}

  fun RoutingContext.challengeGroupPage(
    content: ReadingBatContent,
    user: User?,
    challengeGroup: ChallengeGroup<*>,
    loginAttempt: Boolean,
  ) =
    createHTML()
      .html {
        val browserSession = call.browserSession
        val languageType = challengeGroup.languageType
        val languageName = languageType.languageName
        val groupName = challengeGroup.groupName
        val challenges = challengeGroup.challenges
        val loginPath = pathOf(CHALLENGE_ROOT, languageName, groupName)
        val activeTeachingClassCode = queryActiveTeachingClassCode(user)
        val enrollees = activeTeachingClassCode.fetchEnrollees()
        val msg = Message(queryParam(MSG))

        fun TR.displayFunctionCall(user: User?, challenge: Challenge) {
          val challengeName = challenge.challengeName
          val allCorrect = challenge.isCorrect(user, browserSession)

          td(classes = if (activeTeachingClassCode.isEnabled && enrollees.isNotEmpty()) FUNC_ITEM1 else FUNC_ITEM2) {
            if (activeTeachingClassCode.isNotEnabled)
              img { src = pathOf(STATIC_ROOT, if (allCorrect) GREEN_CHECK else WHITE_CHECK) }

            a {
              style = "font-Size:110%; padding-left:2px"
              href = pathOf(CHALLENGE_ROOT, languageName, groupName, challengeName)
              +challengeName.value
            }

            // This element is dynamically populated by the websocket data
            if (enrollees.isNotEmpty())
              span {
                id = challengeName.value
                +""
              }
          }
        }

        head { headDefault() }

        body {
          bodyHeader(content, user, languageType, loginAttempt, loginPath, false, activeTeachingClassCode, msg)

          h2 { +groupName.toString() }

          if (activeTeachingClassCode.isEnabled)
            displayClassDescription(activeTeachingClassCode, languageName, groupName, enrollees)

          if (enrollees.isNotEmpty())
            p { +"(# of questions | # that started | # completed | Avg correct | Incorrect attempts | Likes/Dislikes)" }

          table {
            val size = challenges.size
            val rows = size.rows(COLUMN_CNT)

            // val width = if (enrollees.isNotEmpty()) 1200 else 800
            // style = "width:${width}px"
            style = "width:100%"

            repeat(rows) { i ->
              tr {
                style = "height:30"
                challenges.apply {
                  displayFunctionCall(user, elementAt(i))
                  elementAtOrNull(i + rows)?.also { displayFunctionCall(user, it) } ?: td {}
                  elementAtOrNull(i + (2 * rows))?.also { displayFunctionCall(user, it) } ?: td {}
                }
              }
            }
          }

          if (isDbmsEnabled() && activeTeachingClassCode.isNotEnabled && challenges.isNotEmpty())
            clearGroupAnswerHistoryOption(user, browserSession, languageName, groupName, challenges)

          backLink(CHALLENGE_ROOT, languageName.value)

          if (enrollees.isNotEmpty())
            enableWebSockets(languageName, groupName, activeTeachingClassCode)

          loadPingdomScript()
        }
      }

  fun BODY.displayClassDescription(
    classCode: ClassCode,
    languageName: LanguageName,
    groupName: GroupName,
    enrollees: List<User>,
  ) {
    h3 {
      style = "margin-left: 5px; color: $HEADER_COLOR"
      a(classes = UNDERLINE) {
        href =
          if (groupName.isNotValid())
            classSummaryEndpoint(classCode)
          else
            classSummaryEndpoint(classCode, languageName, groupName)
        +classCode.toDisplayString()
      }
      +enrolleesDesc(enrollees)
    }
  }

  private fun BODY.enableWebSockets(langName: LanguageName, groupName: GroupName, classCode: ClassCode) {
    script {
      rawHtml(
        """
          var wshost = location.origin;
          if (wshost.startsWith('https:'))
            wshost = wshost.replace(/^https:/, 'wss:');
          else
            wshost = wshost.replace(/^http:/, 'ws:');

          var wsurl = wshost + '$WS_ROOT$CHALLENGE_GROUP_ENDPOINT/' + ${encodeUriElems(langName, groupName, classCode)};
          var ws = new WebSocket(wsurl);

          ws.onopen = function (event) {
            ws.send("$classCode");
          };

          ws.onmessage = function (event) {
            //console.log(event.data);
            var obj = JSON.parse(event.data)
            document.getElementById(obj.challengeName).innerText = obj.msg;
          };
        """.trimIndent(),
      )
    }
  }

  private fun BODY.clearGroupAnswerHistoryOption(
    user: User?,
    browserSession: BrowserSession?,
    languageName: LanguageName,
    groupName: GroupName,
    challenges: List<Challenge>,
  ) {
    val correctAnswersKeys = challenges.map { correctAnswersKey(user, browserSession, it) }
    val challengeAnswerKeys = challenges.map { challengeAnswersKey(user, browserSession, it) }

    p {
      form {
        style = "margin:0"
        action = CLEAR_GROUP_ANSWERS_ENDPOINT
        method = FormMethod.post
        onSubmit = """return confirm('Are you sure you want to clear your previous answers for group "$groupName"?')"""
        hiddenInput {
          name = LANGUAGE_NAME_PARAM
          value = languageName.value
        }
        hiddenInput {
          name = GROUP_NAME_PARAM
          value = groupName.value
        }
        hiddenInput {
          name = CORRECT_ANSWERS_PARAM
          value = Json.encodeToString(correctAnswersKeys)
        }
        hiddenInput {
          name = CHALLENGE_ANSWERS_PARAM
          value = Json.encodeToString(challengeAnswerKeys)
        }
        submitInput {
          style = "vertical-align:middle; margin-top:1; margin-bottom:0"
          value = "Clear answer history"
        }
      }
    }
  }
}
