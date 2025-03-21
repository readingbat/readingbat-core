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

import com.github.pambrose.common.util.decode
import com.github.pambrose.common.util.isNotNull
import com.github.pambrose.common.util.isNull
import com.github.pambrose.common.util.pathOf
import com.github.pambrose.common.util.random
import com.github.pambrose.common.util.toDoubleQuoted
import com.github.readingbat.common.BrowserSession
import com.github.readingbat.common.ClassCode
import com.github.readingbat.common.Constants.CORRECT_COLOR
import com.github.readingbat.common.Constants.INCOMPLETE_COLOR
import com.github.readingbat.common.Constants.LIKE_DISLIKE_CODE
import com.github.readingbat.common.Constants.LIKE_DISLIKE_FUNC
import com.github.readingbat.common.Constants.MSG
import com.github.readingbat.common.Constants.PING_CODE
import com.github.readingbat.common.Constants.PRISM
import com.github.readingbat.common.Constants.PROCESS_USER_ANSWERS_FUNC
import com.github.readingbat.common.Constants.RESP
import com.github.readingbat.common.Constants.WRONG_COLOR
import com.github.readingbat.common.CssNames.ARROW
import com.github.readingbat.common.CssNames.CHALLENGE_DESC
import com.github.readingbat.common.CssNames.CHECK_ANSWERS
import com.github.readingbat.common.CssNames.CODE_BLOCK
import com.github.readingbat.common.CssNames.CODINGBAT
import com.github.readingbat.common.CssNames.DASHBOARD
import com.github.readingbat.common.CssNames.EXPERIMENT
import com.github.readingbat.common.CssNames.FEEDBACK
import com.github.readingbat.common.CssNames.FUNC_COL
import com.github.readingbat.common.CssNames.HINT
import com.github.readingbat.common.CssNames.LIKE_BUTTONS
import com.github.readingbat.common.CssNames.STATUS
import com.github.readingbat.common.CssNames.SUCCESS
import com.github.readingbat.common.CssNames.UNDERLINE
import com.github.readingbat.common.CssNames.USER_RESP
import com.github.readingbat.common.Endpoints.CHALLENGE_ENDPOINT
import com.github.readingbat.common.Endpoints.CHALLENGE_ROOT
import com.github.readingbat.common.Endpoints.CLEAR_CHALLENGE_ANSWERS_ENDPOINT
import com.github.readingbat.common.Endpoints.PLAYGROUND_ROOT
import com.github.readingbat.common.Endpoints.STATIC_ROOT
import com.github.readingbat.common.Endpoints.WS_ROOT
import com.github.readingbat.common.Endpoints.classSummaryEndpoint
import com.github.readingbat.common.FormFields.CHALLENGE_ANSWERS_PARAM
import com.github.readingbat.common.FormFields.CHALLENGE_NAME_PARAM
import com.github.readingbat.common.FormFields.CORRECT_ANSWERS_PARAM
import com.github.readingbat.common.FormFields.GROUP_NAME_PARAM
import com.github.readingbat.common.FormFields.LANGUAGE_NAME_PARAM
import com.github.readingbat.common.FunctionInfo
import com.github.readingbat.common.Message
import com.github.readingbat.common.ParameterIds.DISLIKE_CLEAR
import com.github.readingbat.common.ParameterIds.DISLIKE_COLOR
import com.github.readingbat.common.ParameterIds.FEEDBACK_ID
import com.github.readingbat.common.ParameterIds.HINT_ID
import com.github.readingbat.common.ParameterIds.LIKE_CLEAR
import com.github.readingbat.common.ParameterIds.LIKE_COLOR
import com.github.readingbat.common.ParameterIds.LIKE_SPINNER_ID
import com.github.readingbat.common.ParameterIds.LIKE_STATUS_ID
import com.github.readingbat.common.ParameterIds.NEXTPREVCHANCE_ID
import com.github.readingbat.common.ParameterIds.SPINNER_ID
import com.github.readingbat.common.ParameterIds.STATUS_ID
import com.github.readingbat.common.ParameterIds.SUCCESS_ID
import com.github.readingbat.common.StaticFileNames.DISLIKE_CLEAR_FILE
import com.github.readingbat.common.StaticFileNames.DISLIKE_COLOR_FILE
import com.github.readingbat.common.StaticFileNames.LIKE_CLEAR_FILE
import com.github.readingbat.common.StaticFileNames.LIKE_COLOR_FILE
import com.github.readingbat.common.User
import com.github.readingbat.common.User.Companion.queryActiveTeachingClassCode
import com.github.readingbat.common.browserSession
import com.github.readingbat.common.challengeAnswersKey
import com.github.readingbat.common.correctAnswersKey
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.dsl.challenge.Challenge
import com.github.readingbat.dsl.isDbmsEnabled
import com.github.readingbat.pages.PageUtils.addLink
import com.github.readingbat.pages.PageUtils.backLink
import com.github.readingbat.pages.PageUtils.bodyHeader
import com.github.readingbat.pages.PageUtils.enrolleesDesc
import com.github.readingbat.pages.PageUtils.headDefault
import com.github.readingbat.pages.PageUtils.loadPingdomScript
import com.github.readingbat.pages.PageUtils.rawHtml
import com.github.readingbat.pages.js.CheckAnswersJs.checkAnswersScript
import com.github.readingbat.pages.js.LikeDislikeJs.likeDislikeScript
import com.github.readingbat.server.ChallengeMd5
import com.github.readingbat.server.ServerUtils.queryParam
import com.github.readingbat.server.SessionChallengeInfoTable
import com.github.readingbat.server.UserChallengeInfoTable
import com.pambrose.common.exposed.get
import com.pambrose.common.exposed.readonlyTx
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.ContentType.Text.CSS
import io.ktor.server.routing.RoutingContext
import kotlinx.html.BODY
import kotlinx.html.Entities.nbsp
import kotlinx.html.FormMethod
import kotlinx.html.HEAD
import kotlinx.html.ScriptType.textJavaScript
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.br
import kotlinx.html.button
import kotlinx.html.code
import kotlinx.html.div
import kotlinx.html.emptyMap
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.head
import kotlinx.html.hiddenInput
import kotlinx.html.html
import kotlinx.html.id
import kotlinx.html.img
import kotlinx.html.link
import kotlinx.html.onClick
import kotlinx.html.onFocus
import kotlinx.html.onFocusOut
import kotlinx.html.onKeyDown
import kotlinx.html.onSubmit
import kotlinx.html.p
import kotlinx.html.pre
import kotlinx.html.script
import kotlinx.html.span
import kotlinx.html.stream.createHTML
import kotlinx.html.style
import kotlinx.html.submitInput
import kotlinx.html.tabIndex
import kotlinx.html.table
import kotlinx.html.td
import kotlinx.html.textInput
import kotlinx.html.th
import kotlinx.html.tr
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction

