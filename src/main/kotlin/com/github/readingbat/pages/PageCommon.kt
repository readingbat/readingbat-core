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

import com.github.pambrose.common.util.toRootPath
import com.github.readingbat.dsl.InvalidConfigurationException
import com.github.readingbat.dsl.LanguageType
import com.github.readingbat.dsl.LanguageType.Companion.languageTypesInOrder
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.CSSNames.selected
import com.github.readingbat.misc.Constants.BACK_PATH
import com.github.readingbat.misc.Constants.CHALLENGE_ROOT
import com.github.readingbat.misc.Constants.ICONS
import com.github.readingbat.misc.Constants.READING_BAT
import com.github.readingbat.misc.Constants.RETURN_PATH
import com.github.readingbat.misc.Constants.STATIC_ROOT
import com.github.readingbat.misc.Endpoints.CSS_NAME
import com.github.readingbat.misc.Endpoints.PRIVACY
import com.github.readingbat.misc.PageUtils.pathOf
import com.github.readingbat.misc.UserPrincipal
import com.github.readingbat.pages.HelpAndLogin.helpAndLogin
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.production
import io.ktor.application.call
import io.ktor.http.ContentType.Text.CSS
import io.ktor.http.formUrlEncode
import kotlinx.html.*

internal object PageCommon {

  fun HEAD.headDefault(content: ReadingBatContent) {
    link { rel = "stylesheet"; href = CSS_NAME; type = CSS.toString() }

    // From: https://favicon.io/emoji-favicons/glasses/
    val root = "$STATIC_ROOT/$ICONS"
    link { rel = "apple-touch-icon"; sizes = "180x180"; href = "$root/apple-touch-icon.png" }
    link { rel = "icon"; type = "image/png"; sizes = "32x32"; href = "$root/favicon-32x32.png" }
    link { rel = "icon"; type = "image/png"; sizes = "16x16"; href = "$root/favicon-16x16.png" }
    link { rel = "manifest"; href = "$root/site.webmanifest" }

    title(READING_BAT)

    if (production && content.googleAnalyticsId.isNotBlank()) {
      script { async = true; src = "https://www.googletagmanager.com/gtag/js?id=${content.googleAnalyticsId}" }
      script {
        rawHtml("""
          window.dataLayer = window.dataLayer || [];
          function gtag(){dataLayer.push(arguments);}
          gtag('js', new Date());
          gtag('config', '${content.googleAnalyticsId}');
        """)
      }
    }
  }

  fun HEAD.clickButtonScript(buttonName: String) {
    script {
      rawHtml(
        """
          function click$buttonName(event) {
            if (event != null && event.keyCode == 13) {
              event.preventDefault();
              document.getElementById('$buttonName').click();
            }
          }
        """.trimIndent())
    }
  }

  fun BODY.bodyTitle() {
    div {
      style = "margin-bottom: 0em;"
      a { href = "/"; span { style = "font-size:200%;"; +READING_BAT } }
      span { style = "padding-left:5px;"; +"code reading practice" }
    }
  }

  fun BODY.bodyHeader(principal: UserPrincipal?,
                      loginAttempt: Boolean,
                      content: ReadingBatContent,
                      languageType: LanguageType,
                      loginPath: String,
                      msg: String = "",
                      subMsg: String = "") {

    helpAndLogin(principal, loginPath)

    bodyTitle()

    if (loginAttempt && principal == null)
      p { span { style = "color:red;"; +"Failed to login -- bad username or password" } }


    if (msg.isNotEmpty())
      div {
        style = "min-height:9; color:green;"
        p { style = "max-width:800;"; +msg }
      }

    div {
      style = "min-height:9;"
      p { style = "max-width:800;"; +subMsg }
    }

    div {
      style = "padding-top:10px; min-width:100vw; clear:both;"
      nav {
        ul {
          for (lang in languageTypesInOrder) {
            if (content.hasGroups(lang))
              li(classes = "h2") {
                if (languageType == lang) id = selected
                this@bodyHeader.addLink(lang.name, pathOf(CHALLENGE_ROOT, lang.lowerName))
              }
          }
        }
      }
    }
  }

  fun PipelineCall.defaultLanguageTab(content: ReadingBatContent) =
    languageTypesInOrder
      .asSequence()
      .filter { content.hasGroups(it) }
      .map {
        val params = call.parameters.formUrlEncode()
        "${it.contentRoot}${if (params.isNotEmpty()) "?$params" else ""}"
      }
      .firstOrNull() ?: throw InvalidConfigurationException("Missing default language")

  fun BODY.addLink(text: String, url: String, newWindow: Boolean = false) =
    a { href = url; if (newWindow) target = "_blank"; +text }

  fun BODY.backLink(url: String, marginLeft: String = "1em") {
    if (url.isNotEmpty()) {
      div {
        style = "font-size: 120%; margin-left: $marginLeft;"
        br
        a { href = url; rawHtml("&larr; Back") }
      }
    }
  }

  fun BODY.privacyStatement(backPath: String, returnPath: String) {
    p { a { href = "$PRIVACY?$BACK_PATH=$backPath&$RETURN_PATH=$returnPath"; +"privacy statement" } }
  }

  fun BODY.backLink(vararg pathElems: String) = backLink(pathElems.toList().toRootPath())

  fun HTMLTag.rawHtml(html: String) = unsafe { raw(html) }

  fun Int.rows(cols: Int) = if (this % cols == 0) this / cols else (this / cols) + 1
}
