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

import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.Constants.RESET_ID
import com.github.readingbat.misc.Constants.RETURN_PATH
import com.github.readingbat.misc.Endpoints.PASSWORD_CHANGE
import com.github.readingbat.misc.Endpoints.PASSWORD_RESET
import com.github.readingbat.misc.FormFields.CONFIRM_PASSWORD
import com.github.readingbat.misc.FormFields.NEW_PASSWORD
import com.github.readingbat.misc.FormFields.PREF_ACTION
import com.github.readingbat.misc.FormFields.UPDATE_PASSWORD
import com.github.readingbat.misc.FormFields.USERNAME
import com.github.readingbat.misc.PageUtils.hideShowButton
import com.github.readingbat.pages.PageCommon.backLink
import com.github.readingbat.pages.PageCommon.bodyTitle
import com.github.readingbat.pages.PageCommon.clickButtonScript
import com.github.readingbat.pages.PageCommon.headDefault
import com.github.readingbat.pages.PageCommon.privacyStatement
import com.github.readingbat.pages.PageCommon.rawHtml
import com.github.readingbat.pages.UpdateUserPrefsPage.labelWidth
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.queryParam
import kotlinx.html.*
import kotlinx.html.Entities.nbsp
import kotlinx.html.stream.createHTML

internal object PasswordResetPage {

  const val formName = "pform"
  const val passwordButton = "UpdatePasswordButton"

  fun PipelineCall.passwordResetPage(content: ReadingBatContent, msg: String = ""): String {
    val resetId = queryParam(RESET_ID) ?: "/"

    return if (resetId.isEmpty())
      requestPasswordResetPage(content, msg)
    else
      changePasswordResetPage(content, resetId)
  }


  fun PipelineCall.requestPasswordResetPage(content: ReadingBatContent, msg: String = "") =
    createHTML()
      .html {
        head { headDefault(content) }

        body {
          val returnPath = queryParam(RETURN_PATH) ?: "/"

          bodyTitle()

          p { span { style = "color:red;";if (msg.isNotEmpty()) +msg else rawHtml(nbsp.text) } }

          h2 { +"Password Reset" }

          div {
            style = "margin-left: 1em;"

            form {
              action = "$PASSWORD_RESET?$RETURN_PATH=$returnPath"
              method = FormMethod.post
              table {
                tr {
                  td { style = labelWidth; label { +"Email (used as account id)" } }
                  td {
                    input {
                      name = USERNAME
                      type = InputType.text
                      size = "50"
                    }
                  }
                }
                tr {
                  td {
                  }
                  td {
                    style = "padding-top:10;"
                    input {
                      style = "font-size:25px; height:35; width:  155;"
                      type = InputType.submit
                      value = "Send Password Reset"
                    }
                  }
                }
              }
            }
            p {
              +"""
            This will send an email with a temporary password to the account email address. 
            When you get the email, log in with the temporary password, and go to the prefs page 
            to enter a new password. If the email does not arrive, double-check that the email 
            address above is entered correctly.
          """.trimIndent()
            }

            this@body.privacyStatement(PASSWORD_RESET, returnPath)
          }

          backLink(returnPath)
        }
      }

  fun PipelineCall.changePasswordResetPage(content: ReadingBatContent, resetId: String = "") =
    createHTML()
      .html {
        head {
          headDefault(content)
          clickButtonScript(passwordButton)
        }

        body {
          val returnPath = queryParam(RETURN_PATH) ?: "/"

          bodyTitle()

          h3 { +"Change password" }
          p { +"Password must contain at least 6 characters" }
          form {
            name = formName
            action = PASSWORD_CHANGE
            method = FormMethod.post
            table {
              tr {
                td { style = labelWidth; label { +"New Password" } }
                td { input { type = InputType.password; size = "42"; name = NEW_PASSWORD; value = "" } }
                td { hideShowButton(formName, NEW_PASSWORD) }
              }
              tr {
                td { style = labelWidth; label { +"Confirm Password" } }
                td {
                  input {
                    type = InputType.password
                    size = "42"
                    name = CONFIRM_PASSWORD
                    value = ""
                    onKeyPress = "click$passwordButton(event);"
                  }
                }
                td { hideShowButton(formName, CONFIRM_PASSWORD) }
              }
              tr {
                td {}
                td {
                  input {
                    type = InputType.submit; id = passwordButton; name = PREF_ACTION; value = UPDATE_PASSWORD
                  }
                }
              }
            }
          }



          backLink(returnPath)
        }
      }
}