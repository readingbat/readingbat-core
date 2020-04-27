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

package com.github.readingbat

import com.github.pambrose.common.util.decode
import com.github.readingbat.Constants.answer
import com.github.readingbat.Constants.arrow
import com.github.readingbat.Constants.back
import com.github.readingbat.Constants.checkAnswers
import com.github.readingbat.Constants.checkBar
import com.github.readingbat.Constants.checkJpg
import com.github.readingbat.Constants.cssName
import com.github.readingbat.Constants.cssType
import com.github.readingbat.Constants.feedback
import com.github.readingbat.Constants.funcChoice
import com.github.readingbat.Constants.funcCol
import com.github.readingbat.Constants.funcItem
import com.github.readingbat.Constants.groupItemSrc
import com.github.readingbat.Constants.playground
import com.github.readingbat.Constants.processAnswers
import com.github.readingbat.Constants.production
import com.github.readingbat.Constants.refs
import com.github.readingbat.Constants.selected
import com.github.readingbat.Constants.solution
import com.github.readingbat.Constants.spinner
import com.github.readingbat.Constants.static
import com.github.readingbat.Constants.status
import com.github.readingbat.Constants.tabs
import com.github.readingbat.Constants.titleText
import com.github.readingbat.Constants.userInput
import com.github.readingbat.dsl.*
import com.github.readingbat.dsl.LanguageType.*
import io.ktor.application.ApplicationCall
import io.ktor.http.ContentType
import io.ktor.response.respondText
import kotlinx.css.CSSBuilder
import kotlinx.html.*
import kotlinx.html.Entities.nbsp

fun HEAD.headDefault() {
  link { rel = "stylesheet"; href = "/$cssName"; type = cssType }

  title(titleText)

  if (production) {
    script { async = true; src = "https://www.googletagmanager.com/gtag/js?id=UA-164310007-1" }
    script {
      rawHtml("""
          window.dataLayer = window.dataLayer || [];
          function gtag(){dataLayer.push(arguments);}
          gtag('js', new Date());
          gtag('config', 'UA-164310007-1');
        """)
    }
  }
}

fun BODY.bodyHeader(languageType: LanguageType) {
  div(classes = "header") {
    a { href = "/"; span { style = "font-size:200%;"; +titleText } }
    rawHtml(nbsp.text)
    span { +"code reading practice" }
  }
  nav {
    ul {
      li(classes = "h2") {
        if (languageType.isJava()) id = selected
        a { href = "/${Java.lowerName}"; +Java.name }
      }
      li(classes = "h2") {
        if (languageType.isPython()) id = selected
        a { href = "/${Python.lowerName}"; +Python.name }
      }
      li(classes = "h2") {
        if (languageType.isKotlin()) id = selected
        a { href = "/${Kotlin.lowerName}"; +Kotlin.name }
      }
    }
  }
}

fun TR.groupItem(prefix: String, group: ChallengeGroup) {
  val name = group.name
  val parsedDescription = group.parsedDescription

  td(classes = funcItem) {
    div(classes = groupItemSrc) {
      a(classes = funcChoice) { href = "/$prefix/$name"; +name }
      br { rawHtml(if (parsedDescription.isNotBlank()) parsedDescription else nbsp.text) }
    }
  }
}

fun TR.funcCall(prefix: String, groupName: String, challenge: Challenge) {
  td(classes = funcItem) {
    img { src = checkJpg }
    rawHtml(nbsp.text)
    a(classes = funcChoice) { href = "/$prefix/$groupName/${challenge.name}"; +challenge.name }
  }
}

fun HTML.languageGroupPage(languageType: LanguageType, groups: List<ChallengeGroup>) {
  head {
    headDefault()
  }

  body {
    bodyHeader(languageType)
    div(classes = tabs) {
      table {
        val cols = 3
        val size = groups.size
        val rows = size.rows(cols)
        val languageName = languageType.lowerName

        (0 until rows).forEach { i ->
          tr {
            groups[i].also { group -> groupItem(languageName, group) }
            groups.elementAtOrNull(i + rows)?.also { groupItem(languageName, it) } ?: td {}
            groups.elementAtOrNull(i + (2 * rows))?.also { groupItem(languageName, it) } ?: td {}
          }
        }
      }
    }
  }
}

