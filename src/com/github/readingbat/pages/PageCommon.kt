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

import com.github.readingbat.InvalidConfigurationException
import com.github.readingbat.dsl.LanguageType
import com.github.readingbat.dsl.LanguageType.*
import com.github.readingbat.dsl.ReadingBatContent
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

internal fun HEAD.headDefault(readingBatContent: ReadingBatContent) {
  link { rel = "stylesheet"; href = "/$cssName"; type = cssType }

  // From: https://favicon.io/emoji-favicons/glasses/
  val root = "$staticRoot/$icons"
  link { rel = "apple-touch-icon"; sizes = "180x180"; href = "/$root/apple-touch-icon.png" }
  link { rel = "icon"; type = "image/png"; sizes = "32x32"; href = "/$root/favicon-32x32.png" }
  link { rel = "icon"; type = "image/png"; sizes = "16x16"; href = "/$root/favicon-16x16.png" }
  link { rel = "manifest"; href = "/$root/site.webmanifest" }

  title(titleText)

  if (production && readingBatContent.googleAnalyticsId.isNotBlank()) {
    script { async = true; src = "https://www.googletagmanager.com/gtag/js?id=${readingBatContent.googleAnalyticsId}" }
    script {
      rawHtml("""
          window.dataLayer = window.dataLayer || [];
          function gtag(){dataLayer.push(arguments);}
          gtag('js', new Date());
          gtag('config', '${readingBatContent.googleAnalyticsId}');
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

internal fun BODY.bodyHeader(readingBatContent: ReadingBatContent, languageType: LanguageType) {
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

internal fun defaultTab(readingBatContent: ReadingBatContent) =
  listOf(Java, Python, Kotlin)
    .asSequence()
    .filter { readingBatContent.hasGroups(it) }
    .map { "/$root/${it.lowerName}" }
    .firstOrNull()
    ?: throw InvalidConfigurationException("Missing default language")

internal fun BODY.addLink(text: String, url: String, newWindow: Boolean = false) {
  a { href = url; if (newWindow) target = "_blank"; +text }
}

internal fun BODY.backLink(url: String) {
  div(classes = backLink) { a { href = url; rawHtml("&larr; Back") } }
}

internal fun HTMLTag.rawHtml(html: String) = unsafe { raw(html) }

internal fun Int.rows(cols: Int) = if (this % cols == 0) this / cols else (this / cols) + 1