internal object ChallengePage {
  private val logger = KotlinLogging.logger {}
  private const val SPINNER_CSS = "https://cdnjs.cloudflare.com/ajax/libs/font-awesome/4.7.0/css/font-awesome.min.css"
  private const val NAME_TD = "nameTd"
  private const val ANSWER_TD = "answersTd"
  private const val LIKE_DISLIKE_SPAN = "likeDislikeSpan"
  private const val ANSWER_SPAN = "answersSpan"
  private const val NUM_CORRECTION_SPAN = "numCorrectSpan"
  private const val PING_LABEL = "pingLabel"
  private const val PING_MSG = "pingMsg"
  internal const val HEADER_COLOR = "#419DC1"

  fun RoutingContext.challengePage(
    content: ReadingBatContent,
    user: User?,
    challenge: Challenge,
    loginAttempt: Boolean,
  ) =
    createHTML()
      .html {
        val browserSession = call.browserSession
        val languageType = challenge.languageType
        val languageName = languageType.languageName
        val groupName = challenge.groupName
        val challengeName = challenge.challengeName
        val funcInfo = challenge.functionInfo()
        val loginPath = pathOf(CHALLENGE_ROOT, languageName, groupName, challengeName)
        val activeTeachingClassCode = queryActiveTeachingClassCode(user)
        val enrollees = activeTeachingClassCode.fetchEnrollees()
        val msg = Message(queryParam(MSG))

        head {
          link {
            rel = "stylesheet"
            href = SPINNER_CSS
          }
          link {
            rel = "stylesheet"
            href = pathOf(STATIC_ROOT, PRISM, "$languageName-prism.css")
            type = CSS.toString()
          }

          script(type = textJavaScript) { checkAnswersScript(languageName, groupName, challengeName) }
          script(type = textJavaScript) { likeDislikeScript(languageName, groupName, challengeName) }

          removePrismShadow()
          headDefault()
        }

        body {
          bodyHeader(content, user, languageType, loginAttempt, loginPath, false, activeTeachingClassCode, msg)

          displayChallenge(challenge, funcInfo)

          if (activeTeachingClassCode.isNotEnabled) {
            displayQuestions(user, browserSession, challenge, funcInfo)
          } else {
            displayStudentProgress(challenge, content.maxHistoryLength, funcInfo, activeTeachingClassCode, enrollees)

            if (enrollees.isNotEmpty())
              p {
                span { id = PING_LABEL }
                span { id = PING_MSG }
              }
          }

          backLink(CHALLENGE_ROOT, languageName.value, groupName.value)

          script { src = pathOf(STATIC_ROOT, PRISM, "$languageName-prism.js") }

          if (activeTeachingClassCode.isEnabled && enrollees.isNotEmpty())
            enableWebSockets(activeTeachingClassCode, funcInfo.challengeMd5)

          loadPingdomScript()
        }
      }

