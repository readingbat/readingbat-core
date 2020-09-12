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

import com.github.readingbat.common.CSSNames.INDENT_1EM
import com.github.readingbat.common.CSSNames.INDENT_2EM
import com.github.readingbat.common.ClassCode
import com.github.readingbat.common.ClassCode.Companion.DISABLED_CLASS_CODE
import com.github.readingbat.common.Constants.LABEL_WIDTH
import com.github.readingbat.common.Endpoints.CREATE_ACCOUNT_ENDPOINT
import com.github.readingbat.common.Endpoints.TEACHER_PREFS_ENDPOINT
import com.github.readingbat.common.Endpoints.USER_PREFS_ENDPOINT
import com.github.readingbat.common.FormFields.CLASS_CODE_NAME_PARAM
import com.github.readingbat.common.FormFields.CONFIRM_PASSWORD_PARAM
import com.github.readingbat.common.FormFields.CURR_PASSWORD_PARAM
import com.github.readingbat.common.FormFields.DELETE_ACCOUNT
import com.github.readingbat.common.FormFields.JOIN_A_CLASS
import com.github.readingbat.common.FormFields.JOIN_CLASS
import com.github.readingbat.common.FormFields.NEW_PASSWORD_PARAM
import com.github.readingbat.common.FormFields.RETURN_PARAM
import com.github.readingbat.common.FormFields.UPDATE_PASSWORD
import com.github.readingbat.common.FormFields.USER_PREFS_ACTION_PARAM
import com.github.readingbat.common.FormFields.WITHDRAW_FROM_CLASS
import com.github.readingbat.common.Message
import com.github.readingbat.common.Message.Companion.EMPTY_MESSAGE
import com.github.readingbat.common.User
import com.github.readingbat.common.User.Companion.fetchActiveClassCode
import com.github.readingbat.common.User.Companion.fetchEnrolledClassCode
import com.github.readingbat.common.isValidUser
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.pages.HelpAndLogin.helpAndLogin
import com.github.readingbat.pages.PageUtils.backLink
import com.github.readingbat.pages.PageUtils.bodyTitle
import com.github.readingbat.pages.PageUtils.clickButtonScript
import com.github.readingbat.pages.PageUtils.displayMessage
import com.github.readingbat.pages.PageUtils.headDefault
import com.github.readingbat.pages.PageUtils.hideShowButton
import com.github.readingbat.pages.PageUtils.privacyStatement
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.ServerUtils.queryParam
import kotlinx.html.*
import kotlinx.html.InputType.submit
import kotlinx.html.stream.createHTML
import mu.KLogging
import redis.clients.jedis.Jedis

internal object UserPrefsPage : KLogging() {

  private const val formName = "pform"
  private const val passwordButton = "UpdatePasswordButton"
  private const val joinClassButton = "JoinClassButton"

  fun PipelineCall.userPrefsPage(content: ReadingBatContent,
                                 user: User?,
                                 redis: Jedis,
                                 msg: Message = EMPTY_MESSAGE,
                                 defaultClassCode: ClassCode = DISABLED_CLASS_CODE) =
    if (user.isValidUser(redis))
      userPrefsWithLoginPage(content, user, msg, defaultClassCode, redis)
    else
      requestLogInPage(content, redis)

  private fun PipelineCall.userPrefsWithLoginPage(content: ReadingBatContent,
                                                  user: User,
                                                  msg: Message,
                                                  defaultClassCode: ClassCode,
                                                  redis: Jedis) =
    createHTML()
      .html {
        head {
          headDefault(content)
          clickButtonScript(passwordButton, joinClassButton)
        }

        body {
          val activeClassCode = user.fetchActiveClassCode(redis)
          val returnPath = queryParam(RETURN_PARAM, "/")

          helpAndLogin(content, user, returnPath, activeClassCode.isEnabled, redis)

          bodyTitle()

          h2 { +"User Preferences" }

          if (msg.isAssigned())
            p { span { style = "color:${msg.color}"; this@body.displayMessage(msg) } }

          changePassword()
          joinOrWithdrawFromClass(user, defaultClassCode, redis)
          deleteAccount(user, redis)

          p(classes = INDENT_1EM) {
            a { href = "$TEACHER_PREFS_ENDPOINT?$RETURN_PARAM=$USER_PREFS_ENDPOINT"; +"Teacher Preferences" }
          }

          privacyStatement(USER_PREFS_ENDPOINT)

          backLink(returnPath)
        }
      }

