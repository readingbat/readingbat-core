/*
 * Copyright © 2021 Paul Ambrose (pambrose@mac.com)
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
import com.github.pambrose.common.util.pathOf
import com.github.pambrose.common.util.pluralize
import com.github.pambrose.common.util.toDoubleQuoted
import com.github.pambrose.common.util.toRootPath
import com.github.readingbat.common.CSSNames.ADMIN_BUTTON
import com.github.readingbat.common.CSSNames.INDENT_1EM
import com.github.readingbat.common.CSSNames.SELECTED_TAB
import com.github.readingbat.common.ClassCode
import com.github.readingbat.common.Constants.ADMIN_FUNC
import com.github.readingbat.common.Constants.ICONS
import com.github.readingbat.common.Endpoints.CHALLENGE_ROOT
import com.github.readingbat.common.Endpoints.CSS_ENDPOINT
import com.github.readingbat.common.Endpoints.PRIVACY_ENDPOINT
import com.github.readingbat.common.Endpoints.STATIC_ROOT
import com.github.readingbat.common.FormFields.RETURN_PARAM
import com.github.readingbat.common.Message
import com.github.readingbat.common.Message.Companion.EMPTY_MESSAGE
import com.github.readingbat.common.Property
import com.github.readingbat.common.User
import com.github.readingbat.dsl.LanguageType
import com.github.readingbat.dsl.LanguageType.Companion.languageTypeList
import com.github.readingbat.dsl.LanguageType.Java
import com.github.readingbat.dsl.LanguageType.Kotlin
import com.github.readingbat.dsl.LanguageType.Python
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.dsl.isProduction
import com.github.readingbat.pages.HelpAndLogin.helpAndLogin
import io.ktor.application.*
import io.ktor.http.ContentType.Text.CSS
import io.ktor.routing.*
import io.ktor.util.pipeline.*
import kotlinx.html.*
import kotlinx.html.Entities.nbsp

internal object PageUtils {
  private const val READING_BAT = "ReadingBat"

  fun HEAD.headDefault() {
    link { rel = "stylesheet"; href = CSS_ENDPOINT; type = CSS.toString() }

    // From: https://favicon.io/emoji-favicons/glasses/
    val prefix = pathOf(STATIC_ROOT, ICONS)
    link { rel = "apple-touch-icon"; sizes = "180x180"; href = "$prefix/apple-touch-icon.png" }
    link { rel = "icon"; type = "image/png"; sizes = "32x32"; href = "$prefix/favicon-32x32.png" }
    link { rel = "icon"; type = "image/png"; sizes = "16x16"; href = "$prefix/favicon-16x16.png" }
    link { rel = "manifest"; href = "$prefix/site.webmanifest" }

    title(READING_BAT)

    val analyticsId = Property.ANALYTICS_ID.getPropertyOrNull() ?: ""
    if (isProduction() && analyticsId.isNotBlank()) {
      script { async = true; src = "https://www.googletagmanager.com/gtag/js?id=$analyticsId" }
      script {
        rawHtml(
          """
          window.dataLayer = window.dataLayer || [];
          function gtag(){dataLayer.push(arguments);}
          gtag('js', new Date());
          gtag('config', '$analyticsId');
        """
        )
      }
    }
  }

  fun HEAD.loadBootstrap() {
    link { rel = "stylesheet"; href = "https://maxcdn.bootstrapcdn.com/bootstrap/3.4.1/css/bootstrap.min.css" }
    script { src = "https://ajax.googleapis.com/ajax/libs/jquery/3.5.1/jquery.min.js" }
    script { src = "https://maxcdn.bootstrapcdn.com/bootstrap/3.4.1/js/bootstrap.min.js" }
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
        """.trimIndent()
        )
      }
    }
  }

  fun BODY.bodyTitle() {
    div {
      style = "margin-bottom:0em"
      a { href = "/"; span { style = "font-size:200%"; +READING_BAT } }
      span { style = "padding-left:5px"; +"code reading practice" }
    }
  }

  fun BODY.bodyHeader(
    content: ReadingBatContent,
    user: User?,
    languageType: LanguageType,
    loginAttempt: Boolean,
    loginPath: String,
    displayWelcomeMsg: Boolean,
    activeClassCode: ClassCode,
    msg: Message = EMPTY_MESSAGE
  ) {

    helpAndLogin(content, user, loginPath, activeClassCode.isEnabled)
    bodyTitle()

    p { if (displayWelcomeMsg) +"Welcome to ReadingBat." else rawHtml(nbsp.text) }

    if (loginAttempt && user.isNull())
      p {
        span {
          style =
            "color:red"; +"Failed to login -- incorrect email or password"
        }
      }

    p { span { style = "color:green; max-width:800"; if (msg.isNotBlank) +(msg.toString()) else rawHtml(nbsp.text) } }

    div {
      style = "padding-top:10px; min-width:100vw; clear:both"
      nav {
        ul {
          languageTypeList
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
      style = "border-top: 1px solid; clear: both"
    }
  }

  fun BODY.addLink(text: String, url: String, newWindow: Boolean = false) =
    a { href = url; if (newWindow) target = "_blank"; +text }

  fun BODY.privacyStatement(returnPath: String) =
    p(classes = INDENT_1EM) {
      a { href = "$PRIVACY_ENDPOINT?$RETURN_PARAM=$returnPath"; +"Privacy Statement" }
    }

  private fun BODY.linkWithIndent(url: String, text: String, marginLeft: String = "1em") {
    if (url.isNotEmpty()) {
      div {
        style = "font-size:120%; margin-left:$marginLeft"
        p { a { href = url; rawHtml("&larr; $text") } }
      }
    }
  }

  fun enrolleesDesc(enrollees: List<User>): String {
    val studentCount = if (enrollees.isEmpty()) "No" else enrollees.count().toString()
    return " - $studentCount ${"student".pluralize(enrollees.count())} enrolled"
  }

  @Suppress("unused")
  fun BODY.confirmingButton(text: String, endpoint: String, msg: String) {
    form {
      style = "margin:0"
      action = endpoint
      method = FormMethod.get
      onSubmit = "return confirm('$msg')"
      submitInput {
        style = "vertical-align:middle; margin-top:1; margin-bottom:0"
        value = text
      }
    }
  }

  fun BODY.adminButton(text: String, endpoint: String, confirm: String) {
    button(classes = ADMIN_BUTTON) {
      onClick = "$ADMIN_FUNC(${confirm.toDoubleQuoted()}, ${endpoint.toDoubleQuoted()})"
      +text
    }
  }

  fun BODY.displayMessage(msg: Message) = if (msg.isNotBlank) +(msg.toString()) else rawHtml(nbsp.text)

  private val rootVals = listOf("", "/", Java.contentRoot, Python.contentRoot, Kotlin.contentRoot)

  fun BODY.backLink(vararg pathElems: String = arrayOf("/")) {
    if (pathElems.size == 1 && pathElems[0] in rootVals)
      linkWithIndent(pathElems.toList().toRootPath(), "Home")
    else
      linkWithIndent(pathElems.toList().toRootPath(), "Back")
  }

  fun BODY.loadPingdomScript() {
    Property.PINGDOM_URL.getPropertyOrNull()?.also { if (it.isNotBlank()) script { src = it; async = true } }
  }

  fun BODY.loadStatusPageDisplay() {
    Property.STATUS_PAGE_URL.getPropertyOrNull()?.also { if (it.isNotBlank()) script { src = it } }
  }

  fun HTMLTag.rawHtml(html: String) = unsafe { raw(html) }

  @Suppress("unused")
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

  fun FlowOrInteractiveOrPhrasingContent.hideShowButton(formName: String, fieldName: String, sizePct: Int = 85) {
    button { style = "font-size:$sizePct%"; onClick = hideShowJs(formName, fieldName); +"show/hide" }
  }

  fun encodeUriElems(vararg elems: Any) = elems.joinToString("+'/'+") { "encodeURIComponent('$it')" }
}