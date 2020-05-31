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
import com.github.readingbat.misc.Constants.LABEL_WIDTH
import com.github.readingbat.misc.Constants.RETURN_PATH
import com.github.readingbat.misc.Endpoints.CREATE_ACCOUNT_ENDPOINT
import com.github.readingbat.misc.Endpoints.USER_PREFS_ENDPOINT
import com.github.readingbat.misc.FormFields.CLASSES_CHOICE
import com.github.readingbat.misc.FormFields.CLASSES_DISABLED
import com.github.readingbat.misc.FormFields.CLASS_CODE
import com.github.readingbat.misc.FormFields.CLASS_DESC
import com.github.readingbat.misc.FormFields.CONFIRM_PASSWORD
import com.github.readingbat.misc.FormFields.CREATE_CLASS
import com.github.readingbat.misc.FormFields.CURR_PASSWORD
import com.github.readingbat.misc.FormFields.DELETE_ACCOUNT
import com.github.readingbat.misc.FormFields.DELETE_CLASS
import com.github.readingbat.misc.FormFields.JOIN_CLASS
import com.github.readingbat.misc.FormFields.NEW_PASSWORD
import com.github.readingbat.misc.FormFields.UPDATE_ACTIVE_CLASS
import com.github.readingbat.misc.FormFields.UPDATE_PASSWORD
import com.github.readingbat.misc.FormFields.USER_PREFS_ACTION
import com.github.readingbat.misc.FormFields.WITHDRAW_FROM_CLASS
import com.github.readingbat.misc.PageUtils.hideShowButton
import com.github.readingbat.misc.UserId
import com.github.readingbat.misc.UserId.Companion.classCodeEnrollmentKey
import com.github.readingbat.misc.UserId.Companion.classDescKey
import com.github.readingbat.misc.UserId.Companion.isValidPrincipal
import com.github.readingbat.misc.UserPrincipal
import com.github.readingbat.pages.HelpAndLogin.helpAndLogin
import com.github.readingbat.pages.PageCommon.backLink
import com.github.readingbat.pages.PageCommon.bodyTitle
import com.github.readingbat.pages.PageCommon.clickButtonScript
import com.github.readingbat.pages.PageCommon.displayMessage
import com.github.readingbat.pages.PageCommon.headDefault
import com.github.readingbat.pages.PageCommon.privacyStatement
import com.github.readingbat.pages.PageCommon.rawHtml
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.ServerUtils.fetchPrincipal
import com.github.readingbat.server.ServerUtils.queryParam
import kotlinx.html.*
import kotlinx.html.Entities.nbsp
import kotlinx.html.InputType.radio
import kotlinx.html.InputType.submit
import kotlinx.html.stream.createHTML
import mu.KLogging
import redis.clients.jedis.Jedis

internal object UserPrefsPage : KLogging() {

  private const val divStyle = "margin-left: 2em; margin-bottom: 2em;"
  private const val formName = "pform"
  private const val passwordButton = "UpdatePasswordButton"
  private const val joinClassButton = "JoinClassButton"
  private const val createClassButton = "CreateClassButton"
  private const val deleteClassButton = "DeleteClassButton"


  fun PipelineCall.userPrefsPage(content: ReadingBatContent,
                                 redis: Jedis,
                                 msg: String,
                                 isErrorMsg: Boolean,
                                 defaultClassCode: String = ""): String {
    val principal = fetchPrincipal()
    return if (principal != null && isValidPrincipal(principal, redis))
      prefsWithLoginPage(content, redis, principal, msg, isErrorMsg, defaultClassCode)
    else
      requestLogInPage(content, redis)
  }

