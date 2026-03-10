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
import com.github.pambrose.common.util.pathOf
import com.github.pambrose.common.util.toDoubleQuoted
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
import com.github.readingbat.common.TwClasses
import com.github.readingbat.common.User
import com.github.readingbat.common.User.Companion.queryActiveTeachingClassCode
import com.github.readingbat.common.WsProtocol
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
import com.github.readingbat.posts.ChallengeHistory
import com.github.readingbat.server.ChallengeMd5
import com.github.readingbat.server.ServerUtils.queryParam
import com.github.readingbat.server.UserChallengeInfoTable
import com.pambrose.common.exposed.get
import com.pambrose.common.exposed.readonlyTx
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
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select

internal object ChallengePage {
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
            displayQuestions(user, challenge, funcInfo)
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

    h2(classes = "mb-1") {
      val groupPath = pathOf(CHALLENGE_ROOT, languageName, groupName)
      this@displayChallenge.addLink(groupName.value.decode(), groupPath)
      span(classes = "px-0.5") {
        rawHtml("&rarr;")
      }
      +challengeName.value
    }

    span(classes = "pl-5") {
      val pos = challengeGroup.indexOf(challengeName)
      this@displayChallenge.nextPrevChance(pos, challenges, true)
    }

    if (challenge.description.isNotBlank())
      div(classes = TwClasses.CHALLENGE_DESC) { rawHtml(challenge.parsedDescription) }