  private fun BODY.displayChallenge(challenge: Challenge, funcInfo: FunctionInfo) {
    val languageName = challenge.languageType.languageName
    val groupName = challenge.groupName
    val challengeName = challenge.challengeName
    val challengeGroup = challenge.challengeGroup
    val challenges = challenge.challengeGroup.challenges

    h2 {
      style = "margin-bottom:5px"
      val groupPath = pathOf(CHALLENGE_ROOT, languageName, groupName)
      this@displayChallenge.addLink(groupName.value.decode(), groupPath)
      span {
        style = "padding-left:2px; padding-right:2px"
        rawHtml("&rarr;")
      }
      +challengeName.value
    }

    span {
      style = "padding-left: 20px"
      val pos = challengeGroup.indexOf(challengeName)
      this@displayChallenge.nextPrevChance(pos, challenges, true)
    }

    if (challenge.description.isNotBlank())
      div(classes = CHALLENGE_DESC) { rawHtml(challenge.parsedDescription) }

    div(classes = CODE_BLOCK) {
      pre(classes = "line-numbers") {
        code(classes = "language-$languageName") {
          +funcInfo.codeSnippet
        }
      }
    }
  }

  // Make sure we do not return the same challenge on a chance click
  private fun Int.chance(current: Int): Int {
    if (this == 1)
      return 0

    while (true) {
      val v = random()
      if (v != current)
        return v
    }
  }