fun HTML.challengeGroupPage(challengeGroup: ChallengeGroup) {

  val languageType = challengeGroup.languageType
  val groupName = challengeGroup.name
  val challenges = challengeGroup.challenges
  val prefix = languageType.lowerName

  head {
    headDefault()
  }

  body {
    bodyHeader(languageType)

    div(classes = tabs) {
      h2 { +groupName.decode() }

      table {
        val cols = 3
        val size = challenges.size
        val rows = size.rows(cols)

        (0 until rows).forEach { i ->
          tr {
            challenges.apply {
              elementAt(i).also { funcCall(prefix, groupName, it) }
              elementAtOrNull(i + rows)?.also { funcCall(prefix, groupName, it) } ?: td {}
              elementAtOrNull(i + (2 * rows))?.also { funcCall(prefix, groupName, it) } ?: td {}
            }
          }
        }
      }

      div(classes = back) { a { href = "/$prefix"; rawHtml("&larr; Back") } }
    }
  }
}

fun HTML.challengePage(challenge: Challenge) {
  val languageType = challenge.languageType
  val groupName = challenge.groupName
  val name = challenge.name
  val funcArgs = challenge.inputOutput
  val languageName = languageType.lowerName

  head {
    link {
      rel = "stylesheet"; href = "https://cdnjs.cloudflare.com/ajax/libs/font-awesome/4.7.0/css/font-awesome.min.css"
    }
    link { rel = "stylesheet"; href = "/$static/$languageName-prism.css"; type = cssType }

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

    div(classes = tabs) {
      h2 {
        a { href = "/$languageName/$groupName"; +groupName.decode() }; rawHtml("${nbsp.text}&rarr;${nbsp.text}"); +name
      }

      if (challenge.description.isNotEmpty())
        div(classes = "challenge-desc") { rawHtml(challenge.parsedDescription) }

      pre(classes = "line-numbers") {
        code(classes = "language-$languageName") { +challenge.funcInfo().snippet }
      }

      div(classes = userInput) {
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
              td { span(classes = spinner) { id = spinner } }
              td { span(classes = status) { id = status } }
            }
          }
        }
        p(classes = refs) {
          +"Experiment with this code on "
          a { href = "https://gitpod.io/#${challenge.gitpodUrl}"; target = "_blank"; +"Gitpod.io" }
        }

        if (challenge.codingBatEquiv.isNotEmpty() && languageType in listOf(Java, Python)) {
          p(classes = refs) {
            +"Work on a similar problem on "
            a { href = "https://codingbat.com/prob/${challenge.codingBatEquiv}"; target = "_blank"; +"CodingBat.com" }
          }
        }

        if (languageType.isKotlin()) {
          val kotlinChallenge = challenge as KotlinChallenge
          p(classes = refs) {
            +"Experiment with the code as a "
            a { href = "/$playground/${kotlinChallenge.id}"; target = "_blank"; +"Kotlin Playground" }
          }

        }

        div(classes = back) { a { href = "/$languageName/$groupName"; rawHtml("&larr; Back") } }
      }
    }

    script { src = "/$static/$languageName-prism.js" }
  }
}

fun HTML.kotlinPlayground(funcInfo: FuncInfo) {
  val challenge = funcInfo.challenge
  val languageType = challenge.languageType
  val groupName = challenge.groupName
  val name = challenge.name
  val funcArgs = challenge.inputOutput
  val languageName = languageType.lowerName

  head {
    script { src = "https://unpkg.com/kotlin-playground@1"; attributes["data-selector"] = "code" }

    headDefault()
  }

  body {
    div(classes = "kotlin-code") {
      code(classes = "kotlin-code") {
        //theme = "idea"
        //indent = "4"
        //style = "visibility: hidden; padding: 36px 0;"
        style = "padding: 36px 0;"

        rawHtml(funcInfo.code)
      }
    }
  }
}

fun HTMLTag.rawHtml(html: String) {
  unsafe { raw(html) }
}

suspend inline fun ApplicationCall.respondCss(builder: CSSBuilder.() -> Unit) {
  this.respondText(CSSBuilder().apply(builder).toString(), ContentType.Text.CSS)
}

fun Int.rows(cols: Int) = if (this % cols == 0) this / cols else (this / cols) + 1