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
import com.github.pambrose.common.util.toPath
import com.github.readingbat.RedisPool.redisAction
import com.github.readingbat.config.ClientSession
import com.github.readingbat.dsl.Challenge
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.Answers.processAnswers
import com.github.readingbat.misc.CSSNames.arrow
import com.github.readingbat.misc.CSSNames.challengeDesc
import com.github.readingbat.misc.CSSNames.checkAnswers
import com.github.readingbat.misc.CSSNames.checkBar
import com.github.readingbat.misc.CSSNames.codeBlock
import com.github.readingbat.misc.CSSNames.feedback
import com.github.readingbat.misc.CSSNames.funcCol
import com.github.readingbat.misc.CSSNames.refs
import com.github.readingbat.misc.CSSNames.spinner
import com.github.readingbat.misc.CSSNames.status
import com.github.readingbat.misc.CSSNames.tabs
import com.github.readingbat.misc.CSSNames.userAnswers
import com.github.readingbat.misc.CSSNames.userResp
import com.github.readingbat.misc.Constants.challengeRoot
import com.github.readingbat.misc.Constants.playground
import com.github.readingbat.misc.Constants.staticRoot
import com.github.readingbat.misc.addScript
import io.ktor.auth.UserIdPrincipal
import io.ktor.http.ContentType.Text.CSS
import kotlinx.html.*
import kotlinx.html.Entities.nbsp
import kotlinx.html.stream.createHTML
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}
private val emptyAnswerMap = mutableMapOf<String, String>()
private const val spinnerCss = "https://cdnjs.cloudflare.com/ajax/libs/font-awesome/4.7.0/css/font-awesome.min.css"

internal fun challengePage(principal: UserIdPrincipal?,
                           loginAttempted: Boolean,
                           readingBatContent: ReadingBatContent,
                           challenge: Challenge,
                           clientSession: ClientSession?) =
  createHTML()
    .html {
      val languageType = challenge.languageType
      val languageName = languageType.lowerName
      val groupName = challenge.groupName
      val challengeName = challenge.challengeName
      val funcInfo = challenge.funcInfo(readingBatContent)
      val loginPath = listOf(languageName, groupName, challengeName).toPath(false, false)

      head {
        link { rel = "stylesheet"; href = spinnerCss }
        link { rel = "stylesheet"; href = "/$staticRoot/$languageName-prism.css"; type = CSS.toString() }

        script(type = ScriptType.textJavaScript) { addScript(languageName, groupName, challengeName) }

        removePrismShadow()
        headDefault(readingBatContent)
      }

      body {
        bodyHeader(principal, loginAttempted, readingBatContent, languageType, loginPath)

        div(classes = tabs) {
          h2 {
            val groupPath = listOf(challengeRoot, languageName, groupName).toPath(true, false)
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

          div(classes = userAnswers) {
            table {
              tr { th { +"Function Call" }; th { +"" }; th { +"Return Value" }; th { +"" } }

              var previousAnswers = emptyAnswerMap
              if (clientSession != null) {
                redisAction { redis ->
                  val key = clientSession.challengeKey(languageName, groupName, challenge.challengeName)
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

            div(classes = checkBar) {
              table {
                tr {
                  td {
                    button(classes = checkAnswers) {
                      onClick = "$processAnswers(null, ${funcInfo.answers.size})"; +"Check My Answers!"
                    }
                  }
                  td { span(classes = spinner) { id = spinner } }
                  td { span(classes = status) { id = status } }
                }
              }
            }

            p(classes = refs) {
              +"Experiment with this code on "
              this@body.addLink("Gitpod.io", "https://gitpod.io/#${challenge.gitpodUrl}", true)
              if (languageType.isKotlin()) {
                +" or as a "
                this@body.addLink("Kotlin Playground", "/$playground/$groupName/$challengeName", false)
              }
              +"."
            }

            if (challenge.codingBatEquiv.isNotEmpty() && (languageType.isJava() || languageType.isPython())) {
              p(classes = refs) {
                +"Work on a similar problem on "
                this@body.addLink("CodingBat.com", "https://codingbat.com/prob/${challenge.codingBatEquiv}", true)
                +"."
              }
            }
          }
        }

        backLink(challengeRoot, languageName, groupName)

        script { src = "/$staticRoot/$languageName-prism.js" }
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