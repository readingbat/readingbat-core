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

import com.github.pambrose.common.util.*
import com.github.readingbat.common.*
import com.github.readingbat.common.CSSNames.ARROW
import com.github.readingbat.common.CSSNames.CHALLENGE_DESC
import com.github.readingbat.common.CSSNames.CHECK_ANSWERS
import com.github.readingbat.common.CSSNames.CODE_BLOCK
import com.github.readingbat.common.CSSNames.CODINGBAT
import com.github.readingbat.common.CSSNames.DASHBOARD
import com.github.readingbat.common.CSSNames.EXPERIMENT
import com.github.readingbat.common.CSSNames.FEEDBACK
import com.github.readingbat.common.CSSNames.FUNC_COL
import com.github.readingbat.common.CSSNames.HINT
import com.github.readingbat.common.CSSNames.LIKE_BUTTONS
import com.github.readingbat.common.CSSNames.STATUS
import com.github.readingbat.common.CSSNames.SUCCESS
import com.github.readingbat.common.CSSNames.USER_RESP
import com.github.readingbat.common.CommonUtils.pathOf
import com.github.readingbat.common.Constants.CORRECT_COLOR
import com.github.readingbat.common.Constants.DBMS_DOWN
import com.github.readingbat.common.Constants.INCOMPLETE_COLOR
import com.github.readingbat.common.Constants.LIKE_DISLIKE_JS_FUNC
import com.github.readingbat.common.Constants.MSG
import com.github.readingbat.common.Constants.PING_CODE
import com.github.readingbat.common.Constants.PROCESS_USER_ANSWERS_JS_FUNC
import com.github.readingbat.common.Constants.RESP
import com.github.readingbat.common.Constants.WRONG_COLOR
import com.github.readingbat.common.Endpoints.CHALLENGE_ENDPOINT
import com.github.readingbat.common.Endpoints.CHALLENGE_ROOT
import com.github.readingbat.common.Endpoints.CLEAR_CHALLENGE_ANSWERS_ENDPOINT
import com.github.readingbat.common.Endpoints.PLAYGROUND_ROOT
import com.github.readingbat.common.Endpoints.STATIC_ROOT
import com.github.readingbat.common.FormFields.CHALLENGE_ANSWERS_KEY
import com.github.readingbat.common.FormFields.CHALLENGE_NAME_KEY
import com.github.readingbat.common.FormFields.GROUP_NAME_KEY
import com.github.readingbat.common.FormFields.LANGUAGE_NAME_KEY
import com.github.readingbat.common.KeyConstants.CORRECT_ANSWERS_KEY
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
import com.github.readingbat.common.User.Companion.challengeAnswersKey
import com.github.readingbat.common.User.Companion.correctAnswersKey
import com.github.readingbat.common.User.Companion.fetchActiveClassCode
import com.github.readingbat.common.User.Companion.fetchPreviousAnswers
import com.github.readingbat.common.User.Companion.gson
import com.github.readingbat.common.User.Companion.likeDislikeKey
import com.github.readingbat.dsl.Challenge
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.pages.CheckAnswersJs.checkAnswersScript
import com.github.readingbat.pages.LikeDislikeJs.likeDislikeScript
import com.github.readingbat.pages.PageUtils.addLink
import com.github.readingbat.pages.PageUtils.backLink
import com.github.readingbat.pages.PageUtils.bodyHeader
import com.github.readingbat.pages.PageUtils.headDefault
import com.github.readingbat.pages.PageUtils.rawHtml
import com.github.readingbat.posts.ChallengeHistory
import com.github.readingbat.server.ChallengeMd5
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.ServerUtils.queryParam
import io.ktor.application.*
import io.ktor.http.ContentType.Text.CSS
import io.ktor.sessions.*
import kotlinx.html.BODY
import kotlinx.html.Entities.nbsp
import kotlinx.html.FormMethod
import kotlinx.html.HEAD
import kotlinx.html.InputType
import kotlinx.html.ScriptType.textJavaScript
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.br
import kotlinx.html.button
import kotlinx.html.code
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.id
import kotlinx.html.img
import kotlinx.html.input
import kotlinx.html.link
import kotlinx.html.onClick
import kotlinx.html.onKeyDown
import kotlinx.html.onSubmit
import kotlinx.html.p
import kotlinx.html.pre
import kotlinx.html.script
import kotlinx.html.span
import kotlinx.html.stream.createHTML
import kotlinx.html.style
import kotlinx.html.table
import kotlinx.html.td
import kotlinx.html.textInput
import kotlinx.html.th
import kotlinx.html.tr
import mu.KLogging
import redis.clients.jedis.Jedis

