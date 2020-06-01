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
import com.github.readingbat.dsl.Challenge
import com.github.readingbat.dsl.FunctionInfo
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.BrowserSession
import com.github.readingbat.misc.CSSNames.ARROW
import com.github.readingbat.misc.CSSNames.CHALLENGE_DESC
import com.github.readingbat.misc.CSSNames.CHECK_ANSWERS
import com.github.readingbat.misc.CSSNames.CODE_BLOCK
import com.github.readingbat.misc.CSSNames.DASHBOARD
import com.github.readingbat.misc.CSSNames.FEEDBACK
import com.github.readingbat.misc.CSSNames.FUNC_COL
import com.github.readingbat.misc.CSSNames.REFS
import com.github.readingbat.misc.CSSNames.STATUS
import com.github.readingbat.misc.CSSNames.SUCCESS
import com.github.readingbat.misc.CSSNames.USER_RESP
import com.github.readingbat.misc.CheckAnswersJs.checkAnswersScript
import com.github.readingbat.misc.CheckAnswersJs.processAnswers
import com.github.readingbat.misc.ClassCode
import com.github.readingbat.misc.ClassCode.Companion.EMPTY_CLASS_CODE
import com.github.readingbat.misc.Constants.CHALLENGE_ROOT
import com.github.readingbat.misc.Constants.CORRECT_COLOR
import com.github.readingbat.misc.Constants.DBMS_DOWN
import com.github.readingbat.misc.Constants.MSG
import com.github.readingbat.misc.Constants.PLAYGROUND_ROOT
import com.github.readingbat.misc.Constants.RESP
import com.github.readingbat.misc.Constants.STATIC_ROOT
import com.github.readingbat.misc.Constants.WRONG_COLOR
import com.github.readingbat.misc.Endpoints.CLASS_PREFIX
import com.github.readingbat.misc.KeyConstants.NAME_FIELD
import com.github.readingbat.misc.PageUtils.pathOf
import com.github.readingbat.misc.ParameterIds.FEEDBACK_ID
import com.github.readingbat.misc.ParameterIds.SPINNER_ID
import com.github.readingbat.misc.ParameterIds.STATUS_ID
import com.github.readingbat.misc.ParameterIds.SUCCESS_ID
import com.github.readingbat.misc.User
import com.github.readingbat.misc.User.Companion.challengeAnswersKey
import com.github.readingbat.misc.User.Companion.gson
import com.github.readingbat.misc.UserPrincipal
import com.github.readingbat.pages.PageCommon.addLink
import com.github.readingbat.pages.PageCommon.backLink
import com.github.readingbat.pages.PageCommon.bodyHeader
import com.github.readingbat.pages.PageCommon.headDefault
import com.github.readingbat.pages.PageCommon.rawHtml
import com.github.readingbat.pages.UserPrefsPage.fetchClassDesc
import com.github.readingbat.posts.ChallengeHistory
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.ServerUtils.fetchPrincipal
import com.github.readingbat.server.ServerUtils.queryParam
import io.ktor.application.call
import io.ktor.http.ContentType.Text.CSS
import io.ktor.sessions.get
import io.ktor.sessions.sessions
import kotlinx.html.*
import kotlinx.html.ScriptType.textJavaScript
import kotlinx.html.stream.createHTML
import mu.KLogging
import redis.clients.jedis.Jedis

internal object ChallengePage : KLogging() {
  private const val spinnerCss = "https://cdnjs.cloudflare.com/ajax/libs/font-awesome/4.7.0/css/font-awesome.min.css"
  private const val nameTd = "nameTd"
  private const val answersTd = "answersTd"
  private const val answersSpan = "answersSpan"
  private const val numCorrectSpan = "numCorrectSpan"
  private const val headerColor = "#419DC1"

  fun PipelineCall.challengePage(content: ReadingBatContent,
                                 redis: Jedis?,
                                 challenge: Challenge,
                                 loginAttempt: Boolean) =
    createHTML()
      .html {
        val principal = fetchPrincipal(loginAttempt)
        val browserSession = call.sessions.get<BrowserSession>()
        val languageType = challenge.languageType
        val groupName = challenge.groupName
        val challengeName = challenge.challengeName
        val languageName = languageType.lowerName
        val funcInfo = challenge.funcInfo(content)
        val loginPath = pathOf(CHALLENGE_ROOT, languageName, groupName, challengeName)

        head {
          link { rel = "stylesheet"; href = spinnerCss }
          link { rel = "stylesheet"; href = "$STATIC_ROOT/$languageName-prism.css"; type = CSS.toString() }

          script(type = textJavaScript) { checkAnswersScript(languageName, groupName, challengeName) }

          removePrismShadow()
          headDefault(content)
        }

        body {
          bodyHeader(redis, principal, loginAttempt, content, languageType, loginPath, false, queryParam(MSG) ?: "")

          this@body.displayChallenge(challenge, funcInfo)

          val user = principal?.toUser()
          val activeClassCode = user?.fetchActiveClassCode(redis) ?: EMPTY_CLASS_CODE
          if (activeClassCode.isNotEmpty)
            this@body.displayQuestions(redis, principal, browserSession, challenge, funcInfo)
          else {
            if (redis == null)
              p { +DBMS_DOWN }
            else
              this@body.displayStudentProgress(redis, challenge, content.maxHistoryLength, funcInfo, activeClassCode)
          }

          backLink(CHALLENGE_ROOT, languageName, groupName)

          script { src = "$STATIC_ROOT/$languageName-prism.js" }

          if (activeClassCode.isEmpty)
            addWebSockets(content, activeClassCode)
        }
      }