  private fun BODY.displayQuestions(
    user: User?,
    browserSession: BrowserSession?,
    challenge: Challenge,
    funcInfo: FunctionInfo,
  ) =
    div {
      style = "margin-top:2em; margin-left:2em"

      table {
        tr {
          th {
            colSpan = "2"
            style = "color: $HEADER_COLOR"
            +"Function Call"
            rawHtml(nbsp.text)
          }
          th {
            colSpan = "2"
            style = "color: $HEADER_COLOR"
            +"Return Value"
          }
        }

        val topFocus = "topFocus"
        val bottomFocus = "bottomFocus"
        val offset = 5 // The login dialog takes tabIndex values 1-4
        val previousResponses = fetchPreviousResponses(user, browserSession, challenge)

        // This will cause shift tab to go to bottom input element
        span {
          tabIndex = "5"
          onFocus = "document.querySelector('.$bottomFocus').focus()"
        }

        val cnt = funcInfo.invocationCount

        funcInfo.invocations.withIndex()
          .forEach { (i, invocation) ->
            tr {
              td(classes = FUNC_COL) {
                +invocation.value
                // Pad short invocation calls
                val minLength = 10
                if (invocation.value.length < minLength)
                  rawHtml(" " + List(minLength - invocation.value.length) { nbsp.text }.joinToString(" "))
              }
              td(classes = ARROW) { rawHtml("&rarr;") }
              td {
                val cls =
                  when (i) {
                    cnt - 1 -> " $bottomFocus"
                    0 -> " $topFocus"
                    else -> ""
                  }
                textInput(classes = USER_RESP + cls) {
                  id = "$RESP$i"

                  if (user.isNull() || user.enrolledClassCode.isNotEnabled)
                    onKeyDown = "$PROCESS_USER_ANSWERS_FUNC(event, ${funcInfo.questionCount})"
                  else
                    onFocusOut = "$PROCESS_USER_ANSWERS_FUNC(null, ${funcInfo.questionCount})"

                  val response = previousResponses[invocation.value] ?: ""
                  if (response.isNotBlank())
                    value = response
                  else
                    placeholder = funcInfo.placeHolder

                  // See: http://bluegalaxy.info/codewalk/2018/08/04/javascript-how-to-create-looping-tabindex-cycle/
                  // We want the first input element to be 2
                  tabIndex = (i + offset).toString()
                  if (i == 0)
                    autoFocus = true
                }
              }
              td(classes = FEEDBACK) { id = "$FEEDBACK_ID$i" }
              td(classes = HINT) { id = "$HINT_ID$i" }
            }
          }
        // This will cause tab to circle back to top input element
        span {
          tabIndex = (cnt + offset).toString()
          onFocus = "document.querySelector('.$topFocus').focus()"
        }
      }

      this@displayQuestions.processAnswers(funcInfo, challenge)
      this@displayQuestions.likeDislike(user, browserSession, challenge)
      this@displayQuestions.otherLinks(challenge)
      this@displayQuestions.clearChallengeAnswerHistoryOption(user, browserSession, challenge)
    }

  private fun BODY.enableWebSockets(classCode: ClassCode, challengeMd5: ChallengeMd5) {
    script {
      rawHtml(
        """
        function sleep(ms) {
          return new Promise(resolve => setTimeout(resolve, ms));
        }

        var cnt = 0;
        var firstTime = true;
        var connected = false;
        function connect() {
          var wshost = location.origin;
          if (wshost.startsWith('https:'))
            wshost = wshost.replace(/^https:/, 'wss:');
          else
            wshost = wshost.replace(/^http:/, 'ws:');

          var wsurl = wshost + '$WS_ROOT$CHALLENGE_ENDPOINT/'+encodeURIComponent('$classCode')+'/'+encodeURIComponent('$challengeMd5');
          var ws = new WebSocket(wsurl);

          ws.onopen = function (event) {
            //console.log("WebSocket connected.");
            firstTime = false;
            document.getElementById('$PING_LABEL').innerText = 'Connected';
            document.getElementById('$PING_MSG').innerText = '';
            ws.send("$classCode");
          };

          ws.onclose = function (event) {
            //console.log('WebSocket closed. Reconnect will be attempted in 1 second.', event.reason);
            var msg = 'Connecting';
            if (!firstTime)
              msg = 'Reconnecting';
            for (i = 0; i < cnt%4; i++)
              msg += '.'
            document.getElementById('$PING_LABEL').innerText = msg;
            document.getElementById('$PING_MSG').innerText = '';
            setTimeout(function() {
              cnt+=1;
              connect();
            }, 1000);
          }

          ws.onerror = function(err) {
            //console.error(err)
            ws.close();
          };

          ws.onmessage = function (event) {
            var obj = JSON.parse(event.data);

            if (obj.hasOwnProperty("type") && obj.type == "$PING_CODE") {
              document.getElementById('$PING_LABEL').innerText = 'Connection time: ';
              document.getElementById('$PING_MSG').innerText = obj.msg;
            }
            else if (obj.hasOwnProperty("type") && obj.type == "$LIKE_DISLIKE_CODE") {
              document.getElementById(obj.userId + '-$LIKE_DISLIKE_SPAN').innerHTML = obj.likeDislike;
            }
            else {
              var name = document.getElementById(obj.userId + '-$NAME_TD');
              name.style.backgroundColor = obj.complete ? '$CORRECT_COLOR' : '$INCOMPLETE_COLOR';

              document.getElementById(obj.userId + '-$NUM_CORRECTION_SPAN').innerText = obj.numCorrect;

              var prefix = obj.userId + '-' + obj.history.invocation;

              var answers = document.getElementById(prefix + '-$ANSWER_TD')
              answers.style.backgroundColor = obj.history.correct ? '$CORRECT_COLOR'
                                                                  : (obj.history.answers.length > 0 ? '$WRONG_COLOR'
                                                                                                    : '$INCOMPLETE_COLOR');
              document.getElementById(prefix + '-$ANSWER_SPAN').innerHTML = obj.history.answers;
            }
          };
        }
        connect();
        """.trimIndent(),
      )
    }
  }

