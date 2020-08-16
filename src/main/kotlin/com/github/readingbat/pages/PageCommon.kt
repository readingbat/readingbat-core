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

import com.github.pambrose.common.util.isNull
import com.github.pambrose.common.util.toRootPath
import com.github.readingbat.dsl.InvalidConfigurationException
import com.github.readingbat.dsl.LanguageType
import com.github.readingbat.dsl.LanguageType.Companion.languageTypesInOrder
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.dsl.isProduction
import com.github.readingbat.misc.CSSNames.INDENT_1EM
import com.github.readingbat.misc.CSSNames.SELECTED_TAB
import com.github.readingbat.misc.ClassCode
import com.github.readingbat.misc.Constants.BACK_PATH
import com.github.readingbat.misc.Constants.CHALLENGE_ROOT
import com.github.readingbat.misc.Constants.ICONS
import com.github.readingbat.misc.Constants.RETURN_PATH
import com.github.readingbat.misc.Constants.STATIC_ROOT
import com.github.readingbat.misc.Endpoints.CSS_ENDPOINT
import com.github.readingbat.misc.Endpoints.PRIVACY_ENDPOINT
import com.github.readingbat.misc.Message
import com.github.readingbat.misc.Message.Companion.EMPTY_MESSAGE
import com.github.readingbat.misc.PageUtils.pathOf
import com.github.readingbat.misc.User
import com.github.readingbat.pages.HelpAndLogin.helpAndLogin
import com.github.readingbat.server.PipelineCall
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.ContentType.Text.CSS
import kotlinx.html.*
import kotlinx.html.Entities.nbsp
import redis.clients.jedis.Jedis

internal object PageCommon {
  const val READING_BAT = "ReadingBat"

  fun HEAD.headDefault(content: ReadingBatContent) {
    link { rel = "stylesheet"; href = CSS_ENDPOINT; type = CSS.toString() }

    // From: https://favicon.io/emoji-favicons/glasses/
    val root = "$STATIC_ROOT/$ICONS"
    link { rel = "apple-touch-icon"; sizes = "180x180"; href = "$root/apple-touch-icon.png" }
    link { rel = "icon"; type = "image/png"; sizes = "32x32"; href = "$root/favicon-32x32.png" }
    link { rel = "icon"; type = "image/png"; sizes = "16x16"; href = "$root/favicon-16x16.png" }
    link { rel = "manifest"; href = "$root/site.webmanifest" }

    title(READING_BAT)

    if (isProduction() && content.googleAnalyticsId.isNotBlank()) {
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

  fun HEAD.clickButtonScript(vararg buttonNames: String) {
    buttonNames.forEach { buttonName ->
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
  }

  fun BODY.bodyTitle() {
    div {
      style = "margin-bottom: 0em;"
      a { href = "/"; span { style = "font-size:200%;"; +READING_BAT } }
      span { style = "padding-left:5px;"; +"code reading practice" }
    }
  }

  fun BODY.bodyHeader(user: User?,
                      loginAttempt: Boolean,
                      content: ReadingBatContent,
                      languageType: LanguageType,
                      loginPath: String,
                      displayWelcomeMsg: Boolean,
                      activeClassCode: ClassCode,
                      redis: Jedis?,
                      msg: Message = EMPTY_MESSAGE) {

    helpAndLogin(user, loginPath, activeClassCode.isEnabled, redis)

    bodyTitle()

    p { if (displayWelcomeMsg) +"Welcome to ReadingBat." else rawHtml(nbsp.text) }

    if (loginAttempt && user.isNull())
      p { span { style = "color:red;"; +"Failed to login -- incorrect email or password" } }

    p { span { style = "color:green; max-width:800;"; if (msg.isNotBlank) +(msg.toString()) else rawHtml(nbsp.text) } }

    div {
      style = "padding-top:10px; min-width:100vw; clear:both;"
      nav {
        ul {
          languageTypesInOrder
            .filter { content[it].isNotEmpty() }
            .forEach { lang ->
              li(classes = "h2") {
                if (languageType == lang)
                  id = SELECTED_TAB
                this@bodyHeader.addLink(lang.name, pathOf(CHALLENGE_ROOT, lang.languageName))
              }
            }
        }
      }
    }

    div {
      style = "border-top: 1px solid; clear: both;"
    }
  }

  fun PipelineCall.defaultLanguageTab(content: ReadingBatContent) =
    languageTypesInOrder
      .asSequence()
      .filter { content[it].isNotEmpty() }
      .map {
        val params = call.parameters.formUrlEncode()
        "${it.contentRoot}${if (params.isNotEmpty()) "?$params" else ""}"
      }
      .firstOrNull() ?: throw InvalidConfigurationException("Missing default language")

  fun BODY.addLink(text: String, url: String, newWindow: Boolean = false) =
    a { href = url; if (newWindow) target = "_blank"; +text }

  fun BODY.privacyStatement(backPath: String, returnPath: String) =
    p(classes = INDENT_1EM) {
      a { href = "$PRIVACY_ENDPOINT?$BACK_PATH=$backPath&$RETURN_PATH=$returnPath"; +"Privacy Statement" }
    }

  fun BODY.backLinkWithIndent(url: String, marginLeft: String = "1em") {
    if (url.isNotEmpty()) {
      div {
        style = "font-size: 120%; margin-left: $marginLeft;"
        p { a { href = url; rawHtml("&larr; Back") } }
      }
    }
  }

  fun BODY.displayMessage(msg: Message) = if (msg.isNotBlank) +(msg.toString()) else rawHtml(nbsp.text)

  fun BODY.backLink(vararg pathElems: String) = backLinkWithIndent(pathElems.toList().toRootPath())

  fun HTMLTag.rawHtml(html: String) = unsafe { raw(html) }

  fun Int.rows(cols: Int) = if (this % cols == 0) this / cols else (this / cols) + 1
}