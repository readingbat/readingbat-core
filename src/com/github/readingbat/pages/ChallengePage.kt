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
import com.github.pambrose.common.util.join
import com.github.readingbat.RedisPool.redisAction
import com.github.readingbat.dsl.Challenge
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.*
import com.github.readingbat.misc.Answers.processAnswers
import com.github.readingbat.misc.CSSNames.arrow
import com.github.readingbat.misc.CSSNames.challengeDesc
import com.github.readingbat.misc.CSSNames.check_answers
import com.github.readingbat.misc.CSSNames.codeBlock
import com.github.readingbat.misc.CSSNames.feedback
import com.github.readingbat.misc.CSSNames.funcCol
import com.github.readingbat.misc.CSSNames.refs
import com.github.readingbat.misc.CSSNames.spinnerId
import com.github.readingbat.misc.CSSNames.status
import com.github.readingbat.misc.CSSNames.statusId
import com.github.readingbat.misc.CSSNames.tabs
import com.github.readingbat.misc.CSSNames.userResp
import com.github.readingbat.misc.Constants.CHALLENGE_ROOT
import com.github.readingbat.misc.Constants.PLAYGROUND_ROOT
import com.github.readingbat.misc.Constants.STATIC_ROOT
import io.ktor.http.ContentType.Text.CSS
import kotlinx.html.*
import kotlinx.html.Entities.nbsp
import kotlinx.html.stream.createHTML
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}
private const val spinnerCss = "https://cdnjs.cloudflare.com/ajax/libs/font-awesome/4.7.0/css/font-awesome.min.css"

internal fun challengePage(principal: UserPrincipal?,
                           loginAttempt: Boolean,
                           content: ReadingBatContent,
                           challenge: Challenge,
                           browserSession: BrowserSession?) =
  createHTML()
    .html {
      val languageType = challenge.languageType
      val languageName = languageType.lowerName
      val groupName = challenge.groupName
      val challengeName = challenge.challengeName
      val funcInfo = challenge.funcInfo(content)
      val loginPath = listOf(languageName, groupName, challengeName).join()

      head {
        link { rel = "stylesheet"; href = spinnerCss }
        link { rel = "stylesheet"; href = "$STATIC_ROOT/$languageName-prism.css"; type = CSS.toString() }

        script(type = ScriptType.textJavaScript) { checkAnswersScript(languageName, groupName, challengeName) }

        removePrismShadow()
        headDefault(content)
      }

      body {
        bodyHeader(principal, loginAttempt, content, languageType, loginPath)

        div(classes = tabs) {
          h2 {
            val groupPath = listOf(CHALLENGE_ROOT, languageName, groupName).join()
            this@body.addLink(groupName.decode(), groupPath)
            rawHtml("${nbsp.text}&rarr;${nbsp.text}")
            +challengeName
          }

          if (challenge.description.isNotEmpty())
            div(classes = challengeDesc) { rawHtml(challenge.parsedDescription) }

          div(classes = codeBlock) {
            pre(classes = "line-numbers") {
              code(classes = "language-$languageName") { +funcInfo.codeSnippet }
            }
          }

          div {
            style = "margin-top:2em; margin-left:2em;"
            table {
              tr { th { +"Function Call" }; th { +"" }; th { +"Return Value" }; th { +"" } }

              var previousAnswers = mutableMapOf<String, String>()

              redisAction { redis ->
                val userId: UserId? = lookupUserId(redis, principal)
                val key =
                  userId?.challengeKey(languageName, groupName, challenge.challengeName)
                    ?: browserSession?.challengeKey(languageName, groupName, challenge.challengeName)
                    ?: ""

                if (key.isNotEmpty()) {
                  logger.debug { "Fetching: $key" }
                  previousAnswers = redis.hgetAll(key)
                }
              }

              funcInfo.arguments.indices.forEach { i ->
                tr {
                  val args = funcInfo.arguments[i]
                  td(classes = funcCol) { +args }
                  td(classes = arrow) { rawHtml("&rarr;") }
                  td {
                    textInput(classes = userResp) {
                      id = "$userResp$i"
                      onKeyPress = "$processAnswers(event, ${funcInfo.answers.size})"
                      if (previousAnswers[args] != null)
                        value = previousAnswers[args] ?: ""
                      else
                        placeholder = funcInfo.placeHolder()
                    }
                  }
                  td(classes = feedback) { id = "$feedback$i" }
                }
              }
            }

            div {
              style = "margin-top:1em;"
              table {
                tr {
                  td {
                    button(classes = check_answers) {
                      onClick = "$processAnswers(null, ${funcInfo.answers.size});"; +"Check My Answers!"
                    }
                  }
                  td { style = "vertical-align:middle;"; span { style = "margin-left:1em;"; id = spinnerId } }
                  td { style = "vertical-align:middle;"; span(classes = status) { id = statusId } }
                }
              }
            }

            p(classes = refs) {
              +"Experiment with this code on "
              this@body.addLink("Gitpod.io", "https://gitpod.io/#${challenge.gitpodUrl}", true)
              if (languageType.isKotlin()) {
                +" or as a "
                this@body.addLink("Kotlin Playground", listOf(PLAYGROUND_ROOT, groupName, challengeName).join(), false)
              }
            }

            if (challenge.codingBatEquiv.isNotEmpty() && (languageType.isJava() || languageType.isPython())) {
              p(classes = refs) {
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