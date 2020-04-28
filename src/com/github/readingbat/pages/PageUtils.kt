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

import com.github.readingbat.Constants.checkJpg
import com.github.readingbat.Constants.cssName
import com.github.readingbat.Constants.cssType
import com.github.readingbat.Constants.funcChoice
import com.github.readingbat.Constants.funcItem
import com.github.readingbat.Constants.groupItemSrc
import com.github.readingbat.Constants.production
import com.github.readingbat.Constants.selected
import com.github.readingbat.Constants.titleText
import com.github.readingbat.dsl.Challenge
import com.github.readingbat.dsl.ChallengeGroup
import com.github.readingbat.dsl.LanguageType
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

fun BODY.bodyTitle() {
  div(classes = "header") {
    a { href = "/"; span { style = "font-size:200%;"; +titleText } }
    rawHtml(nbsp.text)
    span { +"code reading practice" }
  }
}

fun BODY.bodyHeader(languageType: LanguageType) {

  bodyTitle()

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

fun HTMLTag.rawHtml(html: String) {
  unsafe { raw(html) }
}

suspend inline fun ApplicationCall.respondCss(builder: CSSBuilder.() -> Unit) {
  this.respondText(CSSBuilder().apply(builder).toString(), ContentType.Text.CSS)
}

fun Int.rows(cols: Int) = if (this % cols == 0) this / cols else (this / cols) + 1