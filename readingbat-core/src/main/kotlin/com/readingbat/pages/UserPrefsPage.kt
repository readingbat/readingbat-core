/*
 * Copyright © 2026 Paul Ambrose (pambrose@mac.com)
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

package com.readingbat.pages

import com.readingbat.common.ClassCode
import com.readingbat.common.ClassCode.Companion.DISABLED_CLASS_CODE
import com.readingbat.common.ClassCodeRepository.toDisplayString
import com.readingbat.common.Constants.LABEL_WIDTH
import com.readingbat.common.Endpoints.OAUTH_LOGIN_ENDPOINT
import com.readingbat.common.Endpoints.TEACHER_PREFS_ENDPOINT
import com.readingbat.common.Endpoints.USER_PREFS_ENDPOINT
import com.readingbat.common.FormFields.CLASS_CODE_NAME_PARAM
import com.readingbat.common.FormFields.DEFAULT_LANGUAGE_CHOICE_PARAM
import com.readingbat.common.FormFields.DELETE_ACCOUNT
import com.readingbat.common.FormFields.JOIN_A_CLASS
import com.readingbat.common.FormFields.JOIN_CLASS
import com.readingbat.common.FormFields.PREFS_ACTION_PARAM
import com.readingbat.common.FormFields.RETURN_PARAM
import com.readingbat.common.FormFields.UPDATE_DEFAULT_LANGUAGE
import com.readingbat.common.FormFields.WITHDRAW_FROM_CLASS
import com.readingbat.common.Message
import com.readingbat.common.Message.Companion.EMPTY_MESSAGE
import com.readingbat.common.TwClasses
import com.readingbat.common.User
import com.readingbat.common.User.Companion.queryActiveTeachingClassCode
import com.readingbat.common.isValidUser
import com.readingbat.dsl.LanguageType
import com.readingbat.dsl.ReadingBatContent
import com.readingbat.pages.HelpAndLogin.helpAndLogin
import com.readingbat.pages.PageUtils.backLink
import com.readingbat.pages.PageUtils.bodyTitle
import com.readingbat.pages.PageUtils.clickButtonScript
import com.readingbat.pages.PageUtils.displayMessage
import com.readingbat.pages.PageUtils.headDefault
import com.readingbat.pages.PageUtils.loadPingdomScript
import com.readingbat.pages.PageUtils.privacyPolicy
import com.readingbat.pages.PageUtils.rawHtml
import com.readingbat.server.ServerUtils.queryParam
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.routing.RoutingContext
import kotlinx.html.BODY
import kotlinx.html.Entities.nbsp
import kotlinx.html.FormMethod
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.id
import kotlinx.html.label
import kotlinx.html.onKeyPress
import kotlinx.html.onSubmit
import kotlinx.html.p
import kotlinx.html.radioInput
import kotlinx.html.span
import kotlinx.html.stream.createHTML
import kotlinx.html.style
import kotlinx.html.submitInput
import kotlinx.html.table
import kotlinx.html.td
import kotlinx.html.textInput
import kotlinx.html.tr

/**
 * Generates the user preferences page at `/userprefs`.
 *
 * Allows users to set their default language, join or withdraw from a class,
 * delete their account, and navigate to teacher preferences. Also provides
 * the "please log in" fallback page for unauthenticated users.
 */
internal object UserPrefsPage {
  private val logger = KotlinLogging.logger {}
  private const val JOIN_CLASS_BUTTON = "JoinClassButton"

  fun RoutingContext.userPrefsPage(
    content: ReadingBatContent,
    user: User?,
    msg: Message = EMPTY_MESSAGE,
    defaultClassCode: ClassCode = DISABLED_CLASS_CODE,
  ) =
    if (user.isValidUser())
      userPrefsWithLoginPage(content, user, msg, defaultClassCode)
    else
      requestLogInPage(content)

  private fun RoutingContext.userPrefsWithLoginPage(
    content: ReadingBatContent,
    user: User,
    msg: Message,
    defaultClassCode: ClassCode,
  ) =
    createHTML()
      .html {
        head {
          headDefault()
          clickButtonScript(JOIN_CLASS_BUTTON)
        }

        body {
          val returnPath = queryParam(RETURN_PARAM, "/")
          val activeTeachingClassCode = queryActiveTeachingClassCode(user)

          helpAndLogin(content, user, returnPath, activeTeachingClassCode.isEnabled)
          bodyTitle()

          h2 { +"User Preferences" }

          if (msg.isAssigned())
            p {
              span {
                style = "color:${msg.color}"
                this@body.displayMessage(msg)
              }
            }

          defaultLanguage(user)
          joinOrWithdrawFromClass(user, defaultClassCode)
          deleteAccount(user)

          p(classes = TwClasses.INDENT_1EM) {
            a {
              href = "$TEACHER_PREFS_ENDPOINT?$RETURN_PARAM=$USER_PREFS_ENDPOINT"
              +"Teacher Preferences"
            }
          }

          backLink(returnPath)
        }
      }