  private fun BODY.changePassword() {
    h3 { +"Change password" }
    div(classes = INDENT_2EM) {
      p { +"Password must contain at least 6 characters" }
      form {
        name = formName
        action = USER_PREFS_ENDPOINT
        method = FormMethod.post
        table {
          tr {
            td { style = LABEL_WIDTH; label { +"Current Password" } }
            td { input { type = InputType.password; size = "42"; name = CURR_PASSWORD_PARAM; value = "" } }
            td { hideShowButton(formName, CURR_PASSWORD_PARAM) }
          }
          tr {
            td { style = LABEL_WIDTH; label { +"New Password" } }
            td { input { type = InputType.password; size = "42"; name = NEW_PASSWORD_PARAM; value = "" } }
            td { hideShowButton(formName, NEW_PASSWORD_PARAM) }
          }
          tr {
            td { style = LABEL_WIDTH; label { +"Confirm Password" } }
            td {
              input {
                type = InputType.password
                size = "42"
                name = CONFIRM_PASSWORD_PARAM
                value = ""
                onKeyPress = "click$passwordButton(event)"
              }
            }
            td { hideShowButton(formName, CONFIRM_PASSWORD_PARAM) }
          }
          tr {
            td {}
            td { input { type = submit; id = passwordButton; name = USER_PREFS_ACTION_PARAM; value = UPDATE_PASSWORD } }
          }
        }
      }
    }
  }

  private fun BODY.joinOrWithdrawFromClass(user: User, defaultClassCode: ClassCode, redis: Jedis) {
    val enrolledClass = user.fetchEnrolledClassCode(redis)
    if (enrolledClass.isEnabled) {
      h3 { +"Enrolled class" }
      val displayStr = enrolledClass.toDisplayString(redis)
      div(classes = INDENT_2EM) {
        p { +"Currently enrolled in class $enrolledClass." }
        p {
          form {
            action = USER_PREFS_ENDPOINT
            method = FormMethod.post
            onSubmit = "return confirm('Are you sure you want to withdraw from class $displayStr?')"
            input { type = submit; name = USER_PREFS_ACTION_PARAM; value = WITHDRAW_FROM_CLASS }
          }
        }
      }
    }
    else {
      h3 { +JOIN_A_CLASS }
      div(classes = INDENT_2EM) {
        p { +"Enter the class code your teacher gave you. This will make your progress visible to your teacher." }
        form {
          action = USER_PREFS_ENDPOINT
          method = FormMethod.post
          table {
            tr {
              td { style = LABEL_WIDTH; label { +"Class Code" } }
              td {
                input {
                  type = InputType.text
                  size = "42"
                  name = CLASS_CODE_NAME_PARAM
                  value = defaultClassCode.displayedValue
                  onKeyPress = "click$joinClassButton(event)"
                }
              }
            }
            tr {
              td {}
              td { input { type = submit; id = joinClassButton; name = USER_PREFS_ACTION_PARAM; value = JOIN_CLASS } }
            }
          }
        }
      }
    }
  }

/*
  private fun BODY.teacherShare() {
    h3 { +"Teacher Share" }
    div {
      style = divStyle
      p { +"Enter the email address of the teacher account. This will make your done page and solution code visible to that account." }
      form {
        action = USER_PREFS_ENDPOINT
        method = FormMethod.post
        table {
          tr {
            td { style = LABEL_WIDTH; label { +"Share To" } }
            td { input { type = InputType.text; size = "42"; name = "pdt"; value = "" } }
          }
          tr {
            td {}
            td { input { type = submit; name = USER_PREFS_ACTION; value = "Share" } }
          }
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
          td { style = LABEL_WIDTH; label { +"Memo" } }
          td { input { type = InputType.text; size = "42"; name = "real"; value = "" } }
        }
        tr {
          td {}
          td { input { type = submit; name = USER_PREFS_ACTION; value = "Update Memo" } }
        }
      }
    }
  }
  */

  private fun BODY.deleteAccount(user: User, redis: Jedis) {
    val email = user.email(redis)
    if (email.isNotBlank()) {
      h3 { +"Delete account" }
      div(classes = INDENT_2EM) {
        p { +"Permanently delete account [$email] -- this cannot be undone!" }
        form {
          action = USER_PREFS_ENDPOINT
          method = FormMethod.post
          onSubmit = "return confirm('Are you sure you want to permanently delete the account for $email ?')"
          input { type = submit; name = USER_PREFS_ACTION_PARAM; value = DELETE_ACCOUNT }
        }
      }
    }
  }

  fun PipelineCall.requestLogInPage(content: ReadingBatContent,
                                    redis: Jedis,
                                    msg: Message = EMPTY_MESSAGE) =
    createHTML()
      .html {
        head { headDefault(content) }

        body {
          val returnPath = queryParam(RETURN_PARAM, "/")

          helpAndLogin(content, null, returnPath, false, redis)

          bodyTitle()

          if (msg.isAssigned())
            p { span { style = "color:${msg.color}"; this@body.displayMessage(msg) } }

          h2 { +"Log in" }

          p {
            +"Please"
            a { href = "$CREATE_ACCOUNT_ENDPOINT?$RETURN_PARAM=$returnPath"; +" create an account " }
            +"or log in to an existing account to edit preferences."
          }

          privacyStatement(USER_PREFS_ENDPOINT)

          backLink(returnPath)

          content.pingdomUrl.also { if (it.isNotBlank()) script { src = it; async = true } }
        }
      }
}