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

import com.github.readingbat.Constants.backLink
import com.github.readingbat.Constants.bodyHeader
import com.github.readingbat.Constants.cssName
import com.github.readingbat.Constants.cssType
import com.github.readingbat.Constants.production
import com.github.readingbat.Constants.selected
import com.github.readingbat.Constants.titleText
import com.github.readingbat.dsl.LanguageType
import com.github.readingbat.dsl.LanguageType.*
import kotlinx.html.*
import kotlinx.html.Entities.nbsp

internal fun HEAD.headDefault() {
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

internal fun BODY.bodyTitle() {
  div(classes = bodyHeader) {
    a { href = "/"; span { style = "font-size:200%;"; +titleText } }
    rawHtml(nbsp.text)
    span { +"code reading practice" }
  }
}

internal fun BODY.bodyHeader(languageType: LanguageType) {
  bodyTitle()

  nav {
    ul {
      li(classes = "h2") {
        if (languageType.isJava()) id = selected
        this@bodyHeader.addLink(Java.name, "/${Java.lowerName}")
      }
      li(classes = "h2") {
        if (languageType.isPython()) id = selected
        this@bodyHeader.addLink(Python.name, "/${Python.lowerName}")
      }
      li(classes = "h2") {
        if (languageType.isKotlin()) id = selected
        this@bodyHeader.addLink(Kotlin.name, "/${Kotlin.lowerName}")
      }
    }
  }
}

internal fun BODY.addLink(text: String, url: String, newWindow: Boolean = false) {
  a { href = url; if (newWindow) target = "_blank"; +text }
}

internal fun BODY.backLink(url: String) {
  div(classes = backLink) { a { href = url; rawHtml("&larr; Back") } }
}

internal fun HTMLTag.rawHtml(html: String) {
  unsafe { raw(html) }
}

internal fun Int.rows(cols: Int) = if (this % cols == 0) this / cols else (this / cols) + 1