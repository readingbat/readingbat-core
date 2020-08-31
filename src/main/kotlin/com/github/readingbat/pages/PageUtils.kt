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
import com.github.readingbat.common.CSSNames.INDENT_1EM
import com.github.readingbat.common.CSSNames.SELECTED_TAB
import com.github.readingbat.common.ClassCode
import com.github.readingbat.common.CommonUtils.pathOf
import com.github.readingbat.common.Constants.BACK_PATH
import com.github.readingbat.common.Constants.ICONS
import com.github.readingbat.common.Constants.RETURN_PATH
import com.github.readingbat.common.Endpoints.CHALLENGE_ROOT
import com.github.readingbat.common.Endpoints.CSS_ENDPOINT
import com.github.readingbat.common.Endpoints.PRIVACY_ENDPOINT
import com.github.readingbat.common.Endpoints.STATIC_ROOT
import com.github.readingbat.common.Message
import com.github.readingbat.common.Message.Companion.EMPTY_MESSAGE
import com.github.readingbat.common.User
import com.github.readingbat.dsl.LanguageType
import com.github.readingbat.dsl.LanguageType.Companion.languageTypesInOrder
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.dsl.isProduction
import com.github.readingbat.pages.HelpAndLogin.helpAndLogin
import io.ktor.application.*
import io.ktor.http.ContentType.Text.CSS
import io.ktor.routing.*
import io.ktor.util.pipeline.*
import kotlinx.html.*
import kotlinx.html.Entities.nbsp
import redis.clients.jedis.Jedis

internal object PageUtils {
  private const val READING_BAT = "ReadingBat"

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

  fun BODY.bodyHeader(content: ReadingBatContent,
                      user: User?,
                      languageType: LanguageType,
                      loginAttempt: Boolean,
                      loginPath: String,
                      displayWelcomeMsg: Boolean,
                      activeClassCode: ClassCode,
                      redis: Jedis?,
                      msg: Message = EMPTY_MESSAGE) {

    helpAndLogin(user, loginPath, activeClassCode.isEnabled, redis)

    bodyTitle()

    p { if (displayWelcomeMsg) +"Welcome to ReadingBat." else rawHtml(nbsp.text) }

    if (loginAttempt && user.isNull())
      p {
        span {
          style =
            "color:red;"; +"Failed to login -- ${if (redis.isNull()) "database is down" else "incorrect email or password"}"
        }
      }

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

  fun BODY.addLink(text: String, url: String, newWindow: Boolean = false) =
    a { href = url; if (newWindow) target = "_blank"; +text }

  fun BODY.privacyStatement(backPath: String, returnPath: String) =
    p(classes = INDENT_1EM) {
      a { href = "$PRIVACY_ENDPOINT?$BACK_PATH=$backPath&$RETURN_PATH=$returnPath"; +"Privacy Statement" }
    }

  private fun BODY.backLinkWithIndent(url: String, marginLeft: String = "1em") {
    if (url.isNotEmpty()) {
      div {
        style = "font-size: 120%; margin-left: $marginLeft;"
        p { a { href = url; rawHtml("&larr; Back") } }
      }
    }
  }

  internal fun BODY.confirmingButton(text: String, endpoint: String, msg: String) {
    form {
      style = "margin:0;"
      action = endpoint
      method = FormMethod.get
      onSubmit = "return confirm('$msg');"
      input {
        style = "vertical-align:middle; margin-top:1; margin-bottom:0;"
        type = InputType.submit; value = text
      }
    }
  }

  fun BODY.displayMessage(msg: Message) = if (msg.isNotBlank) +(msg.toString()) else rawHtml(nbsp.text)

  fun BODY.backLink(vararg pathElems: String) = backLinkWithIndent(pathElems.toList().toRootPath())

  fun HTMLTag.rawHtml(html: String) = unsafe { raw(html) }

  fun Route.getAndPost(path: String, body: PipelineInterceptor<Unit, ApplicationCall>) {
    get(path, body)
    post(path, body)
  }

  private fun hideShowJs(formName: String, fieldName: String) =
    """
      var pw=document.$formName.$fieldName.type=="password"; 
      document.$formName.$fieldName.type=pw?"text":"password"; 
      return false;
    """.trimIndent()

  internal fun FlowOrInteractiveOrPhrasingContent.hideShowButton(formName: String,
                                                                 fieldName: String,
                                                                 sizePct: Int = 85) {
    button { style = "font-size:$sizePct%;"; onClick = hideShowJs(formName, fieldName); +"show/hide" }
  }

  internal fun encodeUriElems(vararg elems: Any) = elems.joinToString("+'/'+") { "encodeURIComponent('$it')" }
}