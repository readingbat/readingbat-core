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
import com.github.readingbat.Constants.answer
import com.github.readingbat.Constants.arrow
import com.github.readingbat.Constants.back
import com.github.readingbat.Constants.challengeDesc
import com.github.readingbat.Constants.checkAnswers
import com.github.readingbat.Constants.checkBar
import com.github.readingbat.Constants.cssType
import com.github.readingbat.Constants.feedback
import com.github.readingbat.Constants.funcCol
import com.github.readingbat.Constants.playground
import com.github.readingbat.Constants.processAnswers
import com.github.readingbat.Constants.refs
import com.github.readingbat.Constants.solution
import com.github.readingbat.Constants.spinner
import com.github.readingbat.Constants.static
import com.github.readingbat.Constants.status
import com.github.readingbat.Constants.tabs
import com.github.readingbat.Constants.userInput
import com.github.readingbat.dsl.Challenge
import com.github.readingbat.dsl.KotlinChallenge
import com.github.readingbat.dsl.LanguageType
import com.github.readingbat.misc.addScript
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
      rel = "stylesheet"; href = "/$static/$languageName-prism.css"; type = cssType
    }

    // Remove the prism shadow
    style {
      rawHtml(
        """
          pre[class*="language-"]:before,
          pre[class*="language-"]:after { display: none; }
        """)
    }

    script(type = ScriptType.textJavaScript) { addScript(languageName) }

    headDefault()
  }

  body {
    bodyHeader(languageType)

    div(classes = tabs) {
      h2 {
        a {
          href = "/$languageName/$groupName"; +groupName.decode()
        }; rawHtml("${Entities.nbsp.text}&rarr;${Entities.nbsp.text}"); +name
      }

      if (challenge.description.isNotEmpty())
        div(classes = challengeDesc) { rawHtml(challenge.parsedDescription) }

      div(classes = userInput) {

        pre(classes = "line-numbers") {
          code(classes = "language-$languageName") { +challenge.funcInfo().snippet }
        }

        table {
          tr { th { +"Function Call" }; th { +"" }; th { +"Return Value" }; th { +"" } }

          funcArgs.withIndex().forEach { (i, v) ->
            tr {
              td(classes = funcCol) { +challenge.funcInfo().invokes[i] }
              td(classes = arrow) { rawHtml("&rarr;") }
              td {
                //textInput(classes = answer) { id = "$answer$i" }
                textInput(classes = answer) {
                  id = "$answer$i"; onKeyPress = "$processAnswers(event, ${funcArgs.size})"
                }
              }
              td(classes = feedback) { id = "$feedback$i" }
              td { hiddenInput { id = "$solution$i"; value = v.second } }
            }
          }
        }

        div(classes = checkBar) {
          table {
            tr {
              td {
                button(classes = checkAnswers) {
                  onClick = "$processAnswers(null, ${funcArgs.size})"; +"Check My Answers!"
                }
              }
              td {
                span(classes = spinner) {
                  id = spinner
                }
              }
              td {
                span(classes = status) {
                  id = status
                }
              }
            }
          }
        }

        if (languageType.isKotlin()) {
          val kotlinChallenge = challenge as KotlinChallenge
          p(classes = refs) {
            +"Experiment with this code as a "
            a { href = "/$playground/$groupName/$name"; target = "_blank"; +"Kotlin Playground" }
          }
        }

        p(classes = refs) {
          +"Experiment with this code on "
          a { href = "https://gitpod.io/#${challenge.gitpodUrl}"; target = "_blank"; +"Gitpod.io" }
        }

        if (challenge.codingBatEquiv.isNotEmpty() && languageType in listOf(LanguageType.Java,
                                                                            LanguageType.Python)
        ) {
          p(classes = refs) {
            +"Work on a similar problem on "
            a { href = "https://codingbat.com/prob/${challenge.codingBatEquiv}"; target = "_blank"; +"CodingBat.com" }
          }
        }

        div(classes = back) { a { href = "/$languageName/$groupName"; rawHtml("&larr; Back") } }
      }
    }

    script { src = "/$static/$languageName-prism.js" }
  }
}