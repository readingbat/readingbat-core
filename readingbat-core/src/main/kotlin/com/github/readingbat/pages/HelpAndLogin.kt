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

import com.github.readingbat.common.AuthRoutes.LOGOUT
import com.github.readingbat.common.ClassCodeRepository.toDisplayString
import com.github.readingbat.common.Endpoints.ABOUT_ENDPOINT
import com.github.readingbat.common.Endpoints.ADMIN_PREFS_ENDPOINT
import com.github.readingbat.common.Endpoints.ENABLE_STUDENT_MODE_ENDPOINT
import com.github.readingbat.common.Endpoints.ENABLE_TEACHER_MODE_ENDPOINT
import com.github.readingbat.common.Endpoints.HELP_ENDPOINT
import com.github.readingbat.common.Endpoints.OAUTH_LOGIN_GITHUB_ENDPOINT
import com.github.readingbat.common.Endpoints.OAUTH_LOGIN_GOOGLE_ENDPOINT
import com.github.readingbat.common.Endpoints.USER_PREFS_ENDPOINT
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
import kotlinx.html.BODY
import kotlinx.html.TD
import kotlinx.html.a
import kotlinx.html.br
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.img
import kotlinx.html.onClick
import kotlinx.html.span
import kotlinx.html.style
import kotlinx.html.table
import kotlinx.html.td
import kotlinx.html.tr

internal object HelpAndLogin {
  private val rootVals = listOf("", "/")

  // Google "G" logo — official colors, 18x18
  @Suppress("ktlint:standard:max-line-length")
  internal const val GOOGLE_SVG =
    """<svg width="18" height="18" viewBox="0 0 48 48" xmlns="http://www.w3.org/2000/svg">""" +
      """<path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"/>""" +
      """<path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"/>""" +
      """<path fill="#FBBC05" d="M10.53 28.59a14.5 14.5 0 0 1 0-9.18l-7.98-6.19a24.0 24.0 0 0 0 0 21.56l7.98-6.19z"/>""" +
      """<path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"/>""" +
      """</svg>"""

  // GitHub Octocat logo — white fill, 18x18
  @Suppress("ktlint:standard:max-line-length")
  internal const val GITHUB_SVG =
    """<svg width="18" height="18" viewBox="0 0 16 16" fill="white" xmlns="http://www.w3.org/2000/svg">""" +
      """<path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49""" +
      """-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82""" +
      """.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15""" +
      """-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82a7.64 7.64 0 0 1 2-.27c.68 0 1.36.09 2 .27 1.53-1.04""" +
      """ 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25""" +
      """.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.01 8.01 0 0 0 16 8c0-4.42-3.58-8-8-8z"/>""" +
      """</svg>"""

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

    div(classes = "float-right pt-2.5") {
      table {
        if (isDbmsEnabled()) {
          tr {
            td(classes = "text-right whitespace-nowrap") {
              if (user != null) {
                userInfoBlock(user, path)
              } else {
                a {
                  href = "#"
                  onClick = "openOAuthModal(); return false;"
                  +"log in"
                }
              }
            }
          }
        }
        tr {
          td(classes = "text-right whitespace-nowrap pt-1.5") {
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

            if (isDbmsEnabled() && user != null) {
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
        if (user != null && user.enrolledClassCode.isEnabled) {
          tr {
            td(classes = "text-right pt-1") {
              span(classes = "text-rb-header") {
                style = "color: $HEADER_COLOR"
                +user.enrolledClassCode.toDisplayString()
              }
            }
          }
        }
      }
    }

    if (isDbmsEnabled() && user == null) {
      oauthModal()
    }
  }

  private fun BODY.oauthModal() {
    div {
      id = "oauth-modal"
      onClick = "if(event.target===this) this.style.display='none';"
      style =
        """
        display:none;
        position:fixed; top:0; left:0; width:100%; height:100%;
        background:rgba(0,0,0,0.5);
        justify-content:center; align-items:center;
        z-index:1000;
        """.trimIndent()

      div {
        style =
          """
          background:white;
          border-radius:8px;
          padding:32px;
          min-width:300px;
          text-align:center;
          box-shadow:0 4px 24px rgba(0,0,0,0.2);
          position:relative;
          """.trimIndent()

        // Close button
        span {
          onClick = "document.getElementById('oauth-modal').style.display='none';"
          style =
            """
            position:absolute; top:8px; right:14px;
            font-size:22px; cursor:pointer; color:#888;
            """.trimIndent()
          rawHtml("&times;")
        }

        div(classes = "text-lg font-bold mb-2") {
          +"Sign In"
        }

        div(classes = "text-[#666] mb-5") {
          +"Choose a provider to continue:"
        }

        div {
          a {
            id = "oauth-google-btn"
            href = OAUTH_LOGIN_GOOGLE_ENDPOINT
            style =
              """
              display:flex; align-items:center; justify-content:center; gap:10px;
              padding:10px 20px;
              margin:8px 0;
              background-color:#4285f4;
              color:white;
              text-decoration:none;
              border-radius:4px;
              font-size:14px;
              """.trimIndent()
            rawHtml(GOOGLE_SVG)
            +"Sign in with Google"
          }

          a {
            id = "oauth-github-btn"
            href = OAUTH_LOGIN_GITHUB_ENDPOINT
            style =
              """
              display:flex; align-items:center; justify-content:center; gap:10px;
              padding:10px 20px;
              margin:8px 0;
              background-color:#333;
              color:white;
              text-decoration:none;
              border-radius:4px;
              font-size:14px;
              """.trimIndent()
            rawHtml(GITHUB_SVG)
            +"Sign in with GitHub"
          }
        }
      }
    }

    rawHtml(
      """
      <script>
      function openOAuthModal(returnUrl) {
        var gh = document.getElementById('oauth-github-btn');
        var go = document.getElementById('oauth-google-btn');
        if (returnUrl) {
          gh.href = '$OAUTH_LOGIN_GITHUB_ENDPOINT?return=' + encodeURIComponent(returnUrl);
          go.href = '$OAUTH_LOGIN_GOOGLE_ENDPOINT?return=' + encodeURIComponent(returnUrl);
        } else {
          gh.href = '$OAUTH_LOGIN_GITHUB_ENDPOINT';
          go.href = '$OAUTH_LOGIN_GOOGLE_ENDPOINT';
        }
        document.getElementById('oauth-modal').style.display = 'flex';
      }
      if (new URLSearchParams(window.location.search).get('login') === 'required') {
        openOAuthModal();
        history.replaceState(null, '', window.location.pathname);
      }
      </script>
      """.trimIndent(),
    )
  }

  private fun TD.userInfoBlock(user: User, loginPath: String) {
    table {
      style = "margin-left:auto"
      tr {
        val avatarUrl = user.avatarUrl
        if (avatarUrl != null) {
          td(classes = "align-middle p-0 pr-2") {
            img(classes = "rounded-full block") {
              src = avatarUrl
              width = "36"
              alt = "avatar"
              attributes["referrerpolicy"] = "no-referrer"
            }
          }
        }
        td(classes = "align-middle p-0 text-right") {
          +user.email.value
          br
          span(classes = "text-[100%] text-gray-500") {
            a {
              href = LOGOUT
              +"log out"
            }
          }
        }
      }
    }
  }
}
