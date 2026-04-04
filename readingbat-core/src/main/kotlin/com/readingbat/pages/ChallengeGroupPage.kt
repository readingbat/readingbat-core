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

package com.readingbat.pages

import com.pambrose.common.util.pathOf
import com.readingbat.common.ClassCode
import com.readingbat.common.ClassCodeRepository.fetchEnrollees
import com.readingbat.common.ClassCodeRepository.toDisplayString
import com.readingbat.common.Constants.COLUMN_CNT
import com.readingbat.common.Constants.MSG
import com.readingbat.common.Endpoints.CHALLENGE_GROUP_ENDPOINT
import com.readingbat.common.Endpoints.CHALLENGE_ROOT
import com.readingbat.common.Endpoints.CLEAR_GROUP_ANSWERS_ENDPOINT
import com.readingbat.common.Endpoints.STATIC_ROOT
import com.readingbat.common.Endpoints.WS_ROOT
import com.readingbat.common.Endpoints.classSummaryEndpoint
import com.readingbat.common.FormFields.CHALLENGE_ANSWERS_PARAM
import com.readingbat.common.FormFields.CORRECT_ANSWERS_PARAM
import com.readingbat.common.FormFields.GROUP_NAME_PARAM
import com.readingbat.common.FormFields.LANGUAGE_NAME_PARAM
import com.readingbat.common.Message
import com.readingbat.common.StaticFileNames.GREEN_CHECK
import com.readingbat.common.StaticFileNames.WHITE_CHECK
import com.readingbat.common.TwClasses
import com.readingbat.common.User
import com.readingbat.common.User.Companion.queryActiveTeachingClassCode
import com.readingbat.common.WsProtocol
import com.readingbat.common.challengeAnswersKey
import com.readingbat.common.correctAnswersKey
import com.readingbat.dsl.ChallengeGroup
import com.readingbat.dsl.ReadingBatContent
import com.readingbat.dsl.challenge.Challenge
import com.readingbat.dsl.isDbmsEnabled
import com.readingbat.pages.ChallengePage.HEADER_COLOR
import com.readingbat.pages.PageUtils.backLink
import com.readingbat.pages.PageUtils.bodyHeader
import com.readingbat.pages.PageUtils.encodeUriElems
import com.readingbat.pages.PageUtils.enrolleesDesc
import com.readingbat.pages.PageUtils.headDefault
import com.readingbat.pages.PageUtils.loadPingdomScript
import com.readingbat.pages.PageUtils.rawHtml
import com.readingbat.server.ChallengeProgressService
import com.readingbat.server.GroupName
import com.readingbat.server.LanguageName
import com.readingbat.server.ServerUtils.queryParam
import com.readingbat.server.ServerUtils.rows
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
import kotlinx.html.onClick
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
import kotlinx.serialization.json.Json

/**
 * Generates the challenge group listing page at `/content/{lang}/{group}`.
 *
 * Shows all challenges in the group as a multi-column table with completion checkmarks.
 * In teacher mode, displays class enrollment info and receives real-time statistics via WebSockets.
 */
internal object ChallengeGroupPage {
  /** Renders the challenge group listing page HTML for the given challenge group. */
  fun RoutingContext.challengeGroupPage(
    content: ReadingBatContent,
    user: User?,
    challengeGroup: ChallengeGroup<*>,
  ) =
    createHTML()
      .html {
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
          val allCorrect = ChallengeProgressService.isCorrect(user, challenge.md5())

          td(
            classes =
            if (activeTeachingClassCode.isEnabled &&
            enrollees.isNotEmpty()
            )
            TwClasses.FUNC_ITEM1
            else
            TwClasses.FUNC_ITEM2,
            ) {
            if (activeTeachingClassCode.isNotEnabled)
              img { src = pathOf(STATIC_ROOT, if (allCorrect) GREEN_CHECK else WHITE_CHECK) }

            val challengePath = pathOf(CHALLENGE_ROOT, languageName, groupName, challengeName)
            a(classes = "text-[110%] pl-0.5") {
              href = challengePath
              if (user == null) {
                onClick = "openOAuthModal('$challengePath'); return false;"
              }
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
          bodyHeader(content, user, languageType, loginPath, false, activeTeachingClassCode, msg)

          h2 { +groupName.toString() }

          if (activeTeachingClassCode.isEnabled)
            displayClassDescription(activeTeachingClassCode, languageName, groupName, enrollees)

          if (enrollees.isNotEmpty())
            p { +"(# of questions | # that started | # completed | Avg correct | Incorrect attempts | Likes/Dislikes)" }

          table(classes = "w-full") {
            val size = challenges.size
            val rows = size.rows(COLUMN_CNT)

            repeat(rows) { i ->
              tr(classes = "h-[30px]") {
                challenges.apply {
                  displayFunctionCall(user, elementAt(i))
                  elementAtOrNull(i + rows)?.also { displayFunctionCall(user, it) } ?: td {}
                  elementAtOrNull(i + (2 * rows))?.also { displayFunctionCall(user, it) } ?: td {}
                }
              }
            }
          }

          if (isDbmsEnabled() && activeTeachingClassCode.isNotEnabled && challenges.isNotEmpty())
            clearGroupAnswerHistoryOption(user, languageName, groupName, challenges)

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
    h3(classes = "ml-1 text-rb-header") {
      style = "margin-left: 5px; color: $HEADER_COLOR"
      a(classes = TwClasses.UNDERLINE) {
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
            document.getElementById(obj["${WsProtocol.CHALLENGE_NAME_FIELD}"]).innerText = obj["${WsProtocol.MSG_FIELD}"];
          };
        """.trimIndent(),
      )
    }
  }

  private fun BODY.clearGroupAnswerHistoryOption(
    user: User?,
    languageName: LanguageName,
    groupName: GroupName,
    challenges: List<Challenge>,
  ) {
    val correctAnswersKeys = challenges.map { correctAnswersKey(user, it) }
    val challengeAnswerKeys = challenges.map { challengeAnswersKey(user, it) }

    p {
      form(classes = "m-0") {
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
        submitInput(classes = TwClasses.CLEAR_HISTORY) {
          value = "Clear answer history"
        }
      }
    }
  }
}
