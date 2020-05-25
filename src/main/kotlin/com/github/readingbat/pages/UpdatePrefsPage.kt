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
import com.github.readingbat.misc.Constants.BACK_PATH
import com.github.readingbat.misc.Constants.RETURN_PATH
import com.github.readingbat.misc.Endpoints.PREFS
import com.github.readingbat.misc.Endpoints.PRIVACY
import com.github.readingbat.misc.FormFields.CURR_PASSWORD
import com.github.readingbat.misc.FormFields.DELETE_ACCOUNT
import com.github.readingbat.misc.FormFields.NEW_PASSWORD
import com.github.readingbat.misc.FormFields.PREF_ACTION
import com.github.readingbat.misc.FormFields.UPDATE_PASSWORD
import com.github.readingbat.misc.PageUtils.hideShowButton
import com.github.readingbat.misc.UserId.Companion.isValidUserId
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.fetchPrincipal
import com.github.readingbat.server.queryParam
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

internal fun PipelineCall.prefsPage(content: ReadingBatContent,
                                    msg: String,
                                    isErrorMsg: Boolean = true): String =
  withRedisPool { redis ->
    val principal = fetchPrincipal()

    if (isValidUserId(principal, redis))
      prefsWithLoginPage(content, msg, isErrorMsg)
    else
      requestLogInPage(content)
  }

private fun PipelineCall.prefsWithLoginPage(content: ReadingBatContent,
                                            msg: String,
                                            isErrorMsg: Boolean) =
  createHTML()
    .html {
      val principal = fetchPrincipal()
      val returnPath = queryParam(RETURN_PATH) ?: "/"

      head {
        headDefault(content)
      }

      body {
        bodyTitle()

        val labelWidth = "width: 250;"
        val formName = "pform"

        div {
          h2 { +"ReadingBat Prefs" }

          if (msg.isNotEmpty())
            p { span { style = "color:${if (isErrorMsg) "red" else "green"};"; +msg } }

          h3 { +"Change password" }
          p { +"Password must contain at least 6 characters" }
          form {
            name = formName
            action = PREFS
            method = FormMethod.post
            table {
              tr {
                td { style = labelWidth; label { +"Current Password" } }
                td { input { type = InputType.password; size = "42"; name = CURR_PASSWORD; value = "" } }
                td { hideShowButton(formName, CURR_PASSWORD) }
              }
              tr {
                td { style = labelWidth; label { +"New Password" } }
                td { input { type = InputType.password; size = "42"; name = NEW_PASSWORD; value = "" } }
                td { hideShowButton(formName, NEW_PASSWORD) }
              }
              tr {
                td {}
                td { input { type = InputType.submit; name = PREF_ACTION; value = UPDATE_PASSWORD } }
              }
            }
          }

          h3 { +"Teacher Share" }
          p { +"Enter the email address of the teacher account. This will make your done page and solution code visible to that account." }
          form {
            action = PREFS
            method = FormMethod.post
            table {
              tr {
                td { style = labelWidth; label { +"Share To" } }
                td { input { type = InputType.text; size = "42"; name = "pdt"; value = "" } }
              }
              tr {
                td {}
                td { input { type = InputType.submit; name = "dosavepdt"; value = "Share" } }
              }
            }
          }

          h3 { +"Memo" }
          p { +"Generally this is left blank. A teacher may ask you to fill this in." }
          form {
            action = PREFS
            method = FormMethod.post
            input { type = InputType.hidden; name = "date"; value = "963892736" }
            table {
              tr {
                td { style = labelWidth; label { +"Memo" } }
                td { input { type = InputType.text; size = "42"; name = "real"; value = "" } }
              }
              tr {
                td {}
                td { input { type = InputType.submit; name = "dosavereal"; value = "Update Memo" } }
              }
            }
          }

          h3 { +"Delete Account" }
          p { +"Permanently delete account [${principal?.userId}] - cannot be undone" }
          form {
            action = PREFS
            method = FormMethod.post
            //onSubmit = "return formcheck()"
            onSubmit = "return confirm('Do you really want to submit the form?');"
            input { type = InputType.submit; name = PREF_ACTION; value = DELETE_ACCOUNT }
          }

          p { a { href = "$PRIVACY?$BACK_PATH=$PREFS&$RETURN_PATH=$returnPath"; +"privacy statement" } }
        }

        backLink(returnPath)
      }
    }
