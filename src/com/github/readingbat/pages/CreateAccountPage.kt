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
import com.github.readingbat.misc.FormFields
import kotlinx.html.*
import kotlinx.html.stream.createHTML

internal fun createAccountPage(content: ReadingBatContent,
                               defaultUserName: String,
                               msg: String,
                               returnPath: String) =
  createHTML()
    .html {
      val createButton = "createAccountButton"

      head {
        headDefault(content)

        script {
          rawHtml(
            """
              function clickCreate(event) {
                if (event != null && event.keyCode == 13) {
                  event.preventDefault();
                  document.getElementById('$createButton').click();
                }
              }
            """.trimIndent())
        }
      }

      body {
        bodyTitle()

        h2 { +"Create Account" }

        div {
          style = "margin-left: 1em;"

          p {
            style = "max-width:800px"
            +"""
          Please enter information to create a new account. We use your email address as your account id 
          (just so it's memorable) and for password reset, not for spamming. The password 
          must have at least 6 characters.
          """.trimIndent()
          }

          p {}

          if (msg.isNotEmpty())
            p { span(classes = "no") { +msg } }

          p {}

          val inputFs = "font-size: 95%;"
          val labelWidth = "width: 250;"
          val formName = "pform"

          form {
            name = formName
            action = CREATE_ACCOUNT
            method = FormMethod.post
            table {
              tr {
                td {
                  style = labelWidth
                  label { +"Email (used as account id)" }
                }
                td {
                  input {
                    style = inputFs
                    type = InputType.text
                    size = "42"
                    name = FormFields.USERNAME
                    value = defaultUserName
                  }
                }
              }
              tr {
                td {
                  style = labelWidth
                  label { +"Password" }
                }
                td {
                  input {
                    style = inputFs
                    type = InputType.password
                    size = "42"
                    name = FormFields.PASSWORD
                    value = ""
                    onKeyPress = "clickCreate(event);"
                  }
                }
                td {
                  button {
                    style = "font-size:85%;"
                    onClick =
                      """
                     var pw=document.$formName.${FormFields.PASSWORD}.type=="password"; 
                     document.$formName.${FormFields.PASSWORD}.type=pw?"text":"password"; 
                     return false;
                    """.trimIndent()
                    +"show/hide"
                  }
                }
              }
              hiddenInput { name = RETURN_PATH; value = returnPath }
              tr {
                td { }
                td {
                  input {
                    style = "font-size : 25px; height: 35; width: 115;"
                    type = InputType.submit
                    id = createButton
                    //name = "dosavecreate"
                    value = "Create Account"
                  }
                }
              }
            }
          }
          p { a { href = "/privacy.html"; +"privacy statement" } }
        }

        backLink(returnPath)
      }
    }
