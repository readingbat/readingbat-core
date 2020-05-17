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
import kotlinx.html.*
import kotlinx.html.stream.createHTML

internal fun prefsPage(readingBatContent: ReadingBatContent) =
  createHTML()
    .html {

      head {
        headDefault(readingBatContent)
      }

      body {
        bodyTitle()

        div {
          h2 { +"ReadingBat Prefs" }
        }
      }
    }

internal fun createAccount(readingBatContent: ReadingBatContent) =
  createHTML()
    .html {

      head {
        headDefault(readingBatContent)
      }

      body {
        bodyTitle()

        h2 { +"Create Account" }
        p {
          style = "max-width:800px"
          +"""
          Please enter information to create a new account. We use your email address as your account id 
          (just so it's memorable) and for password reset, not for spamming. The password 
          must have at least 6 characters.
          """.trimIndent()
        }
        p {
        }
        val inputFs = "font-size: 95%;"
        val labelWidth = "width: 250;"
        form {
          name = "pform"
          action = "/pref"
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
                  name = "uname"
                  value = ""
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
                  name = "pw1"
                  value = ""
                }
              }
              td {
                button {
                  style = "font-size:85%;"
                  onClick =
                    """var pw=document.pform.pw1.type=="password"; document.pform.pw1.type=pw?"text":"password"; return false;"""
                  +"show/hide"
                }
              }
            }
            tr {
              td { }
              td {
                input {
                  style = "font-size : 25px; height: 35; width: 115;"
                  type = InputType.submit
                  name = "dosavecreate"
                  value = "Create Account"
                }
              }
            }
          }
        }
        p { a { href = "/privacy.html"; +"privacy statement" } }
      }
    }

internal fun privacy(readingBatContent: ReadingBatContent) =
  createHTML()
    .html {

      head {
        headDefault(readingBatContent)
      }

      body {
        bodyTitle()

        h2 { +"ReadingBat Privacy" }
        p {
          +"""
              ReadingBat is free -- anyone can access the site to learn and practice coding. 
              The materials are copyright Paul Ambrose. We will not send you any marketing email (spam), and we 
              will not sell your name or contact information to anyone for marketing. We will not identify you, 
              your name or email address (if we should know them) in anything we make public. We collect regular 
              web server logs, and may use the data and submitted code as part of research into teaching technology 
              in action, but we will never make public specific names or email addresses.
              """.trimIndent()
        }

        p {
          +"If you have any thoughts or suggestions about this server, please don't hesitate to email me at: "
          a {
            href = "mailto:pambrose@mac.com?subject=ReadingBat"
            +"pambrose@mac.com"
          }
        }
      }
    }