  private fun BODY.displayStudentProgress(
    challenge: Challenge,
    maxHistoryLength: Int,
    funcInfo: FunctionInfo,
    classCode: ClassCode,
    enrollees: List<User>,
  ) =
    div {
      style = "margin-top:2em"

      val languageName = challenge.languageType.languageName
      val groupName = challenge.groupName

      h3 {
        style = "margin-left: 5px; color: $HEADER_COLOR"
        a(classes = UNDERLINE) {
          href = classSummaryEndpoint(classCode, languageName, groupName)
          +classCode.toDisplayString()
        }
        +enrolleesDesc(enrollees)
      }

      if (enrollees.isNotEmpty()) {
        table {
          style = "width:100%; border-spacing: 5px 10px"

          tr {
            th {
              style = "width:15%; white-space:nowrap; text-align:left; color: $HEADER_COLOR"
              +"Student"
            }
            funcInfo.invocations
              .forEach { invocation ->
                th {
                  style = "text-align:left; color: $HEADER_COLOR"
                  +(invocation.value.run { substring(indexOf("(")) })
                }
              }
          }

          enrollees
            .forEach { enrollee ->
              val numCalls = funcInfo.invocationCount
              var numCorrect = 0
              val results =
                funcInfo.invocations
                  .map { invocation ->
                    val history =
                      readonlyTx {
                        val historyMd5 = challenge.md5(invocation)
                        enrollee.answerHistory(historyMd5, invocation)
                      }

                    if (history.correct)
                      numCorrect++

                    invocation to history
                  }

              val allCorrect = numCorrect == numCalls

              tr(classes = DASHBOARD) {
                td(classes = DASHBOARD) {
                  id = "${enrollee.userId}-$NAME_TD"
                  val color = if (allCorrect) CORRECT_COLOR else INCOMPLETE_COLOR
                  style = "width:15%;white-space:nowrap; background-color:$color"
                  span {
                    id = "${enrollee.userId}-$NUM_CORRECTION_SPAN"
                    +numCorrect.toString()
                  }
                  +"/$numCalls"
                  rawHtml(nbsp.text)
                  +enrollee.fullName.value
                  rawHtml(nbsp.text)
                  // TODO Optimize this to a single query and grab all values at once
                  span {
                    id = "${enrollee.userId}-$LIKE_DISLIKE_SPAN"
                    rawHtml(enrollee.likeDislikeEmoji(challenge))
                  }
                }

                results
                  .forEach { (invocation, history) ->
                    td(classes = DASHBOARD) {
                      id = "${enrollee.userId}-$invocation-$ANSWER_TD"
                      val color =
                        if (history.correct) {
                          CORRECT_COLOR
                        } else {
                          if (history.answers.isNotEmpty()) WRONG_COLOR else INCOMPLETE_COLOR
                        }
                      style = "background-color:$color"
                      span {
                        id = "${enrollee.userId}-$invocation-$ANSWER_SPAN"
                        history.answers.asReversed().take(maxHistoryLength).forEach { answer ->
                          +answer
                          br
                        }
                      }
                    }
                  }
              }
            }
        }
      }
    }

