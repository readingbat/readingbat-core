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

import com.github.readingbat.Module.readingBatContent
import com.github.readingbat.dsl.LanguageType
import com.github.readingbat.dsl.LanguageType.*
import com.github.readingbat.misc.Constants.backLink
import com.github.readingbat.misc.Constants.bodyHeader
import com.github.readingbat.misc.Constants.cssName
import com.github.readingbat.misc.Constants.cssType
import com.github.readingbat.misc.Constants.icons
import com.github.readingbat.misc.Constants.production
import com.github.readingbat.misc.Constants.root
import com.github.readingbat.misc.Constants.selected
import com.github.readingbat.misc.Constants.staticRoot
import com.github.readingbat.misc.Constants.titleText
import kotlinx.html.*
import kotlinx.html.Entities.nbsp

internal fun HEAD.headDefault() {
  link { rel = "stylesheet"; href = "/$cssName"; type = cssType }

  // From: https://favicon.io/emoji-favicons/glasses/
  link { rel = "apple-touch-icon"; sizes = "180x180"; href = "/$staticRoot/$icons/apple-touch-icon.png" }
  link { rel = "icon"; type = "image/png"; sizes = "32x32"; href = "/$staticRoot/$icons/favicon-32x32.png" }
  link { rel = "icon"; type = "image/png"; sizes = "16x16"; href = "/$staticRoot/$icons/favicon-16x16.png" }
  link { rel = "manifest"; href = "/$staticRoot/$icons/site.webmanifest" }

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
      for (lang in listOf(Java, Python, Kotlin)) {
        if (readingBatContent.hasGroups(lang))
          li(classes = "h2") {
            if (languageType == lang) id = selected
            this@bodyHeader.addLink(lang.name, "/$root/${lang.lowerName}")
          }
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

internal fun HTMLTag.rawHtml(html: String) = unsafe { raw(html) }

internal fun Int.rows(cols: Int) = if (this % cols == 0) this / cols else (this / cols) + 1