  private fun BODY.displayChallenge(challenge: Challenge, funcInfo: FunctionInfo) {
    val languageType = challenge.languageType
    val groupName = challenge.groupName
    val challengeName = challenge.challengeName
    val languageName = languageType.lowerName

    h2 {
      val groupPath = pathOf(CHALLENGE_ROOT, languageName, groupName)
      this@displayChallenge.addLink(groupName.decode(), groupPath)
      span { style = "padding-left:2px; padding-right:2px;"; rawHtml("&rarr;") }
      +challengeName
    }

    if (challenge.description.isNotEmpty())
      div(classes = CHALLENGE_DESC) { rawHtml(challenge.parsedDescription) }

    div(classes = CODE_BLOCK) {
      pre(classes = "line-numbers") {
        code(classes = "language-$languageName") { +funcInfo.codeSnippet }
      }
    }
  }

  private fun BODY.displayQuestions(redis: Jedis?,
                                    principal: UserPrincipal?,
                                    browserSession: BrowserSession?,
                                    challenge: Challenge,
                                    funcInfo: FunctionInfo) =
    div {
      style = "margin-top:2em; margin-left:2em;"
      table {
        tr {
          th {
            colSpan = "2"
            style = "color: $headerColor"
            +"Function Call"
          }
          th {
            colSpan = "2"
            style = "color: $headerColor"
            +"Return Value"
          }
        }

        val previousAnswers =
          if (redis == null)
            mutableMapOf()
          else
            previousAnswers(redis, principal, browserSession, challenge)

        funcInfo.invocations.withIndex().forEach { (i, invocation) ->
          tr {
            td(classes = FUNC_COL) { +invocation }
            td(classes = ARROW) { rawHtml("&rarr;") }
            td {
              textInput(classes = USER_RESP) {
                id = "$RESP$i"
                onKeyPress = "$processAnswers(event, ${funcInfo.answers.size})"
                if (previousAnswers[invocation] != null)
                  value = previousAnswers[invocation] ?: ""
                else
                  placeholder = funcInfo.placeHolder()
              }
            }
            td(classes = FEEDBACK) { id = "$FEEDBACK_ID$i" }
          }
        }
      }

      this@displayQuestions.processAnswers(funcInfo)
      this@displayQuestions.otherLinks(challenge)
    }

  private fun BODY.addWebSockets(content: ReadingBatContent, classCode: ClassCode) {
    script {
      rawHtml(
        """
          var wshost = location.origin.replace(${if (content.production) "/^https:/, 'wss:'" else "/^http:/, 'ws:'"})
          var wsurl = wshost + '$CLASS_PREFIX/$classCode'
          
          var ws = new WebSocket(wsurl);
          
          ws.onopen = function (event) {
            ws.send("$classCode"); 
          };
          
          ws.onmessage = function (event) {
            //console.log(event.data);
            var obj = JSON.parse(event.data)
            
            var name = document.getElementById(obj.userId + '-$nameTd');
            name.style.backgroundColor = obj.complete ? '$CORRECT_COLOR' : '$WRONG_COLOR';

            document.getElementById(obj.userId + '-$numCorrectSpan').innerHTML = obj.numCorrect;

            var prefix = obj.userId + '-' + obj.history.invocation;
            var answers = document.getElementById(prefix + '-$answersTd')
            answers.style.backgroundColor = obj.history.correct ? '$CORRECT_COLOR' : '$WRONG_COLOR';

            document.getElementById(prefix + '-$answersSpan').innerHTML = obj.history.answers;
          };
        """.trimIndent())
    }
  }