  private fun BODY.processAnswers(funcInfo: FunctionInfo, challenge: Challenge) {
    div {
      style = "margin-top:2em"
      table {
        tr {
          td {
            button(classes = CHECK_ANSWERS) {
              onClick = "$PROCESS_USER_ANSWERS_FUNC(null, ${funcInfo.questionCount})"
              +"Check My Answers"
            }
          }

          td {
            style = "vertical-align:middle"
            span {
              style = "margin-left:1em"
              id = SPINNER_ID
            }
          }

          td {
            val challengeName = challenge.challengeName
            val challengeGroup = challenge.challengeGroup
            val pos = challengeGroup.indexOf(challengeName)

            span {
              id = NEXTPREVCHANCE_ID
              style = "display:none"
              this@processAnswers.nextPrevChance(pos, challengeGroup.challenges, false)
            }
          }

          td {
            style = "vertical-align:middle"
            span(classes = STATUS) { id = STATUS_ID }
            span(classes = SUCCESS) { id = SUCCESS_ID }
          }
        }
      }
    }
  }

  private fun BODY.nextPrevChance(pos: Int, challenges: List<Challenge>, includePrev: Boolean) {
    if (includePrev) {
      "prev".also {
        if (pos == 0)
          +it
        else
          a {
            href = "./${challenges[pos - 1].challengeName.value}"
            +it
          }
      }

      rawHtml("${nbsp.text} | ${nbsp.text}")
    }

    "next".also {
      if (pos == challenges.size - 1)
        +it
      else
        a {
          href = "./${challenges[pos + 1].challengeName.value}"
          +it
        }
    }

    rawHtml("${nbsp.text} | ${nbsp.text}")

    "chance".also {
      if (challenges.size == 1)
        +it
      else
        a {
          href = "./${challenges[challenges.size.chance(pos)].challengeName.value}"
          +it
        }
    }
  }

  private fun BODY.likeDislike(user: User?, browserSession: BrowserSession?, challenge: Challenge) {
    if (!isDbmsEnabled())
      return

    val likeDislikeVal =
      when {
        user.isNotNull() -> user.likeDislike(challenge)
        browserSession.isNotNull() -> browserSession.likeDislike(challenge)
        else -> 0
      }

    p {
      table {
        val imgSize = "30"
        tr {
          td {
            id = LIKE_CLEAR
            style = "display:${if (likeDislikeVal == 0 || likeDislikeVal == 2) "inline" else "none"}"
            button(classes = LIKE_BUTTONS) {
              onClick = "$LIKE_DISLIKE_FUNC(${LIKE_CLEAR.toDoubleQuoted()})"
              img {
                height = imgSize
                src = pathOf(STATIC_ROOT, LIKE_CLEAR_FILE)
              }
            }
          }
          td {
            id = LIKE_COLOR
            style = "display:${if (likeDislikeVal == 1) "inline" else "none"}"
            button(classes = LIKE_BUTTONS) {
              onClick = "$LIKE_DISLIKE_FUNC(${LIKE_COLOR.toDoubleQuoted()})"
              img {
                height = imgSize
                src = pathOf(STATIC_ROOT, LIKE_COLOR_FILE)
              }
            }
          }
          td {
            id = DISLIKE_CLEAR
            style = "display:${if (likeDislikeVal == 0 || likeDislikeVal == 1) "inline" else "none"}"
            button(classes = LIKE_BUTTONS) {
              onClick = "$LIKE_DISLIKE_FUNC(${DISLIKE_CLEAR.toDoubleQuoted()})"
              img {
                height = imgSize
                src = pathOf(STATIC_ROOT, DISLIKE_CLEAR_FILE)
              }
            }
          }
          td {
            id = DISLIKE_COLOR
            style = "display:${if (likeDislikeVal == 2) "inline" else "none"}"
            button(classes = LIKE_BUTTONS) {
              onClick = "$LIKE_DISLIKE_FUNC(${DISLIKE_COLOR.toDoubleQuoted()})"
              img {
                height = imgSize
                src = pathOf(STATIC_ROOT, DISLIKE_COLOR_FILE)
              }
            }
          }

          td {
            style = "vertical-align:middle"
            span {
              style = "margin-left:1em"
              id = LIKE_SPINNER_ID
            }
          }

          td {
            style = "vertical-align:middle"
            span(classes = STATUS) { id = LIKE_STATUS_ID }
          }
        }
      }
    }
  }

