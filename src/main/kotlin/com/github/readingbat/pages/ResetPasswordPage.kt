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
import com.github.readingbat.misc.Constants
import com.github.readingbat.misc.Endpoints
import com.github.readingbat.misc.FormFields.USERNAME
import com.github.readingbat.pages.PageCommon.backLink
import com.github.readingbat.pages.PageCommon.bodyTitle
import com.github.readingbat.pages.PageCommon.headDefault
import com.github.readingbat.pages.PageCommon.privacyStatement
import com.github.readingbat.pages.UpdateUserPrefsPage.labelWidth
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.queryParam
import kotlinx.html.*
import kotlinx.html.stream.createHTML

internal object ResetPasswordPage {

  fun PipelineCall.resetPasswordPage(content: ReadingBatContent) =
    createHTML()
      .html {

        head { headDefault(content) }

        body {
          val returnPath = queryParam(Constants.RETURN_PATH) ?: "/"

          bodyTitle()

          h2 { +"Reset Password" }

          div {
            style = "margin-left: 1em;"

            form {
              action = "/"
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

            this@body.privacyStatement(Endpoints.USER_PREFS, returnPath)
          }

          backLink(returnPath)
        }
      }
}