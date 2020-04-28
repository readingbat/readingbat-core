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
import com.github.readingbat.Constants
import com.github.readingbat.addScript
import com.github.readingbat.dsl.Challenge
import com.github.readingbat.dsl.KotlinChallenge
import com.github.readingbat.dsl.LanguageType
import kotlinx.html.*

fun HTML.challengePage(challenge: Challenge) {
  val languageType = challenge.languageType
  val languageName = languageType.lowerName
  val groupName = challenge.groupName
  val name = challenge.name
  val funcArgs = challenge.inputOutput

  head {
    link {
      rel = "stylesheet"; href = "https://cdnjs.cloudflare.com/ajax/libs/font-awesome/4.7.0/css/font-awesome.min.css"
    }
    link {
      rel = "stylesheet"; href = "/${Constants.static}/$languageName-prism.css"; type =
      Constants.cssType
    }

    // Remove the prism shadow
    style {
      rawHtml(
        """
            pre[class*="language-"]:before,
            pre[class*="language-"]:after { display: none; }
          """
             )
    }

    script(type = ScriptType.textJavaScript) { addScript(languageName) }

    headDefault()
  }

  body {
    bodyHeader(languageType)

    div(classes = Constants.tabs) {
      h2 {
        a {
          href = "/$languageName/$groupName"; +groupName.decode()
        }; rawHtml("${Entities.nbsp.text}&rarr;${Entities.nbsp.text}"); +name
      }

      if (challenge.description.isNotEmpty())
        div(classes = "challenge-desc") { rawHtml(challenge.parsedDescription) }

      pre(classes = "line-numbers") {
        code(classes = "language-$languageName") { +challenge.funcInfo().snippet }
      }

      div(classes = Constants.userInput) {
        table {
          tr { th { +"Function Call" }; th { +"" }; th { +"Return Value" }; th { +"" } }

          funcArgs.withIndex().forEach { (i, v) ->
            tr {
              td(classes = Constants.funcCol) { +challenge.funcInfo().invokes[i] }
              td(classes = Constants.arrow) { rawHtml("&rarr;") }
              td {
                //textInput(classes = answer) { id = "$answer$i" }
                textInput(classes = Constants.answer) {
                  id = "${Constants.answer}$i"; onKeyPress = "${Constants.processAnswers}(event, ${funcArgs.size})"
                }
              }
              td(classes = Constants.feedback) { id = "${Constants.feedback}$i" }
              td { hiddenInput { id = "${Constants.solution}$i"; value = v.second } }
            }
          }
        }

        div(classes = Constants.checkBar) {
          table {
            tr {
              td {
                button(classes = Constants.checkAnswers) {
                  onClick = "${Constants.processAnswers}(null, ${funcArgs.size})"; +"Check My Answers!"
                }
              }
              td {
                span(classes = Constants.spinner) {
                  id =
                    Constants.spinner
                }
              }
              td {
                span(classes = Constants.status) {
                  id =
                    Constants.status
                }
              }
            }
          }
        }

        if (languageType.isKotlin()) {
          val kotlinChallenge = challenge as KotlinChallenge
          p(classes = Constants.refs) {
            +"Experiment with this code as a "
            a { href = "/${Constants.playground}/$groupName/$name"; target = "_blank"; +"Kotlin Playground" }
          }
        }

        p(classes = Constants.refs) {
          +"Experiment with this code on "
          a { href = "https://gitpod.io/#${challenge.gitpodUrl}"; target = "_blank"; +"Gitpod.io" }
        }

        if (challenge.codingBatEquiv.isNotEmpty() && languageType in listOf(LanguageType.Java,
                                                                            LanguageType.Python)
        ) {
          p(classes = Constants.refs) {
            +"Work on a similar problem on "
            a { href = "https://codingbat.com/prob/${challenge.codingBatEquiv}"; target = "_blank"; +"CodingBat.com" }
          }
        }

        div(classes = Constants.back) { a { href = "/$languageName/$groupName"; rawHtml("&larr; Back") } }
      }
    }

    script { src = "/${Constants.static}/$languageName-prism.js" }
  }
}