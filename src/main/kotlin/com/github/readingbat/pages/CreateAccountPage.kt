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
import com.github.readingbat.misc.Constants.RETURN_PATH
import com.github.readingbat.misc.Endpoints.CREATE_ACCOUNT
import com.github.readingbat.misc.FormFields.CONFIRM_PASSWORD
import com.github.readingbat.misc.FormFields.EMAIL
import com.github.readingbat.misc.FormFields.NAME
import com.github.readingbat.misc.FormFields.PASSWORD
import com.github.readingbat.misc.PageUtils.hideShowButton
import com.github.readingbat.pages.PageCommon.backLink
import com.github.readingbat.pages.PageCommon.bodyTitle
import com.github.readingbat.pages.PageCommon.clickButtonScript
import com.github.readingbat.pages.PageCommon.displayMessage
import com.github.readingbat.pages.PageCommon.headDefault
import com.github.readingbat.pages.PageCommon.privacyStatement
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.ServerUtils.queryParam
import kotlinx.html.*
import kotlinx.html.stream.createHTML

internal object CreateAccountPage {

  fun PipelineCall.createAccountPage(content: ReadingBatContent,
                                     defaultName: String = "",
                                     defaultEmail: String = "",
                                     msg: String = "") =
    createHTML()
      .html {
        val returnPath = queryParam(RETURN_PATH) ?: "/"
        val createButton = "CreateAccountButton"

        head {
          headDefault(content)
          clickButtonScript(createButton)
        }

        body {
          bodyTitle()

          h2 { +"Create Account" }

          div {
            style = "margin-left: 1em;"

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
              action = CREATE_ACCOUNT
              method = FormMethod.post
              table {
                tr {
                  td { style = labelWidth; label { +"Name" } }
                  td {
                    input {
                      style = inputFs; type = InputType.text; size = "42"; name = NAME; value = defaultName
                    }
                  }
                }
                tr {
                  td { style = labelWidth; label { +"Email (used as account id)" } }
                  td {
                    input {
                      style = inputFs; type = InputType.text; size = "42"; name = EMAIL; value = defaultEmail
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

            this@body.privacyStatement(CREATE_ACCOUNT, returnPath)
          }

          backLink(returnPath)
        }
      }
}