  private fun BODY.displayStudentProgress(redis: Jedis,
                                          challenge: Challenge,
                                          maxHistoryLength: Int,
                                          funcInfo: FunctionInfo,
                                          activeClassCode: ClassCode) =
    div {
      style = "margin-top:2em;"

      val languageType = challenge.languageType
      val groupName = challenge.groupName
      val challengeName = challenge.challengeName
      val languageName = languageType.lowerName

      val enrollees = activeClassCode.fetchEnrollees(redis)
      if (enrollees.isEmpty()) {
        h3 {
          style = "margin-left: 5px; color: $headerColor"
          +"No students enrolled in ${fetchClassDesc(activeClassCode, redis)} [$activeClassCode]"
        }
      }
      else {
        //br
        h3 {
          style = "margin-left: 5px; color: $headerColor"
          +"Student progress for ${fetchClassDesc(activeClassCode, redis)} [$activeClassCode]"
        }

        table {
          style = "width:100%; border-spacing: 5px 10px;"

          tr {
            th { style = "text-align:left; color: $headerColor"; +"Student" }
            funcInfo.invocations.indices.forEach { i ->
              val invocation = funcInfo.invocations[i]
              th { style = "text-align:left; color: $headerColor"; +invocation.substring(invocation.indexOf("(")) }
            }
          }

          enrollees.forEach { user ->
            val userInfoKey = user.userInfoKey
            var numCorrect = 0

            val results =
              funcInfo.invocations
                .map { invocation ->
                  val answerHistoryKey = user.answerHistoryKey(languageName, groupName, challengeName, invocation)
                  val history =
                    gson.fromJson(redis[answerHistoryKey], ChallengeHistory::class.java)
                      ?: ChallengeHistory(invocation)
                  if (history.correct)
                    numCorrect++
                  invocation to history
                }

            tr(classes = DASHBOARD) {
              td(classes = DASHBOARD) {
                id = "${user.id}-$nameTd"
                style = "background-color:${if (numCorrect == results.size) CORRECT_COLOR else WRONG_COLOR};"

                span { id = "${user.id}-$numCorrectSpan"; +numCorrect.toString() }
                +"/${results.size}"
                rawHtml(Entities.nbsp.text)
                +(redis.hget(userInfoKey, NAME_FIELD) ?: "")
              }

              results.forEach { (invocation, history) ->
                td(classes = DASHBOARD) {
                  id = "${user.id}-$invocation-$answersTd"
                  style = "background-color:${if (history.correct) CORRECT_COLOR else WRONG_COLOR};"
                  span {
                    id = "${user.id}-$invocation-$answersSpan"
                    history.answers.asReversed().take(maxHistoryLength).forEach { answer -> +answer; br }
                  }
                }
              }
            }
          }
        }
      }
    }

  private fun previousAnswers(redis: Jedis,
                              principal: UserPrincipal?,
                              browserSession: BrowserSession?,
                              challenge: Challenge): MutableMap<String, String> {
    val languageType = challenge.languageType
    val groupName = challenge.groupName
    val challengeName = challenge.challengeName
    val languageName = languageType.lowerName
    val user: User? = principal?.toUser()
    val key = challengeAnswersKey(user, browserSession, languageName, groupName, challengeName)

    return if (key.isNotEmpty()) redis.hgetAll(key) else mutableMapOf()
  }

  private fun BODY.processAnswers(funcInfo: FunctionInfo) {
    div {
      style = "margin-top:2em;"
      table {
        tr {
          td {
            button(classes = CHECK_ANSWERS) {
              onClick = "$processAnswers(null, ${funcInfo.answers.size});"; +"Check My Answers"
            }
          }
          td { style = "vertical-align:middle;"; span { style = "margin-left:1em;"; id = SPINNER_ID } }
          td {
            style = "vertical-align:middle;"
            span(classes = STATUS) { id = STATUS_ID }
            span(classes = SUCCESS) { id = SUCCESS_ID }
          }
        }
      }
    }
  }

  private fun BODY.otherLinks(challenge: Challenge) {
    val languageType = challenge.languageType
    val groupName = challenge.groupName
    val challengeName = challenge.challengeName

    p(classes = REFS) {
      +"Experiment with this code on "
      this@otherLinks.addLink("Gitpod.io", "https://gitpod.io/#${challenge.gitpodUrl}", true)
      if (languageType.isKotlin()) {
        +" or as a "
        this@otherLinks.addLink("Kotlin Playground", pathOf(PLAYGROUND_ROOT, groupName, challengeName), false)
      }
    }

    if (challenge.codingBatEquiv.isNotEmpty() && (languageType.isJava() || languageType.isPython())) {
      p(classes = REFS) {
        +"Work on a similar problem on "
        this@otherLinks.addLink("CodingBat.com", "https://codingbat.com/prob/${challenge.codingBatEquiv}", true)
      }
    }
  }

  private fun HEAD.removePrismShadow() {
    // Remove the prism shadow
    style {
      rawHtml(
        """
        pre[class*="language-"]:before,
        pre[class*="language-"]:after { display: none; }
      """)
    }
  }
}