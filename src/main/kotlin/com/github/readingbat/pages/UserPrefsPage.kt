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
import com.github.readingbat.misc.CSSNames.INDENT_2EM
import com.github.readingbat.misc.ClassCode
import com.github.readingbat.misc.ClassCode.Companion.STUDENT_CLASS_CODE
import com.github.readingbat.misc.Constants.LABEL_WIDTH
import com.github.readingbat.misc.Constants.RETURN_PATH
import com.github.readingbat.misc.Endpoints.CREATE_ACCOUNT_ENDPOINT
import com.github.readingbat.misc.Endpoints.TEACHER_PREFS_ENDPOINT
import com.github.readingbat.misc.Endpoints.USER_PREFS_ENDPOINT
import com.github.readingbat.misc.Endpoints.USER_PREFS_POST_ENDPOINT
import com.github.readingbat.misc.FormFields.CLASS_CODE_NAME
import com.github.readingbat.misc.FormFields.CONFIRM_PASSWORD
import com.github.readingbat.misc.FormFields.CURR_PASSWORD
import com.github.readingbat.misc.FormFields.DELETE_ACCOUNT
import com.github.readingbat.misc.FormFields.JOIN_CLASS
import com.github.readingbat.misc.FormFields.NEW_PASSWORD
import com.github.readingbat.misc.FormFields.UPDATE_PASSWORD
import com.github.readingbat.misc.FormFields.USER_PREFS_ACTION
import com.github.readingbat.misc.FormFields.WITHDRAW_FROM_CLASS
import com.github.readingbat.misc.Message
import com.github.readingbat.misc.Message.Companion.EMPTY_MESSAGE
import com.github.readingbat.misc.PageUtils.hideShowButton
import com.github.readingbat.misc.User
import com.github.readingbat.misc.User.Companion.fetchActiveClassCode
import com.github.readingbat.misc.User.Companion.fetchEnrolledClassCode
import com.github.readingbat.misc.isValidUser
import com.github.readingbat.pages.HelpAndLogin.helpAndLogin
import com.github.readingbat.pages.PageCommon.backLink
import com.github.readingbat.pages.PageCommon.bodyTitle
import com.github.readingbat.pages.PageCommon.clickButtonScript
import com.github.readingbat.pages.PageCommon.displayMessage
import com.github.readingbat.pages.PageCommon.headDefault
import com.github.readingbat.pages.PageCommon.privacyStatement
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
                                 defaultClassCode: ClassCode = STUDENT_CLASS_CODE) =
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
        val activeClassCode = user.fetchActiveClassCode(redis)

        head {
          headDefault(content)
          clickButtonScript(passwordButton, joinClassButton)
        }

        body {
          val returnPath = queryParam(RETURN_PATH, "/")

          helpAndLogin(user, returnPath, activeClassCode.isTeacherMode, redis)

          bodyTitle()

          h2 { +"ReadingBat User Preferences" }

          p { span { style = "color:${if (msg.isError) "red" else "green"};"; this@body.displayMessage(msg) } }

          changePassword()
          joinOrWithdrawFromClass(user, defaultClassCode, redis)
          deleteAccount(user, redis)

          p(classes = INDENT_1EM) {
            a { href = "$TEACHER_PREFS_ENDPOINT?$RETURN_PATH=$returnPath"; +"Teacher Preferences" }
          }

          privacyStatement(USER_PREFS_ENDPOINT, returnPath)

          backLink(returnPath)
        }
      }

  private fun BODY.changePassword() {
    h3 { +"Change password" }
    div(classes = INDENT_2EM) {
      p { +"Password must contain at least 6 characters" }
      form {
        name = formName
        action = USER_PREFS_POST_ENDPOINT
        method = FormMethod.post
        table {
          tr {
            td { style = LABEL_WIDTH; label { +"Current Password" } }
            td { input { type = InputType.password; size = "42"; name = CURR_PASSWORD; value = "" } }
            td { hideShowButton(formName, CURR_PASSWORD) }
          }
          tr {
            td { style = LABEL_WIDTH; label { +"New Password" } }
            td { input { type = InputType.password; size = "42"; name = NEW_PASSWORD; value = "" } }
            td { hideShowButton(formName, NEW_PASSWORD) }
          }
          tr {
            td { style = LABEL_WIDTH; label { +"Confirm Password" } }
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
            td { input { type = submit; id = passwordButton; name = USER_PREFS_ACTION; value = UPDATE_PASSWORD } }
          }
        }
      }
    }
  }

  private fun BODY.joinOrWithdrawFromClass(user: User,
                                           defaultClassCode: ClassCode,
                                           redis: Jedis) {
    val enrolledClass = user.fetchEnrolledClassCode(redis)
    if (enrolledClass.isTeacherMode) {
      h3 { +"Enrolled class" }
      val classDesc = enrolledClass.fetchClassDesc(redis, true)
      div(classes = INDENT_2EM) {
        p { +"Currently enrolled in class $enrolledClass [$classDesc]." }
        p {
          form {
            action = USER_PREFS_POST_ENDPOINT
            method = FormMethod.post
            onSubmit = "return confirm('Are you sure you want to withdraw from class $classDesc [$enrolledClass]?');"
            input { type = submit; name = USER_PREFS_ACTION; value = WITHDRAW_FROM_CLASS }
          }
        }
      }
    }
    else {
      h3 { +"Join a class" }
      div(classes = INDENT_2EM) {
        p { +"Enter the class code your teacher gave you. This will make your progress visible to your teacher." }
        form {
          action = USER_PREFS_POST_ENDPOINT
          method = FormMethod.post
          table {
            tr {
              td { style = LABEL_WIDTH; label { +"Class Code" } }
              td {
                input {
                  type = InputType.text
                  size = "42"
                  name = CLASS_CODE_NAME
                  value = defaultClassCode.value
                  onKeyPress = "click$joinClassButton(event);"
                }
              }
            }
            tr {
              td {}
              td { input { type = submit; id = joinClassButton; name = USER_PREFS_ACTION; value = JOIN_CLASS } }
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
          action = USER_PREFS_POST_ENDPOINT
          method = FormMethod.post
          onSubmit = "return confirm('Are you sure you want to permanently delete the account for $email ?');"
          input { type = submit; name = USER_PREFS_ACTION; value = DELETE_ACCOUNT }
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
          val returnPath = queryParam(RETURN_PATH, "/")

          helpAndLogin(null, returnPath, false, redis)

          bodyTitle()

          p { span { style = "color:${if (msg.isError) "red" else "green"};"; this@body.displayMessage(msg) } }

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