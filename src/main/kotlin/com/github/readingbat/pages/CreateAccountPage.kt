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
import com.github.readingbat.misc.CSSNames.INDENT_1EM
import com.github.readingbat.misc.Constants.RETURN_PATH
import com.github.readingbat.misc.Endpoints.CREATE_ACCOUNT_ENDPOINT
import com.github.readingbat.misc.Endpoints.CREATE_ACCOUNT_POST_ENDPOINT
import com.github.readingbat.misc.FormFields.CONFIRM_PASSWORD
import com.github.readingbat.misc.FormFields.EMAIL
import com.github.readingbat.misc.FormFields.FULLNAME
import com.github.readingbat.misc.FormFields.PASSWORD
import com.github.readingbat.misc.Message
import com.github.readingbat.misc.Message.Companion.EMPTY_MESSAGE
import com.github.readingbat.misc.PageUtils.hideShowButton
import com.github.readingbat.pages.PageUtils.backLink
import com.github.readingbat.pages.PageUtils.bodyTitle
import com.github.readingbat.pages.PageUtils.clickButtonScript
import com.github.readingbat.pages.PageUtils.displayMessage
import com.github.readingbat.pages.PageUtils.headDefault
import com.github.readingbat.pages.PageUtils.privacyStatement
import com.github.readingbat.server.Email
import com.github.readingbat.server.Email.Companion.EMPTY_EMAIL
import com.github.readingbat.server.FullName
import com.github.readingbat.server.FullName.Companion.EMPTY_FULLNAME
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.ServerUtils.queryParam
import kotlinx.html.FormMethod
import kotlinx.html.InputType
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.head
import kotlinx.html.hiddenInput
import kotlinx.html.html
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.onKeyPress
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.stream.createHTML
import kotlinx.html.style
import kotlinx.html.table
import kotlinx.html.td
import kotlinx.html.tr

internal object CreateAccountPage {

  fun PipelineCall.createAccountPage(content: ReadingBatContent,
                                     defaultFullName: FullName = EMPTY_FULLNAME,
                                     defaultEmail: Email = EMPTY_EMAIL,
                                     msg: Message = EMPTY_MESSAGE) =
    createHTML()
      .html {
        val returnPath = queryParam(RETURN_PATH, "/")
        val createButton = "CreateAccountButton"

        head {
          headDefault(content)
          clickButtonScript(createButton)
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

            p { span { style = "color:red;"; this@body.displayMessage(msg) } }

            val inputFs = "font-size: 95%;"
            val labelWidth = "width: 250;"
            val formName = "pform"

            form {
              name = formName
              action = CREATE_ACCOUNT_POST_ENDPOINT
              method = FormMethod.post
              table {
                tr {
                  td { style = labelWidth; label { +"Name" } }
                  td {
                    input {
                      style = inputFs; type = InputType.text; size = "42"; name = FULLNAME; value =
                      defaultFullName.value
                    }
                  }
                }
                tr {
                  td { style = labelWidth; label { +"Email (used as account id)" } }
                  td {
                    input {
                      style = inputFs; type = InputType.text; size = "42"; name = EMAIL; value = defaultEmail.value
                    }
                  }
                }
                tr {
                  td { style = labelWidth; label { +"Password" } }
                  td {
                    input {
                      style = inputFs
                      type = InputType.password
                      size = "42"
                      name = PASSWORD
                      value = ""
                    }
                  }
                  td { hideShowButton(formName, PASSWORD) }
                }
                tr {
                  td { style = labelWidth; label { +"Confirm Password" } }
                  td {
                    input {
                      style = inputFs
                      type = InputType.password
                      size = "42"
                      name = CONFIRM_PASSWORD
                      value = ""
                      onKeyPress = "click$createButton(event);"
                    }
                  }
                  td { hideShowButton(formName, CONFIRM_PASSWORD) }
                }
                hiddenInput { name = RETURN_PATH; value = returnPath }
                tr {
                  td { }
                  td {
                    input {
                      style = "font-size : 25px; height: 35; width: 115;"
                      id = createButton
                      type = InputType.submit
                      value = "Create Account"
                    }
                  }
                }
              }
            }
          }

          privacyStatement(CREATE_ACCOUNT_ENDPOINT, returnPath)

          backLink(returnPath)
        }
      }
}