internal object ChallengePage : KLogging() {
  private const val spinnerCss = "https://cdnjs.cloudflare.com/ajax/libs/font-awesome/4.7.0/css/font-awesome.min.css"
  private const val nameTd = "nameTd"
  private const val answersTd = "answersTd"
  private const val answersSpan = "answersSpan"
  private const val numCorrectSpan = "numCorrectSpan"
  private const val pingMsg = "pingMsg"
  internal const val headerColor = "#419DC1"

  fun PipelineCall.challengePage(content: ReadingBatContent,
                                 user: User?,
                                 challenge: Challenge,
                                 loginAttempt: Boolean,
                                 redis: Jedis?) =
    createHTML()
      .html {
        val browserSession = call.sessions.get<BrowserSession>()
        val languageType = challenge.languageType
        val languageName = languageType.languageName
        val groupName = challenge.groupName
        val challengeName = challenge.challengeName
        val funcInfo = challenge.functionInfo(content)
        val loginPath = pathOf(CHALLENGE_ROOT, languageName, groupName, challengeName)
        val activeClassCode = user.fetchActiveClassCode(redis)
        val enrollees = activeClassCode.fetchEnrollees(redis)

        head {
          link { rel = "stylesheet"; href = spinnerCss }
          link { rel = "stylesheet"; href = "$STATIC_ROOT/$languageName-prism.css"; type = CSS.toString() }

          script(type = textJavaScript) { checkAnswersScript(languageName, groupName, challengeName) }
          script(type = textJavaScript) { likeDislikeScript(languageName, groupName, challengeName) }

          removePrismShadow()
          headDefault(content)
        }

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

          displayChallenge(challenge, funcInfo)

          if (activeClassCode.isNotEnabled)
            displayQuestions(user, browserSession, challenge, funcInfo, redis)
          else {
            if (redis.isNull()) {
              p { +DBMS_DOWN.value }
            }
            else {
              displayStudentProgress(challenge, content.maxHistoryLength, funcInfo, activeClassCode, enrollees, redis)
              p { +"Connection time: "; span { id = pingMsg } }
            }
          }

          backLink(CHALLENGE_ROOT, languageName.value, groupName.value)

          script { src = "$STATIC_ROOT/$languageName-prism.js" }

          if (activeClassCode.isEnabled && enrollees.isNotEmpty())
            enableWebSockets(activeClassCode, funcInfo.challengeMd5)
        }
      }

  private fun BODY.displayChallenge(challenge: Challenge, funcInfo: FunctionInfo) {
    val languageName = challenge.languageType.languageName
    val groupName = challenge.groupName
    val challengeName = challenge.challengeName
    val challengeGroup = challenge.challengeGroup
    val challenges = challenge.challengeGroup.challenges

    h2 {
      style = "margin-bottom:5px;"
      val groupPath = pathOf(CHALLENGE_ROOT, languageName, groupName)
      this@displayChallenge.addLink(groupName.value.decode(), groupPath)
      span { style = "padding-left:2px; padding-right:2px;"; rawHtml("&rarr;") }
      +challengeName.value
    }

    span {
      style = "padding-left:20px;"
      val pos = challengeGroup.indexOf(challengeName)
      this@displayChallenge.nextPrevChance(pos, challenges, true)
    }

    if (challenge.description.isNotBlank())
      div(classes = CHALLENGE_DESC) { rawHtml(challenge.parsedDescription) }

    div(classes = CODE_BLOCK) {
      pre(classes = "line-numbers") {
        code(classes = "language-$languageName") { +funcInfo.codeSnippet }
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

  private fun BODY.displayQuestions(user: User?,
                                    browserSession: BrowserSession?,
                                    challenge: Challenge,
                                    funcInfo: FunctionInfo,
                                    redis: Jedis?) =
    div {
      style = "margin-top:2em; margin-left:2em;"
      table {
        tr {
          th {
            colSpan = "2"
            style = "color: $headerColor"
            +"Function Call"
            rawHtml(nbsp.text)
          }
          th {
            colSpan = "2"
            style = "color: $headerColor"
            +"Return Value"
          }
        }

        val correctAnswers = user.fetchPreviousAnswers(challenge, browserSession, redis)

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
                textInput(classes = USER_RESP) {
                  id = "$RESP$i"
                  onKeyDown = "$PROCESS_USER_ANSWERS_JS_FUNC(event, ${funcInfo.correctAnswers.size})"
                  val answer = correctAnswers[invocation.value] ?: ""
                  if (answer.isNotBlank())
                    value = answer
                  else
                    placeholder = funcInfo.placeHolder
                }
              }
              td(classes = FEEDBACK) { id = "$FEEDBACK_ID$i" }
              td(classes = HINT) { id = "$HINT_ID$i" }
            }
          }
      }

      this@displayQuestions.processAnswers(funcInfo, challenge)
      if (redis.isNotNull())
        this@displayQuestions.likeDislike(user, browserSession, challenge, redis)
      this@displayQuestions.otherLinks(challenge)
      if (redis.isNotNull())
        this@displayQuestions.clearChallengeAnswerHistoryOption(user, browserSession, challenge)
    }

  private fun BODY.enableWebSockets(classCode: ClassCode, challengeMd5: ChallengeMd5) {
    script {
      rawHtml(
        """
          var wshost = location.origin;
          if (wshost.startsWith('https:'))
            wshost = wshost.replace(/^https:/, 'wss:');
          else
            wshost = wshost.replace(/^http:/, 'ws:');

          var wsurl = wshost + '$CHALLENGE_ENDPOINT/'+encodeURIComponent('$classCode')+'/'+encodeURIComponent('$challengeMd5');
 
          var ws = new WebSocket(wsurl);
          
          ws.onopen = function (event) {
            ws.send("$classCode"); 
          };
          
          ws.onmessage = function (event) {
            var obj = JSON.parse(event.data);

            if (obj.hasOwnProperty("type") && obj.type == "$PING_CODE") {
              document.getElementById('$pingMsg').innerHTML = obj.msg;
            }
            else {
              var name = document.getElementById(obj.userId + '-$nameTd');
              name.style.backgroundColor = obj.complete ? '$CORRECT_COLOR' : '$INCOMPLETE_COLOR';
  
              document.getElementById(obj.userId + '-$numCorrectSpan').innerHTML = obj.numCorrect;
  
              var prefix = obj.userId + '-' + obj.history.invocation;
              
              var answers = document.getElementById(prefix + '-$answersTd')
              answers.style.backgroundColor = obj.history.correct ? '$CORRECT_COLOR' : (obj.history.answers.length > 0 ? '$WRONG_COLOR' : '$INCOMPLETE_COLOR');
  
              document.getElementById(prefix + '-$answersSpan').innerHTML = obj.history.answers;
            }
          };
        """.trimIndent())
    }
  }

  private fun BODY.displayStudentProgress(challenge: Challenge,
                                          maxHistoryLength: Int,
                                          funcInfo: FunctionInfo,
                                          activeClassCode: ClassCode,
                                          enrollees: List<User>,
                                          redis: Jedis) =
    div {
      style = "margin-top:2em;"

      val languageName = challenge.languageType.languageName
      val groupName = challenge.groupName
      val challengeName = challenge.challengeName
      val classDesc = activeClassCode.fetchClassDesc(redis, true)

      h3 {
        style = "margin-left: 5px; color: $headerColor"
        +"${if (enrollees.isEmpty()) "No students enrolled in " else "Student progress for "} $classDesc [$activeClassCode]"
      }

      if (enrollees.isNotEmpty()) {
        table {
          style = "width:100%; border-spacing: 5px 10px;"

          tr {
            th { style = "width:15%; white-space:nowrap; text-align:left; color: $headerColor"; +"Student" }
            funcInfo.invocations
              .forEach { invocation ->
                th {
                  style = "text-align:left; color: $headerColor"; +(invocation.value.run { substring(indexOf("(")) })
                }
              }
          }

          enrollees
            .forEach { enrollee ->
              val numChallenges = funcInfo.invocations.size
              var numCorrect = 0
              val results =
                funcInfo.invocations
                  .map { invocation ->
                    val historyKey = enrollee.answerHistoryKey(languageName, groupName, challengeName, invocation)
                    val history =
                      gson.fromJson(redis[historyKey], ChallengeHistory::class.java) ?: ChallengeHistory(invocation)
                    if (history.correct)
                      numCorrect++
                    invocation to history
                  }
              val allCorrect = numCorrect == numChallenges

              tr(classes = DASHBOARD) {
                td(classes = DASHBOARD) {
                  id = "${enrollee.id}-$nameTd"
                  style =
                    "width:15%;white-space:nowrap; background-color:${if (allCorrect) CORRECT_COLOR else INCOMPLETE_COLOR};"

                  span { id = "${enrollee.id}-$numCorrectSpan"; +numCorrect.toString() }
                  +"/$numChallenges"
                  rawHtml(nbsp.text)
                  +enrollee.name(redis)
                }

                results
                  .forEach { (invocation, history) ->
                    td(classes = DASHBOARD) {
                      id = "${enrollee.id}-$invocation-$answersTd"
                      style =
                        "background-color:${if (history.correct) CORRECT_COLOR else (if (history.answers.isNotEmpty()) WRONG_COLOR else INCOMPLETE_COLOR)};"
                      span {
                        id = "${enrollee.id}-$invocation-$answersSpan"
                        history.answers.asReversed().take(maxHistoryLength).forEach { answer -> +answer; br }
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
      style = "margin-top:2em;"
      table {
        tr {
          td {
            button(classes = CHECK_ANSWERS) {
              onClick = "$PROCESS_USER_ANSWERS_JS_FUNC(null, ${funcInfo.correctAnswers.size});"; +"Check My Answers"
            }
          }

          td { style = "vertical-align:middle;"; span { style = "margin-left:1em;"; id = SPINNER_ID } }

          td {
            val challengeName = challenge.challengeName
            val challengeGroup = challenge.challengeGroup
            val pos = challengeGroup.indexOf(challengeName)

            span {
              id = NEXTPREVCHANCE_ID
              style = "display:none;"
              this@processAnswers.nextPrevChance(pos, challengeGroup.challenges, false)
            }
          }

          td {
            style = "vertical-align:middle;"
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
          a { href = "./${challenges[pos - 1].challengeName.value}"; +it }
      }

      rawHtml("${nbsp.text} | ${nbsp.text}")
    }

    "next".also {
      if (pos == challenges.size - 1)
        +it
      else
        a { href = "./${challenges[pos + 1].challengeName.value}"; +it }
    }

    rawHtml("${nbsp.text} | ${nbsp.text}")

    "chance".also {
      if (challenges.size == 1)
        +it
      else
        a { href = "./${challenges[challenges.size.chance(pos)].challengeName.value}"; +it }
    }
  }

  private fun BODY.likeDislike(user: User?, browserSession: BrowserSession?, challenge: Challenge, redis: Jedis) {
    val languageName = challenge.languageType.languageName
    val groupName = challenge.groupName
    val challengeName = challenge.challengeName

    val likeDislikeKey = user.likeDislikeKey(browserSession, languageName, groupName, challengeName)
    val likeDislikeVal = if (likeDislikeKey.isNotEmpty()) redis[likeDislikeKey]?.toInt() ?: 0 else 0

    p {
      table {
        val imgSize = "30"
        tr {
          td {
            id = LIKE_CLEAR
            style = "display:${if (likeDislikeVal == 0 || likeDislikeVal == 2) "inline" else "none" + ";"}"
            button(classes = LIKE_BUTTONS) {
              onClick = "$LIKE_DISLIKE_JS_FUNC(${LIKE_CLEAR.toDoubleQuoted()});"
              img { height = imgSize; src = "$STATIC_ROOT/like-clear.png" }
            }
          }
          td {
            id = LIKE_COLOR
            style = "display:${if (likeDislikeVal == 1) "inline" else "none" + ";"}"
            button(classes = LIKE_BUTTONS) {
              onClick = "$LIKE_DISLIKE_JS_FUNC(${LIKE_COLOR.toDoubleQuoted()});"
              img { height = imgSize; src = "$STATIC_ROOT/like-color.png" }
            }
          }
          td {
            id = DISLIKE_CLEAR
            style = "display:${if (likeDislikeVal == 0 || likeDislikeVal == 1) "inline" else "none" + ";"}"
            button(classes = LIKE_BUTTONS) {
              onClick = "$LIKE_DISLIKE_JS_FUNC(${DISLIKE_CLEAR.toDoubleQuoted()});"
              img { height = imgSize; src = "$STATIC_ROOT/dislike-clear.png" }
            }
          }
          td {
            id = DISLIKE_COLOR
            style = "display:${if (likeDislikeVal == 2) "inline" else "none" + ";"}"
            button(classes = LIKE_BUTTONS) {
              onClick = "$LIKE_DISLIKE_JS_FUNC(${DISLIKE_COLOR.toDoubleQuoted()});"
              img { height = imgSize; src = "$STATIC_ROOT/dislike-color.png" }
            }
          }

          td { style = "vertical-align:middle;"; span { style = "margin-left:1em;"; id = LIKE_SPINNER_ID } }

          td { style = "vertical-align:middle;"; span(classes = STATUS) { id = LIKE_STATUS_ID } }
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

  private fun BODY.clearChallengeAnswerHistoryOption(user: User?,
                                                     browserSession: BrowserSession?,
                                                     challenge: Challenge) {
    val languageName = challenge.languageType.languageName
    val groupName = challenge.groupName
    val challengeName = challenge.challengeName
    val correctAnswersKey = user.correctAnswersKey(browserSession, languageName, groupName, challengeName)
    val challengeAnswersKey = user.challengeAnswersKey(browserSession, languageName, groupName, challengeName)

    form {
      style = "margin:0;"
      action = CLEAR_CHALLENGE_ANSWERS_ENDPOINT
      method = FormMethod.post
      onSubmit = """return confirm('Are you sure you want to clear your previous answers for "$challengeName"?');"""
      input { type = InputType.hidden; name = LANGUAGE_NAME_KEY; value = languageName.value }
      input { type = InputType.hidden; name = GROUP_NAME_KEY; value = groupName.value }
      input { type = InputType.hidden; name = CHALLENGE_NAME_KEY; value = challengeName.value }
      input { type = InputType.hidden; name = CORRECT_ANSWERS_KEY; value = correctAnswersKey }
      input { type = InputType.hidden; name = CHALLENGE_ANSWERS_KEY; value = challengeAnswersKey }
      input {
        style = "vertical-align:middle; margin-top:1; margin-bottom:0;"
        type = InputType.submit; value = "Clear answer history"
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
        """.trimIndent())
    }
  }
}