  private fun BODY.otherLinks(challenge: Challenge) {
    val languageType = challenge.languageType
    val groupName = challenge.groupName
    val challengeName = challenge.challengeName

    p(classes = EXPERIMENT) {
      +"Experiment with this code on "
      this@otherLinks.addLink("Gitpod.io", "https://gitpod.io/#${challenge.gitpodUrl}", true)
      if (languageType.isKotlin) {
        +" or as a "
        this@otherLinks.addLink("Kotlin Playground", pathOf(PLAYGROUND_ROOT, groupName, challengeName), false)
      }
    }

    if (challenge.codingBatEquiv.isNotEmpty() && (languageType.isJava || languageType.isPython)) {
      p(classes = CODINGBAT) {
        +"Work on a similar problem on "
        this@otherLinks.addLink("CodingBat.com", "https://codingbat.com/prob/${challenge.codingBatEquiv}", true)
      }
    }
  }

  private fun BODY.clearChallengeAnswerHistoryOption(
    user: User?,
    browserSession: BrowserSession?,
    challenge: Challenge,
  ) {
    val languageName = challenge.languageType.languageName
    val groupName = challenge.groupName
    val challengeName = challenge.challengeName
    val correctAnswersKey = correctAnswersKey(user, browserSession, languageName, groupName, challengeName)
    val challengeAnswersKey = challengeAnswersKey(user, browserSession, languageName, groupName, challengeName)

    if (!isDbmsEnabled())
      return

    form {
      style = "margin:0"
      action = CLEAR_CHALLENGE_ANSWERS_ENDPOINT
      method = FormMethod.post
      onSubmit = """return confirm('Are you sure you want to clear your previous answers for "$challengeName"?')"""
      hiddenInput {
        name = LANGUAGE_NAME_PARAM
        value = languageName.value
      }
      hiddenInput {
        name = GROUP_NAME_PARAM
        value = groupName.value
      }
      hiddenInput {
        name = CHALLENGE_NAME_PARAM
        value = challengeName.value
      }
      hiddenInput {
        name = CORRECT_ANSWERS_PARAM
        value = correctAnswersKey
      }
      hiddenInput {
        name = CHALLENGE_ANSWERS_PARAM
        value = challengeAnswersKey
      }
      submitInput {
        style = "vertical-align:middle; margin-top:1; margin-bottom:0"
        value = "Clear answer history"
      }
    }
  }

  private fun HEAD.removePrismShadow() {
    // Remove the prism shadow
    style {
      rawHtml(
        """
          pre[class*="language-"]:before,
          pre[class*="language-"]:after { display:none; }
        """.trimIndent(),
      )
    }
  }

  fun fetchPreviousResponses(user: User?, browserSession: BrowserSession?, challenge: Challenge) =
    when {
      !isDbmsEnabled() -> emptyMap
      user.isNotNull() ->
        readonlyTx {
          with(UserChallengeInfoTable) {
            select(answersJson)
              .where { (userRef eq user.userDbmsId) and (md5 eq challenge.md5()) }
              .map { it[0] as String }
              .firstOrNull()
              ?.let { Json.decodeFromString<Map<String, String>>(it) }
              ?: emptyMap
          }
        }

      browserSession.isNotNull() ->
        transaction {
          with(SessionChallengeInfoTable) {
            select(answersJson)
              .where { (sessionRef eq browserSession.queryOrCreateSessionDbmsId()) and (md5 eq challenge.md5()) }
              .map { it[0] as String }
              .firstOrNull()
              ?.let { Json.decodeFromString<Map<String, String>>(it) }
              ?: emptyMap
          }
        }

      else -> emptyMap
    }
}
