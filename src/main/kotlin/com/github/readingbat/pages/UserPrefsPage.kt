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
import com.github.readingbat.misc.Constants.RETURN_PATH
import com.github.readingbat.misc.Endpoints.CREATE_ACCOUNT_ENDPOINT
import com.github.readingbat.misc.Endpoints.USER_PREFS_ENDPOINT
import com.github.readingbat.misc.FormFields.CLASS_CODE
import com.github.readingbat.misc.FormFields.CLASS_DESC
import com.github.readingbat.misc.FormFields.CONFIRM_PASSWORD
import com.github.readingbat.misc.FormFields.CREATE_CLASS
import com.github.readingbat.misc.FormFields.CURR_PASSWORD
import com.github.readingbat.misc.FormFields.DELETE_ACCOUNT
import com.github.readingbat.misc.FormFields.JOIN_CLASS
import com.github.readingbat.misc.FormFields.NEW_PASSWORD
import com.github.readingbat.misc.FormFields.UPDATE_PASSWORD
import com.github.readingbat.misc.FormFields.USER_PREFS_ACTION
import com.github.readingbat.misc.PageUtils.hideShowButton
import com.github.readingbat.misc.UserId
import com.github.readingbat.misc.UserId.Companion.classCodeEnrollmentKey
import com.github.readingbat.misc.UserId.Companion.isValidPrincipal
import com.github.readingbat.misc.UserPrincipal
import com.github.readingbat.pages.HelpAndLogin.helpAndLogin
import com.github.readingbat.pages.PageCommon.backLink
import com.github.readingbat.pages.PageCommon.bodyTitle
import com.github.readingbat.pages.PageCommon.clickButtonScript
import com.github.readingbat.pages.PageCommon.displayMessage
import com.github.readingbat.pages.PageCommon.headDefault
import com.github.readingbat.pages.PageCommon.privacyStatement
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.ServerUtils.fetchPrincipal
import com.github.readingbat.server.ServerUtils.queryParam
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import mu.KLogging

internal object UserPrefsPage : KLogging() {

  const val labelWidth = "width: 250;"
  const val formName = "pform"
  const val passwordButton = "UpdatePasswordButton"
  const val joinClassButton = "JoinClassButton"
  const val createClassButton = "CreateClassButton"

  fun PipelineCall.userPrefsPage(content: ReadingBatContent,
                                 msg: String,
                                 isErrorMsg: Boolean,
                                 defaultClassCode: String = ""): String {
    val principal = fetchPrincipal()
    return if (principal != null && isValidPrincipal(principal))
      prefsWithLoginPage(content, principal, msg, isErrorMsg, defaultClassCode)
    else
      requestLogInPage(content)
  }

  private fun PipelineCall.prefsWithLoginPage(content: ReadingBatContent,
                                              principal: UserPrincipal,
                                              msg: String,
                                              isErrorMsg: Boolean,
                                              defaultClassCode: String) =
    createHTML()
      .html {
        head {
          headDefault(content)
          clickButtonScript(passwordButton, joinClassButton, createClassButton)
        }

        body {
          bodyTitle()

          h2 { +"ReadingBat Prefs" }

          p { span { style = "color:${if (isErrorMsg) "red" else "green"};"; this@body.displayMessage(msg) } }

          val returnPath = queryParam(RETURN_PATH) ?: "/"

          changePassword()
          joinClass(defaultClassCode)
          createClass(principal)
          //teacherShare()
          //memo()
          deleteAccount(principal)
          privacyStatement(USER_PREFS_ENDPOINT, returnPath)
          backLink(returnPath)
        }
      }

