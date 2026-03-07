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
import com.github.readingbat.common.Endpoints.ENABLE_STUDENT_MODE_ENDPOINT
import com.github.readingbat.common.Endpoints.ENABLE_TEACHER_MODE_ENDPOINT
import com.github.readingbat.common.Endpoints.HELP_ENDPOINT
import com.github.readingbat.common.Endpoints.OAUTH_LOGIN_ENDPOINT
import com.github.readingbat.common.Endpoints.USER_PREFS_ENDPOINT
import com.github.readingbat.common.FormFields.RETURN_PARAM
import com.github.readingbat.common.User
import com.github.readingbat.common.User.Companion.queryPreviousTeacherClassCode
import com.github.readingbat.common.isAdminUser
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.dsl.isDbmsEnabled
import com.github.readingbat.dsl.isProduction
import com.github.readingbat.pages.ChallengePage.HEADER_COLOR
import com.github.readingbat.server.ServerUtils.firstNonEmptyLanguageType
import kotlinx.html.BODY
import kotlinx.html.TD
import kotlinx.html.a
import kotlinx.html.br
import kotlinx.html.div
import kotlinx.html.img
import kotlinx.html.span
import kotlinx.html.style
import kotlinx.html.table
import kotlinx.html.td
import kotlinx.html.tr

internal object HelpAndLogin {
  private val rootVals = listOf("", "/")

  fun BODY.helpAndLogin(
    content: ReadingBatContent,
    user: User?,
    loginPath: String,
    teacherMode: Boolean,
  ) {
    val previousClassCode = queryPreviousTeacherClassCode(user)
    val path =
      if (loginPath in rootVals)
        firstNonEmptyLanguageType(content, user?.defaultLanguage).contentRoot
      else
        loginPath

    div {
      style = "float:right; padding-top:10px"
      table {
        if (isDbmsEnabled()) {
          tr {
            td {
              style = "text-align:right; white-space:nowrap"
              if (user.isNotNull()) {
                userInfoBlock(user, path)
              } else {
                a {
                  href = OAUTH_LOGIN_ENDPOINT
                  +"log in"
                }
              }
            }
          }
        }
        tr {
          td {
            style = "text-align:right; white-space:nowrap; padding-top:6px"

            if (previousClassCode.isEnabled) {
              val (endpoint, msg) =
                if (teacherMode)
                  ENABLE_STUDENT_MODE_ENDPOINT to "student mode"
                else
                  ENABLE_TEACHER_MODE_ENDPOINT to "teacher mode"
              a {
                href = "$endpoint?$RETURN_PARAM=$loginPath"
                +msg
              }
              +" | "
            }

            a {
              href = "$ABOUT_ENDPOINT?$RETURN_PARAM=$loginPath"
              +"about"
            }
            +" | "
            a {
              href = "$HELP_ENDPOINT?$RETURN_PARAM=$loginPath"
              +"help"
            }

            if (isDbmsEnabled()) {
              if (!isProduction() || user.isAdminUser()) {
                +" | "
                a {
                  href = "$ADMIN_PREFS_ENDPOINT?$RETURN_PARAM=$loginPath"
                  +"admin"
                }
              }

              +" | "
              a {
                href = "$USER_PREFS_ENDPOINT?$RETURN_PARAM=$loginPath"
                +"prefs"
              }
            }
          }
        }
        if (user.isNotNull() && user.enrolledClassCode.isEnabled) {
          tr {
            td {
              style = "text-align:right; padding-top:4px"
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

  private fun TD.userInfoBlock(user: User, loginPath: String) {
    table {
      tr {
        val avatarUrl = user.avatarUrl
        if (avatarUrl != null) {
          td {
            style = "vertical-align:middle; padding:0; padding-right:8px"
            img {
              src = avatarUrl
              width = "36"
              alt = "avatar"
              style = "border-radius:50%; display:block"
              attributes["referrerpolicy"] = "no-referrer"
            }
          }
        }
        td {
          style = "vertical-align:middle; padding:0; text-align:right"
          +user.email.value
          br
          span {
            style = "font-size:100%; color:gray"
            a {
              href = "$LOGOUT?$RETURN_PARAM=$loginPath"
              +"log out"
            }
          }
        }
      }
    }
  }
}