    div(classes = TwClasses.CODE_BLOCK) {
      pre(classes = "line-numbers") {
        code(classes = "language-$languageName") {
          +funcInfo.codeSnippet
        }
      }
    }
  }

  // Make sure we do not return the same challenge on a chance click
  private fun Int.chance(current: Int): Int {
    if (this == 1) return 0
    return (0 until this).filter { it != current }.random()
  }

  private fun BODY.displayQuestions(
    user: User?,
    challenge: Challenge,
    funcInfo: FunctionInfo,
  ) =
    div(classes = "mt-8 ml-8") {
      table {
        tr {
          th(classes = "text-rb-header") {
            colSpan = "2"
            style = "color: $HEADER_COLOR"
            +"Function Call"
            rawHtml(nbsp.text)
          }
          th(classes = "text-rb-header") {
            colSpan = "2"
            style = "color: $HEADER_COLOR"
            +"Return Value"
          }
        }

        val topFocus = "topFocus"
        val bottomFocus = "bottomFocus"
        val offset = 5 // The login dialog takes tabIndex values 1-4
        val previousResponses = fetchPreviousResponses(user, challenge)

        // This will cause shift tab to go to bottom input element
        span {
          tabIndex = "5"
          onFocus = "document.querySelector('.$bottomFocus').focus()"
        }

        val cnt = funcInfo.invocationCount

        funcInfo.invocations.withIndex()
          .forEach { (i, invocation) ->
            tr {
              td(classes = TwClasses.FUNC_COL) {
                +invocation.value
                // Pad short invocation calls
                val minLength = 10
                if (invocation.value.length < minLength)
                  rawHtml(" " + List(minLength - invocation.value.length) { nbsp.text }.joinToString(" "))
              }
              td(classes = TwClasses.ARROW) { rawHtml("&rarr;") }
              td {
                val cls =
                  when (i) {
                    cnt - 1 -> " $bottomFocus"
                    0 -> " $topFocus"
                    else -> ""
                  }
                textInput(classes = TwClasses.USER_RESP + cls) {
                  id = "$RESP$i"

                  if (user == null || user.enrolledClassCode.isNotEnabled)
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
              td(classes = TwClasses.FEEDBACK) { id = "$FEEDBACK_ID$i" }
              td { id = "$HINT_ID$i" }
            }
          }
        // This will cause tab to circle back to top input element
        span {
          tabIndex = (cnt + offset).toString()
          onFocus = "document.querySelector('.$topFocus').focus()"
        }
      }

      this@displayQuestions.processAnswers(funcInfo, challenge)
      this@displayQuestions.likeDislike(user, challenge)
      this@displayQuestions.otherLinks(challenge)
      this@displayQuestions.clearChallengeAnswerHistoryOption(user, challenge)
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
            var typeField = "${WsProtocol.TYPE_FIELD}";
            var msgField = "${WsProtocol.MSG_FIELD}";
            var userIdField = "${WsProtocol.USER_ID_FIELD}";
            var likeDislikeField = "${WsProtocol.LIKE_DISLIKE_FIELD}";
            var completeField = "${WsProtocol.COMPLETE_FIELD}";
            var numCorrectField = "${WsProtocol.NUM_CORRECT_FIELD}";
            var historyField = "${WsProtocol.HISTORY_FIELD}";
            var invocationField = "${WsProtocol.INVOCATION_FIELD}";
            var correctField = "${WsProtocol.CORRECT_FIELD}";
            var answersField = "${WsProtocol.ANSWERS_FIELD}";

            if (obj.hasOwnProperty(typeField) && obj[typeField] == "$PING_CODE") {
              document.getElementById('$PING_LABEL').innerText = 'Connection time: ';
              document.getElementById('$PING_MSG').innerText = obj[msgField];
            }
            else if (obj.hasOwnProperty(typeField) && obj[typeField] == "$LIKE_DISLIKE_CODE") {
              document.getElementById(obj[userIdField] + '-$LIKE_DISLIKE_SPAN').innerHTML = obj[likeDislikeField];
            }
            else {
              var name = document.getElementById(obj[userIdField] + '-$NAME_TD');
              name.style.backgroundColor = obj[completeField] ? '$CORRECT_COLOR' : '$INCOMPLETE_COLOR';

              document.getElementById(obj[userIdField] + '-$NUM_CORRECTION_SPAN').innerText = obj[numCorrectField];

              var prefix = obj[userIdField] + '-' + obj[historyField][invocationField];

              var answers = document.getElementById(prefix + '-$ANSWER_TD')
              answers.style.backgroundColor = obj[historyField][correctField] ? '$CORRECT_COLOR'
                                                                  : (obj[historyField][answersField].length > 0 ? '$WRONG_COLOR'
                                                                                                    : '$INCOMPLETE_COLOR');
              document.getElementById(prefix + '-$ANSWER_SPAN').innerHTML = obj[historyField][answersField];
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
    div(classes = "mt-8") {
      val languageName = challenge.languageType.languageName
      val groupName = challenge.groupName

      h3(classes = "ml-1 text-rb-header") {
        style = "margin-left: 5px; color: $HEADER_COLOR"
        a(classes = TwClasses.UNDERLINE) {
          href = classSummaryEndpoint(classCode, languageName, groupName)
          +classCode.toDisplayString()
        }
        +enrolleesDesc(enrollees)
      }

      if (enrollees.isNotEmpty()) {
        table(classes = "w-full border-separate border-spacing-x-[5px] border-spacing-y-[10px]") {
          tr {
            th(classes = "w-[15%] whitespace-nowrap text-left text-rb-header") {
              style = "width:15%; white-space:nowrap; text-align:left; color: $HEADER_COLOR"
              +"Student"
            }
            funcInfo.invocations
              .forEach { invocation ->
                th(classes = "text-left text-rb-header") {
                  style = "text-align:left; color: $HEADER_COLOR"
                  +(invocation.value.run { substring(indexOf("(")) })
                }
              }
          }

          val challengeMd5s = funcInfo.invocations.map { challenge.md5(it) }

          enrollees
            .forEach { enrollee ->
              val numCalls = funcInfo.invocationCount
              var numCorrect = 0
              val historyMap = enrollee.answerHistoryBulk(challengeMd5s)
              val results =
                funcInfo.invocations
                  .map { invocation ->
                    val historyMd5 = challenge.md5(invocation)
                    val history = historyMap[historyMd5] ?: ChallengeHistory(invocation)

                    if (history.correct)
                      numCorrect++

                    invocation to history
                  }

              val allCorrect = numCorrect == numCalls

              tr(classes = TwClasses.DASHBOARD) {
                td(classes = TwClasses.DASHBOARD) {
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
                  span {
                    id = "${enrollee.userId}-$LIKE_DISLIKE_SPAN"
                    rawHtml(enrollee.likeDislikeEmoji(challenge))
                  }
                }

                results
                  .forEach { (invocation, history) ->
                    td(classes = TwClasses.DASHBOARD) {
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
    div(classes = "mt-8") {
      table {
        tr {
          td {
            button(classes = TwClasses.CHECK_ANSWERS) {
              onClick = "$PROCESS_USER_ANSWERS_FUNC(null, ${funcInfo.questionCount})"
              +"Check My Answers"
            }
          }

          td(classes = "align-middle") {
            span(classes = "ml-4") {
              id = SPINNER_ID
            }
          }

          td {
            val challengeName = challenge.challengeName
            val challengeGroup = challenge.challengeGroup
            val pos = challengeGroup.indexOf(challengeName)

            span(classes = "hidden") {
              id = NEXTPREVCHANCE_ID
              this@processAnswers.nextPrevChance(pos, challengeGroup.challenges, false)
            }
          }

          td(classes = "align-middle") {
            span(classes = TwClasses.STATUS) { id = STATUS_ID }
            span(classes = TwClasses.SUCCESS) { id = SUCCESS_ID }
          }
        }
      }
    }
  }

  private fun BODY.nextPrevChance(pos: Int, challenges: List<Challenge>, includePrev: Boolean) {
    if (includePrev) {
      val prevLabel = "prev"
      if (pos == 0) {
        +prevLabel
      } else {
        a {
          href = "./${challenges[pos - 1].challengeName.value}"
          +prevLabel
        }
      }

      rawHtml("${nbsp.text} | ${nbsp.text}")
    }

    val nextLabel = "next"
    if (pos == challenges.size - 1) {
      +nextLabel
    } else {
      a {
        href = "./${challenges[pos + 1].challengeName.value}"
        +nextLabel
      }
    }

    rawHtml("${nbsp.text} | ${nbsp.text}")

    val chanceLabel = "chance"
    if (challenges.size == 1) {
      +chanceLabel
    } else {
      a {
        href = "./${challenges[challenges.size.chance(pos)].challengeName.value}"
        +chanceLabel
      }
    }
  }

  private fun BODY.likeDislike(user: User?, challenge: Challenge) {
    if (!isDbmsEnabled())
      return

    val likeDislikeVal = user?.likeDislike(challenge) ?: 0

    p {
      table {
        val imgSize = "30"
        tr {
          td {
            id = LIKE_CLEAR
            style = "display:${if (likeDislikeVal == 0 || likeDislikeVal == 2) "inline" else "none"}"
            button(classes = TwClasses.LIKE_BUTTONS) {
              onClick = "$LIKE_DISLIKE_FUNC(${LIKE_CLEAR.toDoubleQuoted()})"
              img {
                style = "height:${imgSize}px; width:${imgSize}px"
                src = pathOf(STATIC_ROOT, LIKE_CLEAR_FILE)
              }
            }
          }
          td {
            id = LIKE_COLOR
            style = "display:${if (likeDislikeVal == 1) "inline" else "none"}"
            button(classes = TwClasses.LIKE_BUTTONS) {
              onClick = "$LIKE_DISLIKE_FUNC(${LIKE_COLOR.toDoubleQuoted()})"
              img {
                style = "height:${imgSize}px; width:${imgSize}px"
                src = pathOf(STATIC_ROOT, LIKE_COLOR_FILE)
              }
            }
          }
          td {
            id = DISLIKE_CLEAR
            style = "display:${if (likeDislikeVal == 0 || likeDislikeVal == 1) "inline" else "none"}"
            button(classes = TwClasses.LIKE_BUTTONS) {
              onClick = "$LIKE_DISLIKE_FUNC(${DISLIKE_CLEAR.toDoubleQuoted()})"
              img {
                style = "height:${imgSize}px; width:${imgSize}px"
                src = pathOf(STATIC_ROOT, DISLIKE_CLEAR_FILE)
              }
            }
          }
          td {
            id = DISLIKE_COLOR
            style = "display:${if (likeDislikeVal == 2) "inline" else "none"}"
            button(classes = TwClasses.LIKE_BUTTONS) {
              onClick = "$LIKE_DISLIKE_FUNC(${DISLIKE_COLOR.toDoubleQuoted()})"
              img {
                style = "height:${imgSize}px; width:${imgSize}px"
                src = pathOf(STATIC_ROOT, DISLIKE_COLOR_FILE)
              }
            }
          }

          td(classes = "align-middle") {
            span(classes = "ml-4") {
              id = LIKE_SPINNER_ID
            }
          }

          td(classes = "align-middle") {
            span(classes = TwClasses.STATUS) { id = LIKE_STATUS_ID }
          }
        }
      }
    }
  }

  private fun BODY.otherLinks(challenge: Challenge) {
    val languageType = challenge.languageType
    val groupName = challenge.groupName
    val challengeName = challenge.challengeName

    p(classes = TwClasses.EXPERIMENT) {
      +"Experiment with this code on "
      this@otherLinks.addLink("Gitpod.io", "https://gitpod.io/#${challenge.gitpodUrl}", true)
      if (languageType.isKotlin) {
        +" or as a "
        this@otherLinks.addLink("Kotlin Playground", pathOf(PLAYGROUND_ROOT, groupName, challengeName), false)
      }
    }

    if (challenge.codingBatEquiv.isNotEmpty() && (languageType.isJava || languageType.isPython)) {
      p(classes = TwClasses.CODING_BAT) {
        +"Work on a similar problem on "
        this@otherLinks.addLink("CodingBat.com", "https://codingbat.com/prob/${challenge.codingBatEquiv}", true)
      }
    }
  }

  private fun BODY.clearChallengeAnswerHistoryOption(
    user: User?,
    challenge: Challenge,
  ) {
    val languageName = challenge.languageType.languageName
    val groupName = challenge.groupName
    val challengeName = challenge.challengeName
    val correctAnswersKey = correctAnswersKey(user, languageName, groupName, challengeName)
    val challengeAnswersKey = challengeAnswersKey(user, languageName, groupName, challengeName)

    if (!isDbmsEnabled())
      return

    form(classes = "m-0") {
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
      submitInput(classes = TwClasses.CLEAR_HISTORY) {
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

  fun fetchPreviousResponses(user: User?, challenge: Challenge) =
    when {
      !isDbmsEnabled() || user == null -> {
        emptyMap
      }

      else -> {
        readonlyTx {
          with(UserChallengeInfoTable) {
            select(answersJson)
              .where { (userRef eq user.userDbmsId) and (md5 eq challenge.md5()) }
              .map { it[0] as String }
              .firstOrNull()
              ?.takeIf { it.isNotBlank() }
              ?.let { Json.decodeFromString<Map<String, String>>(it) }
              ?: emptyMap
          }
        }
      }
    }
}
