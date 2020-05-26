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

import com.github.pambrose.common.redis.RedisUtils.withRedisPool
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.Constants.DBMS_DOWN
import com.github.readingbat.misc.Constants.INVALID_RESET_ID
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
import com.github.readingbat.misc.UserId
import com.github.readingbat.pages.PageCommon.backLink
import com.github.readingbat.pages.PageCommon.bodyTitle
import com.github.readingbat.pages.PageCommon.clickButtonScript
import com.github.readingbat.pages.PageCommon.headDefault
import com.github.readingbat.pages.PageCommon.privacyStatement
import com.github.readingbat.pages.PageCommon.rawHtml
import com.github.readingbat.pages.UpdateUserPrefsPage.labelWidth
import com.github.readingbat.posts.PasswordReset.ResetPasswordException
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.queryParam
import kotlinx.html.*
import kotlinx.html.Entities.nbsp
import kotlinx.html.stream.createHTML
import mu.KLogging

internal object PasswordResetPage : KLogging() {

  const val formName = "pform"
  const val passwordButton = "UpdatePasswordButton"

  fun PipelineCall.passwordResetPage(content: ReadingBatContent, resetId: String, msg: String): String =
    if (resetId.isEmpty())
      requestPasswordResetPage(content, msg)
    else {
      try {
        val username =
          withRedisPool { redis ->
            if (redis == null)
              throw ResetPasswordException(DBMS_DOWN)
            val passwordResetKey = UserId.passwordResetKey(resetId)
            redis.get(passwordResetKey) ?: throw ResetPasswordException(INVALID_RESET_ID)
          }
        changePasswordResetPage(content, username, resetId, msg)
      } catch (e: ResetPasswordException) {
        logger.info(e) { e.message }
        requestPasswordResetPage(content, e.message ?: "Unable to reset password")
      }
    }

  private fun PipelineCall.requestPasswordResetPage(content: ReadingBatContent, msg: String = "") =
    createHTML()
      .html {
        head { headDefault(content) }

        body {
          val returnPath = queryParam(RETURN_PATH) ?: "/"

          bodyTitle()

          p { span { style = "color:red;"; if (msg.isNotEmpty()) +msg else rawHtml(nbsp.text) } }

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
                This will send an email with a link that will allow you th set your password. 
                When you get the email, click on the link (or enter the URL in your browser) and  
                enter a new password. If the email does not arrive, double-check that the email 
                address above is entered correctly.
              """.trimIndent()
            }

            this@body.privacyStatement(PASSWORD_RESET, returnPath)
          }

          backLink(returnPath)
        }
      }

  private fun PipelineCall.changePasswordResetPage(content: ReadingBatContent,
                                                   username: String,
                                                   resetId: String,
                                                   msg: String) =
    createHTML()
      .html {
        head {
          headDefault(content)
          clickButtonScript(passwordButton)
        }

        body {
          val returnPath = queryParam(RETURN_PATH) ?: "/"

          bodyTitle()

          p { span { style = "color:red;";if (msg.isNotEmpty()) +msg else rawHtml(nbsp.text) } }

          h3 { +"Change password for $username" }
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
                td {
                  input {
                    type = InputType.hidden; name = RESET_ID; value = resetId
                  }
                }
                td {
                  input {
                    style = "font-size:25px; height:35; width:  155;"
                    type = InputType.submit
                    id = passwordButton
                    name = PREF_ACTION
                    value = UPDATE_PASSWORD
                  }
                }
              }
            }
          }

          backLink(returnPath)
        }
      }
}