  private fun BODY.defaultLanguage(user: User) {
    h3 { +"Default Language" }
    div(classes = TwClasses.INDENT_2EM) {
      table {
        form {
          action = USER_PREFS_ENDPOINT
          method = FormMethod.post

          this@table.tr {
            LanguageType.entries
              .forEach { languageType ->
                td(classes = "text-center") {
                  radioInput {
                    id = languageType.languageName.value
                    name = DEFAULT_LANGUAGE_CHOICE_PARAM
                    value = languageType.languageName.value
                    checked = user.defaultLanguage == languageType
                  }
                  label {
                    htmlFor = languageType.languageName.value
                    +" ${languageType.name} "
                  }
                }
                td { rawHtml(nbsp.text) }
                td { rawHtml(nbsp.text) }
              }
            td {}
            td {
              submitInput {
                style = "font-size:12px"
                name = PREFS_ACTION_PARAM
                value = UPDATE_DEFAULT_LANGUAGE
              }
            }
          }
        }
      }
    }
  }

  private fun BODY.joinOrWithdrawFromClass(user: User, defaultClassCode: ClassCode) {
    val enrolledClass = user.enrolledClassCode
    if (enrolledClass.isEnabled) {
      h3 { +"Enrolled class" }
      val displayStr = enrolledClass.toDisplayString()
      div(classes = TwClasses.INDENT_2EM) {
        p { +"Currently enrolled in class $displayStr." }
        p {
          form {
            action = USER_PREFS_ENDPOINT
            method = FormMethod.post
            onSubmit = "return confirm('Are you sure you want to withdraw from class $displayStr?')"
            submitInput {
              style = "font-size:12px"
              name = PREFS_ACTION_PARAM
              value = WITHDRAW_FROM_CLASS
            }
          }
        }
      }
    } else {
      h3 { +JOIN_A_CLASS }
      div(classes = TwClasses.INDENT_2EM) {
        p { +"Enter the class code your teacher gave you. This will make your progress visible to your teacher." }
        form {
          action = USER_PREFS_ENDPOINT
          method = FormMethod.post
          table {
            tr {
              td {
                style = LABEL_WIDTH
                label { +"Class Code" }
              }
              td {
                textInput {
                  style = "font-size:12px; padding:4px; border-radius:4px"
                  size = "42"
                  name = CLASS_CODE_NAME_PARAM
                  value = defaultClassCode.displayedValue
                  onKeyPress = "click$JOIN_CLASS_BUTTON(event)"
                }
              }
            }
            tr {
              td {}
              td {
                submitInput {
                  style = "font-size:12px; margin-top:2px"
                  id = JOIN_CLASS_BUTTON
                  name = PREFS_ACTION_PARAM
                  value = JOIN_CLASS
                }
              }
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
              td { textInput { size = "42"; name = "pdt"; value = "" } }
            }
            tr {
              td {}
              td { submitInput { style = "font-size:12px"; name = USER_PREFS_ACTION; value = "Share" } }
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
        hiddenInput { name = "date"; value = "963892736" }
        table {
          tr {
            td { style = LABEL_WIDTH; label { +"Memo" } }
            td { textInput { size = "42"; name = "real"; value = "" } }
          }
          tr {
            td {}
            td { submitInput { style = "font-size:12px"; name = USER_PREFS_ACTION; value = "Update Memo" } }
          }
        }
      }
    }
    */

  private fun BODY.deleteAccount(user: User) {
    val email = user.email
    if (email.isNotBlank()) {
      h3 { +"Delete account" }
      div(classes = TwClasses.INDENT_2EM) {
        p { +"Permanently delete account [$email] -- this cannot be undone!" }
        form {
          action = USER_PREFS_ENDPOINT
          method = FormMethod.post
          onSubmit = "return confirm('Are you sure you want to permanently delete the account for $email ?')"
          submitInput {
            style = "font-size:12px"
            name = PREFS_ACTION_PARAM
            value = DELETE_ACCOUNT
          }
        }
      }
    }
  }

  fun RoutingContext.requestLogInPage(content: ReadingBatContent, msg: Message = EMPTY_MESSAGE) =
    createHTML()
      .html {
        head { headDefault() }

        body {
          val returnPath = queryParam(RETURN_PARAM, "/")

          helpAndLogin(content, null, returnPath, false)
          bodyTitle()

          if (msg.isAssigned())
            p {
              span {
                style = "color:${msg.color}"
                this@body.displayMessage(msg)
              }
            }

          h2 { +"Log in" }

          p {
            +"Please"
            a {
              href = OAUTH_LOGIN_ENDPOINT
              +" log in "
            }
            +"to edit preferences."
          }

          privacyPolicy(USER_PREFS_ENDPOINT)
          backLink(returnPath)
          loadPingdomScript()
        }
      }
}
