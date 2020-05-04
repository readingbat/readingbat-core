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
import com.github.readingbat.misc.Constants.arrow
import com.github.readingbat.misc.Constants.challengeDesc
import com.github.readingbat.misc.Constants.checkAnswers
import com.github.readingbat.misc.Constants.checkBar
import com.github.readingbat.misc.Constants.codeBlock
import com.github.readingbat.misc.Constants.cssType
import com.github.readingbat.misc.Constants.feedback
import com.github.readingbat.misc.Constants.funcCol
import com.github.readingbat.misc.Constants.playground
import com.github.readingbat.misc.Constants.processAnswers
import com.github.readingbat.misc.Constants.refs
import com.github.readingbat.misc.Constants.root
import com.github.readingbat.misc.Constants.spinner
import com.github.readingbat.misc.Constants.static
import com.github.readingbat.misc.Constants.status
import com.github.readingbat.misc.Constants.tabs
import com.github.readingbat.misc.Constants.userAnswers
import com.github.readingbat.misc.Constants.userResp
import com.github.readingbat.misc.addScript
import kotlinx.html.*
import kotlinx.html.Entities.nbsp

internal fun HTML.challengePage(challenge: Challenge) {
  val languageType = challenge.languageType
  val languageName = languageType.lowerName
  val groupName = challenge.groupName
  val name = challenge.name

  head {
    link { rel = "stylesheet"; href = spinnerCss }
    link { rel = "stylesheet"; href = "/$static/$languageName-prism.css"; type = cssType }

    script(type = ScriptType.textJavaScript) { addScript(languageName, groupName, name) }

    removePrismShadow()
    headDefault()
  }

  body {
    bodyHeader(languageType)

    div(classes = tabs) {
      h2 {
        this@body.addLink(groupName.decode(), "/$root/$languageName/$groupName")
        rawHtml("${nbsp.text}&rarr;${nbsp.text}"); +name
      }

      if (challenge.description.isNotEmpty())
        div(classes = challengeDesc) { rawHtml(challenge.parsedDescription) }

      div(classes = codeBlock) {
        pre(classes = "line-numbers") {
          code(classes = "language-$languageName") { +challenge.funcInfo().codeSnippet }
        }
      }

      val funcInfo = challenge.funcInfo()

      div(classes = userAnswers) {
        table {
          tr { th { +"Function Call" }; th { +"" }; th { +"Return Value" }; th { +"" } }

          //funcArgs.withIndex().forEach { (i, v) ->
          funcInfo.arguments.indices.forEach { i ->
            tr {
              td(classes = funcCol) { +funcInfo.arguments[i] }
              td(classes = arrow) { rawHtml("&rarr;") }
              td {
                textInput(classes = userResp) {
                  id = "$userResp$i"; onKeyPress = "$processAnswers(event, ${funcInfo.answers.size})"
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
            this@body.addLink("Kotlin Playground", "/$playground/$groupName/$name", true)
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

    backLink("/$root/$languageName/$groupName")

    script { src = "/$static/$languageName-prism.js" }
  }
}

private const val spinnerCss = "https://cdnjs.cloudflare.com/ajax/libs/font-awesome/4.7.0/css/font-awesome.min.css"

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