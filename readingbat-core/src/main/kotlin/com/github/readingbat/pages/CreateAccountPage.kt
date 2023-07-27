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

import com.github.readingbat.common.CssNames.INDENT_1EM
import com.github.readingbat.common.Endpoints.CREATE_ACCOUNT_ENDPOINT
import com.github.readingbat.common.FormFields.CONFIRM_PASSWORD_PARAM
import com.github.readingbat.common.FormFields.EMAIL_PARAM
import com.github.readingbat.common.FormFields.FULLNAME_PARAM
import com.github.readingbat.common.FormFields.PASSWORD_PARAM
import com.github.readingbat.common.FormFields.RETURN_PARAM
import com.github.readingbat.common.Message
import com.github.readingbat.common.Message.Companion.EMPTY_MESSAGE
import com.github.readingbat.pages.PageUtils.backLink
import com.github.readingbat.pages.PageUtils.bodyTitle
import com.github.readingbat.pages.PageUtils.clickButtonScript
import com.github.readingbat.pages.PageUtils.displayMessage
import com.github.readingbat.pages.PageUtils.headDefault
import com.github.readingbat.pages.PageUtils.hideShowButton
import com.github.readingbat.pages.PageUtils.loadPingdomScript
import com.github.readingbat.pages.PageUtils.privacyStatement
import com.github.readingbat.server.Email
import com.github.readingbat.server.Email.Companion.EMPTY_EMAIL
import com.github.readingbat.server.FullName
import com.github.readingbat.server.FullName.Companion.EMPTY_FULLNAME
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.ServerUtils.queryParam
import kotlinx.html.*
import kotlinx.html.stream.createHTML

internal object CreateAccountPage {

  private const val CREATE_BUTTON = "CreateAccountButton"

  fun PipelineCall.createAccountPage(
    defaultFullName: FullName = EMPTY_FULLNAME,
    defaultEmail: Email = EMPTY_EMAIL,
    msg: Message = EMPTY_MESSAGE
  ) =
    createHTML()
      .html {
        val returnPath = queryParam(RETURN_PARAM, "/")

        head {
          headDefault()
          clickButtonScript(CREATE_BUTTON)
        }

        body {
          bodyTitle()

          h2 { +"Create Account" }

          div(classes = INDENT_1EM) {
            p {
              +"""
              Please enter information to create a new account. We use your email address as your account id 
              (just so it's memorable) and for password reset, not for spamming. The password 
              must have at least 6 characters.
            """.trimIndent()
            }

            p { span { style = "color:red"; this@body.displayMessage(msg) } }

            val inputFs = "font-size: 95%"
            val labelWidth = "width: 250"
            val formName = "pform"

            form {
              name = formName
              action = CREATE_ACCOUNT_ENDPOINT
              method = FormMethod.post
              table {
                tr {
                  td { style = labelWidth; label { +"Name" } }
                  td {
                    textInput {
                      style = inputFs; size = "42"; id = "fullname"; name = FULLNAME_PARAM; value =
                      defaultFullName.value
                    }
                  }
                }
                tr {
                  td { style = labelWidth; label { +"Email (used as account id)" } }
                  td {
                    textInput {
                      style = inputFs; size = "42"; id = "email"; name = EMAIL_PARAM; value =
                      defaultEmail.value
                    }
                  }
                }
                tr {
                  td { style = labelWidth; label { +"Password" } }
                  td {
                    passwordInput {
                      style = inputFs
                      size = "42"
                      id = "passwd"
                      name = PASSWORD_PARAM
                      value = ""
                    }
                  }
                  td { hideShowButton(formName, PASSWORD_PARAM) }
                }
                tr {
                  td { style = labelWidth; label { +"Confirm Password" } }
                  td {
                    passwordInput {
                      style = inputFs
                      size = "42"
                      id = "confirm_passwd"
                      name = CONFIRM_PASSWORD_PARAM
                      value = ""
                      onKeyPress = "click$CREATE_BUTTON(event)"
                    }
                  }
                  td { hideShowButton(formName, CONFIRM_PASSWORD_PARAM) }
                }
                hiddenInput { name = RETURN_PARAM; value = returnPath }
                tr {
                  td { }
                  td {
                    submitInput {
                      style = "font-size:100%; height:35; width:125"
                      id = CREATE_BUTTON
                      value = "Create Account"
                    }
                  }
                }
              }
            }
          }

          privacyStatement(CREATE_ACCOUNT_ENDPOINT)
          backLink(returnPath)
          loadPingdomScript()
        }
      }
}