  private fun PipelineCall.prefsWithLoginPage(content: ReadingBatContent,
                                              redis: Jedis,
                                              principal: UserPrincipal,
                                              msg: String,
                                              isErrorMsg: Boolean,
                                              defaultClassCode: String) =
    createHTML()
      .html {
        head {
          headDefault(content)
          clickButtonScript(passwordButton, joinClassButton, createClassButton, deleteClassButton)
        }

        body {
          bodyTitle()

          h2 { +"ReadingBat Prefs" }

          p { span { style = "color:${if (isErrorMsg) "red" else "green"};"; this@body.displayMessage(msg) } }

          val returnPath = queryParam(RETURN_PATH) ?: "/"

          changePassword()
          joinOrWithdrawFromClass(redis, principal, defaultClassCode)
          createClass()
          displayClasses(redis, principal)
          deleteClass(redis, principal)
          //teacherShare()
          //memo()
          deleteAccount(redis, principal)
          privacyStatement(USER_PREFS_ENDPOINT, returnPath)
          backLink(returnPath)
        }
      }

  private fun BODY.changePassword() {
    h3 { +"Change password" }
    div {
      style = divStyle
      p { +"Password must contain at least 6 characters" }
      form {
        name = formName
        action = USER_PREFS_ENDPOINT
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
            td {
              input {
                type = submit; id = passwordButton; name = USER_PREFS_ACTION; value = UPDATE_PASSWORD
              }
            }
          }
        }
      }
    }
  }

  private fun BODY.joinOrWithdrawFromClass(redis: Jedis, principal: UserPrincipal, defaultClassCode: String) {
    val userId = UserId(principal.userId)
    val enrolledClass = userId.fetchEnrolledClassCode(redis)
    logger.info { "Enrolled in $enrolledClass" }
    if (enrolledClass.isNotEmpty()) {
      h3 { +"Enrolled class" }
      val classDesc = redis[classDescKey(enrolledClass)] ?: "Missing Description"
      div {
        style = divStyle
        p { +"Currently enrolled in class $enrolledClass [$classDesc]." }
        p {
          form {
            action = USER_PREFS_ENDPOINT
            method = FormMethod.post
            onSubmit = "return confirm('Are you sure you want to withdraw from class $enrolledClass [$classDesc]?');"
            input { type = submit; name = USER_PREFS_ACTION; value = WITHDRAW_FROM_CLASS }
          }
        }
      }
    }
    else {
      h3 { +"Join a class" }
      div {
        style = divStyle
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
                  name = CLASS_CODE
                  value = defaultClassCode
                  onKeyPress = "click$joinClassButton(event);"
                }
              }
            }
            tr {
              td {}
              td {
                input {
                  type = submit; id = joinClassButton; name = USER_PREFS_ACTION; value = JOIN_CLASS
                }
              }
            }
          }
        }
      }
    }
  }

  private fun BODY.createClass() {
    h3 { +"Create a class" }
    div {
      style = divStyle
      p { +"Enter a decription of the class." }
      form {
        action = USER_PREFS_ENDPOINT
        method = FormMethod.post
        table {
          tr {
            td { style = LABEL_WIDTH; label { +"Class Description" } }
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
                type = submit; id = createClassButton; name = USER_PREFS_ACTION; value = CREATE_CLASS
              }
            }
          }
        }
      }
    }
  }

  private fun BODY.displayClasses(redis: Jedis, principal: UserPrincipal) {
    val userId = UserId(principal.userId)
    val ids = redis.smembers(userId.userClassesKey)

    if (ids.size > 0) {
      val activeClassCode = userId.fetchActiveClassCode(redis)
      h3 { +"Current classes" }
      div {
        style = divStyle

        table {
          tr {
            td { this@displayClasses.classList(activeClassCode, ids, redis) }
            td { this@displayClasses.deleteClassButtons(ids, redis) }
          }
        }
      }
    }
  }

  private fun BODY.classList(activeClassCode: String, ids: Set<String>, redis: Jedis) {
    table {
      style = "border-spacing: 15px 5px;"
      tr { th { +"Active" }; th { +"Class Code" }; th { +"Description" }; th { +"Enrollees" } }
      form {
        action = USER_PREFS_ENDPOINT
        method = FormMethod.post
        ids.forEach { classCode ->
          val classDesc = redis[classDescKey(classCode)] ?: "Missing Description"
          val enrolleeCount =
            redis.smembers(classCodeEnrollmentKey(classCode)).filter { it.isNotEmpty() }.count()
          this@table.tr {
            style = "height:1.5em"
            td {
              style = "text-align:center;"
              input {
                type = radio; name = CLASSES_CHOICE; value = classCode; checked = activeClassCode == classCode
              }
            }
            td { +classCode }
            td { +classDesc }
            td { style = "text-align:center;"; +enrolleeCount.toString() }
          }
        }
        this@table.tr {
          style = "height:1.5em"
          td {
            style = "text-align:center;";
            input {
              type = radio; name = CLASSES_CHOICE; value = CLASSES_DISABLED; checked = activeClassCode.isEmpty()
            }
          }
          td { colSpan = "4"; +"Disable active class" }
        }
        this@table.tr {
          td {}
          td { input { type = submit; name = USER_PREFS_ACTION; value = UPDATE_ACTIVE_CLASS } }
        }
      }
    }
  }

  private fun BODY.deleteClassButtons(ids: Set<String>, redis: Jedis) {
    table {
      style = "border-spacing: 0px 0px;"
      tr { th { rawHtml(nbsp.text) } }
      ids.forEach { classCode ->
        val classDesc = redis[classDescKey(classCode)] ?: "Missing Description"
        tr {
          style = "height:1.5em"
          td {
            style = "vertical-align:center"
            form {
              action = USER_PREFS_ENDPOINT
              method = FormMethod.post
              onSubmit =
                "return confirm('Are you sure you want to delete class $classCode [$classDesc]?');"
              input { type = InputType.hidden; name = CLASS_CODE; value = classCode }
              input { type = submit; name = USER_PREFS_ACTION; value = DELETE_CLASS }
            }
          }
        }
      }
      tr { td { rawHtml(nbsp.text) } }
      tr { td { rawHtml(nbsp.text) } }
    }
  }

  private fun BODY.deleteClass(redis: Jedis, principal: UserPrincipal) {
    val userId = UserId(principal.userId)
    val ids = redis.smembers(userId.userClassesKey)
    if (ids.size > 0) {
      h3 { +"Delete a class" }
      div {
        style = divStyle
        p { +"Enter the class code you wish to delete." }
        form {
          action = USER_PREFS_ENDPOINT
          method = FormMethod.post
          onSubmit = "return confirm('Are you sure you want to permanently delete this class code ?');"
          table {
            tr {
              td { style = LABEL_WIDTH; label { +"Class code" } }
              td {
                input {
                  type = InputType.text
                  size = "42"
                  name = CLASS_CODE
                  value = ""
                  onKeyPress = "click$deleteClassButton(event);"
                }
              }
            }
            tr {
              td {}
              td {
                input {
                  type = submit; id = deleteClassButton; name = USER_PREFS_ACTION; value = DELETE_CLASS
                }
              }
            }
          }
        }
      }
    }
  }

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

  private fun BODY.deleteAccount(redis: Jedis, principal: UserPrincipal) {
    val email = principal.email(redis)
    if (email.isNotEmpty()) {
      h3 { +"Delete account" }
      div {
        style = divStyle
        p { +"Permanently delete account [$email] -- this cannot be undone!" }
        form {
          action = USER_PREFS_ENDPOINT
          method = FormMethod.post
          onSubmit = "return confirm('Are you sure you want to permanently delete the account for $email ?');"
          input { type = submit; name = USER_PREFS_ACTION; value = DELETE_ACCOUNT }
        }
      }
    }
  }

  fun PipelineCall.requestLogInPage(content: ReadingBatContent,
                                    redis: Jedis,
                                    isErrorMsg: Boolean = false,
                                    msg: String = "") =
    createHTML()
      .html {
        head { headDefault(content) }

        body {
          val returnPath = queryParam(RETURN_PATH) ?: "/"

          helpAndLogin(redis, fetchPrincipal(), returnPath)

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