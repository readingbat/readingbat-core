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

import com.github.pambrose.common.util.toPath
import com.github.readingbat.InvalidConfigurationException
import com.github.readingbat.config.production
import com.github.readingbat.dsl.LanguageType
import com.github.readingbat.dsl.LanguageType.Companion.languageTypesInOrder
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.AuthRoutes.LOGOUT
import com.github.readingbat.misc.CSSNames.backLinkCls
import com.github.readingbat.misc.CSSNames.bodyHeaderCls
import com.github.readingbat.misc.CSSNames.max
import com.github.readingbat.misc.CSSNames.pretab
import com.github.readingbat.misc.CSSNames.selected
import com.github.readingbat.misc.Constants.ABOUT
import com.github.readingbat.misc.Constants.PREFS
import com.github.readingbat.misc.Constants.challengeRoot
import com.github.readingbat.misc.Constants.cssName
import com.github.readingbat.misc.Constants.icons
import com.github.readingbat.misc.Constants.staticRoot
import com.github.readingbat.misc.Constants.titleText
import com.github.readingbat.misc.FormFields.PASSWORD
import com.github.readingbat.misc.FormFields.USERNAME
import io.ktor.auth.UserIdPrincipal
import io.ktor.http.ContentType.Text.CSS
import kotlinx.html.*
import kotlinx.html.Entities.nbsp

internal fun HEAD.headDefault(readingBatContent: ReadingBatContent) {
  link { rel = "stylesheet"; href = "/$cssName"; type = CSS.toString() }

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

internal fun BODY.helpAndLogin(principal: UserIdPrincipal?, loginPath: String) {
  div {
    style = "float:right; margin:0px; border: 1px solid lightgray;"
    table {

      if (principal != null) {
        tr {
          val elems = principal.name.split("@")
          td {
            if (elems.size == 1) +elems[0]
            else {
              +elems[0]; br; +"@${elems[1]}"
            }
          }
        }
        tr {
          td {
            /*
          a {
            href = "/doc/practice/code-badges.html"; img {
            width = "30"; style = "vertical-align: middle"; src = "/$staticRoot/s5j.png"
          }
          }
           */
            +"["; a { href = LOGOUT; +"log out" }; +"]"
          }
        }
      }
      else {
        val path = "/$challengeRoot/$loginPath"
        form(method = FormMethod.post) {
          action = path
          this@table.tr {
            td { +"id/email" }
            td { textInput { name = USERNAME; size = "20"; placeholder = "username" } }
          }
          this@table.tr {
            td { +"password" }
            td { passwordInput { name = PASSWORD; size = "20"; placeholder = "password" } }
          }
          this@table.tr {
            td {}
            td { submitInput { name = "dologin"; value = "log in" } }
          }
          hiddenInput { name = "fromurl"; value = path }
        }
        tr {
          td {
            colSpan = "2"
            a { href = "/reset"; +"forgot password" }
            +" | "
            a { href = "/pref?docreate=1"; +"create account" }
          }
        }
      }
    }
  }

  div {
    style = "float:right"
    table {
      tr {
        td {
          //valign = "top"
          style = "text-align:right"
          colSpan = "1"
          a { href = ABOUT; +"about" }
          +" | "
          //a { href = "/help.html"; +"help" }
          //+" | "
          a {
            //href = "/doc/code-help-videos.html"; +"code help+videos | "
            //a { href = "/done?user=pambrose@mac.com&tag=6621428513"; +"done" }
            //+" | "
            //a { href = "/report"; +"report" }
            //+" | "
            a { href = PREFS; +"prefs " }
          }
        }
      }
    }
  }
}

internal fun BODY.bodyTitle() {
  div(classes = bodyHeaderCls) {
    a { href = "/"; span { style = "font-size:200%;"; +titleText } }
    rawHtml(nbsp.text)
    span { +"code reading practice" }
  }
}

internal fun BODY.bodyHeader(principal: UserIdPrincipal?,
                             readingBatContent: ReadingBatContent,
                             languageType: LanguageType,
                             loginPath: String,
                             message: String = "") {

  helpAndLogin(principal, loginPath)

  bodyTitle()

  div(classes = pretab) {
    p(classes = max) { +message }
  }

  nav {
    ul {
      for (lang in languageTypesInOrder) {
        if (readingBatContent.hasGroups(lang))
          li(classes = "h2") {
            if (languageType == lang) id = selected
            this@bodyHeader.addLink(lang.name, "/$challengeRoot/${lang.lowerName}")
          }
      }
    }
  }
}

internal fun defaultTab(readingBatContent: ReadingBatContent) =
  languageTypesInOrder
    .asSequence()
    .filter { readingBatContent.hasGroups(it) }
    .map { "/$challengeRoot/${it.lowerName}" }
    .firstOrNull()
    ?: throw InvalidConfigurationException("Missing default language")

internal fun BODY.addLink(text: String, url: String, newWindow: Boolean = false) {
  a { href = url; if (newWindow) target = "_blank"; +text }
}

internal fun BODY.backLink(vararg url: String) {
  br
  div(classes = backLinkCls) { a { href = "/" + url.toList().toPath(false); rawHtml("&larr; Back") } }
}

internal fun HTMLTag.rawHtml(html: String) = unsafe { raw(html) }

internal fun Int.rows(cols: Int) = if (this % cols == 0) this / cols else (this / cols) + 1
