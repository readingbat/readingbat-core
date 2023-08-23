/*
 * Copyright © 2023 Paul Ambrose (pambrose@mac.com)
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

import com.github.pambrose.common.util.isNotNull
import com.github.readingbat.common.AuthRoutes.LOGOUT
import com.github.readingbat.common.Endpoints.ABOUT_ENDPOINT
import com.github.readingbat.common.Endpoints.ADMIN_PREFS_ENDPOINT
import com.github.readingbat.common.Endpoints.CREATE_ACCOUNT_ENDPOINT
import com.github.readingbat.common.Endpoints.ENABLE_STUDENT_MODE_ENDPOINT
import com.github.readingbat.common.Endpoints.ENABLE_TEACHER_MODE_ENDPOINT
import com.github.readingbat.common.Endpoints.HELP_ENDPOINT
import com.github.readingbat.common.Endpoints.PASSWORD_RESET_ENDPOINT
import com.github.readingbat.common.Endpoints.USER_PREFS_ENDPOINT
import com.github.readingbat.common.FormFields.EMAIL_PARAM
import com.github.readingbat.common.FormFields.PASSWORD_PARAM
import com.github.readingbat.common.FormFields.RETURN_PARAM
import com.github.readingbat.common.User
import com.github.readingbat.common.User.Companion.queryPreviousTeacherClassCode
import com.github.readingbat.common.isAdminUser
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.dsl.isDbmsEnabled
import com.github.readingbat.dsl.isProduction
import com.github.readingbat.pages.ChallengePage.HEADER_COLOR
import com.github.readingbat.pages.PageUtils.rawHtml
import com.github.readingbat.server.ServerUtils.firstNonEmptyLanguageType
import kotlinx.html.*

internal object HelpAndLogin {

  private val rootVals = listOf("", "/")

  fun BODY.helpAndLogin(
    content: ReadingBatContent,
    user: User?,
    loginPath: String,
    teacherMode: Boolean
  ) {
    val previousClassCode = queryPreviousTeacherClassCode(user)
    val path =
      if (loginPath in rootVals)
        firstNonEmptyLanguageType(content, user?.defaultLanguage).contentRoot
      else
        loginPath

    if (isDbmsEnabled())
      div {
        style = "float:right; margin:0px; border: 1px solid lightgray; margin-left: 10px; padding: 5px"
        table {
          if (user.isNotNull()) logoutOption(user, path) else loginOption(path)
        }
      }

    div {
      style = "float:right"
      table {
        tr {
          td {
            style = "padding-top:10px; text-align:center"
            colSpan = "1"

            if (previousClassCode.isEnabled) {
              val (endpoint, msg) =
                if (teacherMode)
                  ENABLE_STUDENT_MODE_ENDPOINT to "student mode"
                else
                  ENABLE_TEACHER_MODE_ENDPOINT to "teacher mode"
              a { href = "$endpoint?$RETURN_PARAM=$loginPath"; +msg }
              +" | "
            }

            a { href = "$ABOUT_ENDPOINT?$RETURN_PARAM=$loginPath"; +"about" }
            +" | "
            a { href = "$HELP_ENDPOINT?$RETURN_PARAM=$loginPath"; +"help" }

            if (isDbmsEnabled()) {
              if (!isProduction() || user.isAdminUser()) {
                +" | "
                a { href = "$ADMIN_PREFS_ENDPOINT?$RETURN_PARAM=$loginPath"; +"admin" }
              }

              +" | "
              a { href = "$USER_PREFS_ENDPOINT?$RETURN_PARAM=$loginPath"; +"prefs" }
            }
          }
        }
        tr {
          td {
            style = "padding-top:10px; text-align:center"
            colSpan = "1"
            if (user.isNotNull() && user.enrolledClassCode.isEnabled) {
              span {
                style = "color: $HEADER_COLOR"
                +user.enrolledClassCode.toDisplayString()
              }
            }
          }
        }
      }
    }
  }

  private fun TABLE.logoutOption(user: User, loginPath: String) {
    tr {
      val email = user.email
      val elems = email.value.split("@")
      td {
        +elems[0]
        if (elems.size > 1) {
          br; +"@${elems[1]}"
        }
      }
    }

    tr {
      td {
        /*
    a {
      href = "/doc/practice/code-badges.html"; img {
      width = "30"; style = "vertical-align: middle"; src = "$STATIC_ROOT/s5j.png"
    }
    }
     */
        +"["; a { href = "$LOGOUT?$RETURN_PARAM=$loginPath"; +"log out" }; +"]"
      }
    }
  }

  private fun TABLE.loginOption(loginPath: String) {
    val topFocus = "loginTpFocus"
    val bottomFocus = "loginBottomFocus"

    form {
      method = FormMethod.post
      action = loginPath

      span { tabIndex = "1"; onFocus = "document.querySelector('.$bottomFocus').focus()" }

      this@loginOption.tr {
        td { +"id/email" }
        td {
          textInput(classes = topFocus) {
            id = EMAIL_PARAM; name = EMAIL_PARAM; size = "20"; placeholder = "username"; tabIndex = "2"
          }
        }
      }

      this@loginOption.tr {
        td { +"password" }
        td {
          passwordInput(classes = bottomFocus) {
            id = PASSWORD_PARAM; name = PASSWORD_PARAM; size = "20"; placeholder = "password"; tabIndex = "3"
          }
        }
      }

      this@loginOption.tr {
        td {}
        td { submitInput { id = "login"; name = "dologin"; value = "log in" } }
      }

      span { tabIndex = "4"; onFocus = "document.querySelector('.$topFocus').focus()" }

      hiddenInput { name = "fromurl"; value = loginPath }
    }

    // Set focus to email field
    script { rawHtml("""document.getElementById("$EMAIL_PARAM").focus()""") }

    tr {
      td {
        colSpan = "2"
        a { href = "$PASSWORD_RESET_ENDPOINT?$RETURN_PARAM=$loginPath"; +"forgot password" }
        +" | "
        a { href = "$CREATE_ACCOUNT_ENDPOINT?$RETURN_PARAM=$loginPath"; +"create account" }
      }
    }
  }
}