  private fun BODY.changePassword() {
    h3 { +"Change password" }
    p { +"Password must contain at least 6 characters" }
    form {
      name = formName
      action = USER_PREFS_ENDPOINT
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
              type = InputType.submit; id = passwordButton; name = USER_PREFS_ACTION; value = UPDATE_PASSWORD
            }
          }
        }
      }
    }
  }

  private fun BODY.joinClass(defaultClassCode: String) {
    h3 { +"Join a class" }
    p { +"Enter the class code your teacher gave you. This will make your progress visible to your teacher." }
    form {
      action = USER_PREFS_ENDPOINT
      method = FormMethod.post
      table {
        tr {
          td { style = labelWidth; label { +"Class Code" } }
          td {
            input {
              type = InputType.text
              size = "42"
              name = CLASS_CODE
              value = defaultClassCode
              onKeyPress = "click$joinClassButton(event);"
            }
          }
        }
        tr {
          td {}
          td { input { type = InputType.submit; id = joinClassButton; name = USER_PREFS_ACTION; value = JOIN_CLASS } }
        }
      }
    }
  }

  private fun BODY.createClass(principal: UserPrincipal) {
    h3 { +"Create a class" }
    p { +"Enter a decription of your class." }
    form {
      action = USER_PREFS_ENDPOINT
      method = FormMethod.post
      table {
        tr {
          td { style = labelWidth; label { +"Class Description" } }
          td {
            input {
              type = InputType.text
              size = "42"
              name = CLASS_DESC
              value = ""
              onKeyPress = "click$createClassButton(event);"
            }
          }
        }
        tr {
          td {}
          td {
            input {
              type = InputType.submit; id = createClassButton; name = USER_PREFS_ACTION; value = CREATE_CLASS
            }
          }
        }
      }
    }

    displayClasses(principal)
  }

  private fun BODY.displayClasses(principal: UserPrincipal) {
    withRedisPool { redis ->
      if (redis != null) {
        val userId = UserId(principal.userId)
        logger.info { "Looking at id: ${userId.id}" }
        logger.info { "Looking at key: ${userId.userClassesKey}" }
        val ids = redis.smembers(userId.userClassesKey)
        if (ids.size > 0) {
          div {
            style = "margin-left: 15em;"

            table {
              style = "border-spacing: 15px 5px;"
              tr {
                th { +"Class Code" }
                th { +"Description" }
                th { +"Enrollees" }
              }
              ids.forEach { classCode ->
                tr {
                  td {
                    +classCode
                  }
                  td {
                    +(redis[UserId.classDescKey(classCode)] ?: "Missing Description")
                  }
                  td {
                    val userClassesKey = classCodeEnrollmentKey(classCode)
                    +(redis.smembers(userClassesKey).filter { it.isNotEmpty() }.count().toString())
                  }
                }
              }
            }
            br
          }
        }
      }
    }
  }

  private fun BODY.teacherShare() {
    h3 { +"Teacher Share" }
    p { +"Enter the email address of the teacher account. This will make your done page and solution code visible to that account." }
    form {
      action = USER_PREFS_ENDPOINT
      method = FormMethod.post
      table {
        tr {
          td { style = labelWidth; label { +"Share To" } }
          td { input { type = InputType.text; size = "42"; name = "pdt"; value = "" } }
        }
        tr {
          td {}
          td { input { type = InputType.submit; name = USER_PREFS_ACTION; value = "Share" } }
        }
      }
    }
  }

  private fun BODY.memo() {
    h3 { +"Memo" }
    p { +"Generally this is left blank. A teacher may ask you to fill this in." }
    form {
      action = USER_PREFS_ENDPOINT
      method = FormMethod.post
      input { type = InputType.hidden; name = "date"; value = "963892736" }
      table {
        tr {
          td { style = labelWidth; label { +"Memo" } }
          td { input { type = InputType.text; size = "42"; name = "real"; value = "" } }
        }
        tr {
          td {}
          td { input { type = InputType.submit; name = USER_PREFS_ACTION; value = "Update Memo" } }
        }
      }
    }
  }

  private fun BODY.deleteAccount(principal: UserPrincipal) {
    h3 { +"Delete account" }

    val email = withRedisPool { redis -> principal.email(redis) }
    if (email.isNotEmpty()) {
      p { +"Permanently delete account [$email] -- cannot be undone!" }
      form {
        action = USER_PREFS_ENDPOINT
        method = FormMethod.post
        onSubmit =
          "return confirm('Are you sure you want to permanently delete the account for $email ?');"
        input { type = InputType.submit; name = USER_PREFS_ACTION; value = DELETE_ACCOUNT }
      }
    }
  }

  fun PipelineCall.requestLogInPage(content: ReadingBatContent, isErrorMsg: Boolean = false, msg: String = "") =
    createHTML()
      .html {
        head { headDefault(content) }

        body {
          val returnPath = queryParam(RETURN_PATH) ?: "/"

          helpAndLogin(fetchPrincipal(), returnPath)

          bodyTitle()

          p { span { style = "color:${if (isErrorMsg) "red" else "green"};"; this@body.displayMessage(msg) } }

          h2 { +"Log in" }

          p {
            +"Please"
            a { href = "$CREATE_ACCOUNT_ENDPOINT?$RETURN_PATH=$returnPath"; +" create an account " }
            +"or log in to an existing account to edit preferences."
          }
          privacyStatement(USER_PREFS_ENDPOINT, returnPath)

          backLink(returnPath)
        }
      }
}