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

import com.github.pambrose.common.redis.RedisUtils.withRedisPool
import com.github.pambrose.common.util.decode
import com.github.readingbat.dsl.Challenge
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.BrowserSession
import com.github.readingbat.misc.CSSNames.ARROW
import com.github.readingbat.misc.CSSNames.CHALLENGE_DESC
import com.github.readingbat.misc.CSSNames.CHECK_ANSWERS
import com.github.readingbat.misc.CSSNames.CODE_BLOCK
import com.github.readingbat.misc.CSSNames.FEEDBACK
import com.github.readingbat.misc.CSSNames.FUNC_COL
import com.github.readingbat.misc.CSSNames.REFS
import com.github.readingbat.misc.CSSNames.STATUS
import com.github.readingbat.misc.CSSNames.SUCCESS
import com.github.readingbat.misc.CSSNames.TABS
import com.github.readingbat.misc.CSSNames.USER_RESP
import com.github.readingbat.misc.CheckAnswersJs.checkAnswersScript
import com.github.readingbat.misc.CheckAnswersJs.processAnswers
import com.github.readingbat.misc.Constants.CHALLENGE_ROOT
import com.github.readingbat.misc.Constants.MSG
import com.github.readingbat.misc.Constants.PLAYGROUND_ROOT
import com.github.readingbat.misc.Constants.RESP
import com.github.readingbat.misc.Constants.STATIC_ROOT
import com.github.readingbat.misc.PageUtils.pathOf
import com.github.readingbat.misc.ParameterIds.FEEDBACK_ID
import com.github.readingbat.misc.ParameterIds.SPINNER_ID
import com.github.readingbat.misc.ParameterIds.STATUS_ID
import com.github.readingbat.misc.ParameterIds.SUCCESS_ID
import com.github.readingbat.misc.UserId
import com.github.readingbat.misc.UserId.Companion.lookupPrincipal
import com.github.readingbat.pages.PageCommon.addLink
import com.github.readingbat.pages.PageCommon.backLink
import com.github.readingbat.pages.PageCommon.bodyHeader
import com.github.readingbat.pages.PageCommon.headDefault
import com.github.readingbat.pages.PageCommon.rawHtml
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.fetchPrincipal
import com.github.readingbat.server.queryParam
import io.ktor.application.call
import io.ktor.http.ContentType.Text.CSS
import io.ktor.sessions.get
import io.ktor.sessions.sessions
import kotlinx.html.*
import kotlinx.html.ScriptType.textJavaScript
import kotlinx.html.stream.createHTML
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}
private const val spinnerCss = "https://cdnjs.cloudflare.com/ajax/libs/font-awesome/4.7.0/css/font-awesome.min.css"

internal object ChallengePage {

  fun PipelineCall.challengePage(content: ReadingBatContent,
                                 challenge: Challenge,
                                 loginAttempt: Boolean) =
    createHTML()
      .html {
        val principal = fetchPrincipal(loginAttempt)
        val browserSession = call.sessions.get<BrowserSession>()
        val languageType = challenge.languageType
        val languageName = languageType.lowerName
        val groupName = challenge.groupName
        val challengeName = challenge.challengeName
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
          val msg = queryParam(MSG) ?: ""
          bodyHeader(principal, loginAttempt, content, languageType, loginPath, msg)

          div(classes = TABS) {
            h2 {
              val groupPath = pathOf(CHALLENGE_ROOT, languageName, groupName)
              this@body.addLink(groupName.decode(), groupPath)
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

            div {
              style = "margin-top:2em; margin-left:2em;"
              table {
                tr { th { +"Function Call" }; th { +"" }; th { +"Return Value" }; th { +"" } }

                var previousAnswers = mutableMapOf<String, String>()

                withRedisPool { redis ->
                  val userId: UserId? = lookupPrincipal(principal, redis)
                  val key =
                    userId?.challengeKey(languageName, groupName, challenge.challengeName)
                      ?: browserSession?.challengeKey(languageName, groupName, challenge.challengeName)
                      ?: ""

                  if (redis != null && key.isNotEmpty()) {
                    logger.debug { "Fetching: $key" }
                    previousAnswers = redis.hgetAll(key)
                  }
                }

                funcInfo.arguments.indices.forEach { i ->
                  tr {
                    val args = funcInfo.arguments[i]
                    td(classes = FUNC_COL) { +args }
                    td(classes = ARROW) { rawHtml("&rarr;") }
                    td {
                      textInput(classes = USER_RESP) {
                        id = "$RESP$i"
                        onKeyPress = "$processAnswers(event, ${funcInfo.answers.size})"
                        if (previousAnswers[args] != null)
                          value = previousAnswers[args] ?: ""
                        else
                          placeholder = funcInfo.placeHolder()
                      }
                    }
                    td(classes = FEEDBACK) { id = "$FEEDBACK_ID$i" }
                  }
                }
              }

              div {
                style = "margin-top:2em;"
                table {
                  tr {
                    td {
                      button(classes = CHECK_ANSWERS) {
                        onClick = "$processAnswers(null, ${funcInfo.answers.size});"; +"Check My Answers!"
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

              p(classes = REFS) {
                +"Experiment with this code on "
                this@body.addLink("Gitpod.io", "https://gitpod.io/#${challenge.gitpodUrl}", true)
                if (languageType.isKotlin()) {
                  +" or as a "
                  this@body.addLink("Kotlin Playground", pathOf(PLAYGROUND_ROOT, groupName, challengeName), false)
                }
              }

              if (challenge.codingBatEquiv.isNotEmpty() && (languageType.isJava() || languageType.isPython())) {
                p(classes = REFS) {
                  +"Work on a similar problem on "
                  this@body.addLink("CodingBat.com", "https://codingbat.com/prob/${challenge.codingBatEquiv}", true)
                }
              }
            }
          }

          backLink(CHALLENGE_ROOT, languageName, groupName)

          script { src = "$STATIC_ROOT/$languageName-prism.js" }
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