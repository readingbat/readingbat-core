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
import com.github.readingbat.dsl.isProduction
import com.github.readingbat.misc.CSSNames.INDENT_2EM
import com.github.readingbat.misc.ClassCode
import com.github.readingbat.misc.Constants.ADMIN_USERS
import com.github.readingbat.misc.Constants.LABEL_WIDTH
import com.github.readingbat.misc.Constants.RETURN_PATH
import com.github.readingbat.misc.Endpoints.RESET_CONTENT_ENDPOINT
import com.github.readingbat.misc.Endpoints.RESET_MAPS_ENDPOINT
import com.github.readingbat.misc.Endpoints.TEACHER_PREFS_ENDPOINT
import com.github.readingbat.misc.Endpoints.TEACHER_PREFS_POST_ENDPOINT
import com.github.readingbat.misc.FormFields
import com.github.readingbat.misc.FormFields.CLASSES_CHOICE
import com.github.readingbat.misc.FormFields.CLASS_CODE_NAME
import com.github.readingbat.misc.FormFields.CREATE_CLASS
import com.github.readingbat.misc.FormFields.DELETE_CLASS
import com.github.readingbat.misc.FormFields.DISABLED_MODE
import com.github.readingbat.misc.FormFields.UPDATE_ACTIVE_CLASS
import com.github.readingbat.misc.FormFields.USER_PREFS_ACTION
import com.github.readingbat.misc.Message
import com.github.readingbat.misc.Message.Companion.EMPTY_MESSAGE
import com.github.readingbat.misc.User
import com.github.readingbat.misc.User.Companion.fetchActiveClassCode
import com.github.readingbat.misc.isValidUser
import com.github.readingbat.pages.HelpAndLogin.helpAndLogin
import com.github.readingbat.pages.PageCommon.backLink
import com.github.readingbat.pages.PageCommon.bodyTitle
import com.github.readingbat.pages.PageCommon.button
import com.github.readingbat.pages.PageCommon.clickButtonScript
import com.github.readingbat.pages.PageCommon.displayMessage
import com.github.readingbat.pages.PageCommon.headDefault
import com.github.readingbat.pages.PageCommon.privacyStatement
import com.github.readingbat.pages.PageCommon.rawHtml
import com.github.readingbat.pages.UserPrefsPage.requestLogInPage
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.ServerUtils.queryParam
import kotlinx.html.BODY
import kotlinx.html.Entities.nbsp
import kotlinx.html.FormMethod
import kotlinx.html.InputType
import kotlinx.html.InputType.radio
import kotlinx.html.InputType.submit
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.onKeyPress
import kotlinx.html.onSubmit
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.stream.createHTML
import kotlinx.html.style
import kotlinx.html.table
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.tr
import mu.KLogging
import redis.clients.jedis.Jedis

internal object TeacherPrefsPage : KLogging() {

  private const val createClassButton = "CreateClassButton"

  fun PipelineCall.teacherPrefsPage(content: ReadingBatContent,
                                    user: User?,
                                    redis: Jedis,
                                    msg: Message = EMPTY_MESSAGE,
                                    defaultClassDesc: String = "") =
    if (user.isValidUser(redis))
      teacherPrefsWithLoginPage(content, user, msg, defaultClassDesc, redis)
    else
      requestLogInPage(content, redis)

  private fun PipelineCall.teacherPrefsWithLoginPage(content: ReadingBatContent,
                                                     user: User,
                                                     msg: Message,
                                                     defaultClassDesc: String,
                                                     redis: Jedis) =
    createHTML()
      .html {
        val activeClassCode = user.fetchActiveClassCode(redis)

        head {
          headDefault(content)
          clickButtonScript(createClassButton)
        }

        body {
          val returnPath = queryParam(RETURN_PATH, "/")

          helpAndLogin(user, returnPath, activeClassCode.isEnabled, redis)
          bodyTitle()

          h2 { +"ReadingBat User Preferences" }

          p { span { style = "color:${if (msg.isError) "red" else "green"};"; this@body.displayMessage(msg) } }

          createClass(defaultClassDesc)
          displayClasses(user, activeClassCode, redis)

          if (!isProduction() || user.email(redis).value in ADMIN_USERS) {
            h2 { +"ReadingBat System Admin" }

            p {
              this@body.button("Reset ReadingBat Content",
                               RESET_CONTENT_ENDPOINT,
                               "Are you sure you want to reset the content? (This can take a while)")
            }

            p {
              this@body.button("Reset ReadingBat Maps",
                               RESET_MAPS_ENDPOINT,
                               "Are you sure you want to reset the maps?")
            }
          }

          privacyStatement(TEACHER_PREFS_ENDPOINT, returnPath)

          backLink(returnPath)
        }
      }

  private fun BODY.createClass(defaultClassDesc: String) {
    h3 { +"Create a class" }
    div(classes = INDENT_2EM) {
      p { +"Enter a decription of the class." }
      form {
        action = TEACHER_PREFS_POST_ENDPOINT
        method = FormMethod.post
        table {
          tr {
            td { style = LABEL_WIDTH; label { +"Class Description" } }
            td {
              input {
                type = InputType.text
                size = "42"
                name = FormFields.CLASS_DESC
                value = defaultClassDesc
                onKeyPress = "click${createClassButton}(event);"
              }
            }
          }
          tr {
            td {}
            td { input { type = submit; id = createClassButton; name = USER_PREFS_ACTION; value = CREATE_CLASS } }
          }
        }
      }
    }
  }

  private fun BODY.displayClasses(user: User, activeClassCode: ClassCode, redis: Jedis) {
    val classCodes = user.classCodes(redis)
    if (classCodes.isNotEmpty()) {
      h3 { +"Classes" }
      div(classes = INDENT_2EM) {
        table {
          tr {
            td { style = "vertical-align:top;"; this@displayClasses.classList(activeClassCode, classCodes, redis) }
            td { style = "vertical-align:top;"; this@displayClasses.deleteClassButtons(classCodes, redis) }
          }
        }
      }
    }
  }

  private fun BODY.classList(activeClassCode: ClassCode, classCodes: List<ClassCode>, redis: Jedis) {
    table {
      style = "border-spacing: 15px 5px;"
      tr { th { +"Active" }; th { +"Class Code" }; th { +"Description" }; th { +"Enrollees" } }
      form {
        action = TEACHER_PREFS_POST_ENDPOINT
        method = FormMethod.post
        classCodes.forEach { classCode ->
          val classDesc = classCode.fetchClassDesc(redis)
          val enrolleeCount = classCode.fetchEnrollees(redis).count()
          this@table.tr {
            td {
              style = "text-align:center;"
              input {
                type = radio
                name = CLASSES_CHOICE
                value = classCode.value
                checked = activeClassCode == classCode
              }
            }
            td { +classCode.displayedValue }
            td { +classDesc }
            td { style = "text-align:center;"; +enrolleeCount.toString() }
          }
        }
        this@table.tr {
          td {
            style = "text-align:center;"
            input {
              type = radio; name = CLASSES_CHOICE; value = DISABLED_MODE; checked = activeClassCode.isNotEnabled
            }
          }
          td { colSpan = "3"; +"Student mode" }
        }
        this@table.tr {
          td {}
          td { input { type = submit; name = USER_PREFS_ACTION; value = UPDATE_ACTIVE_CLASS } }
        }
      }
    }
  }

  private fun BODY.deleteClassButtons(classCodes: List<ClassCode>, redis: Jedis) {
    table {
      style = "border-spacing: 5px 5px;"
      tr { th { rawHtml(nbsp.text) } }
      classCodes.forEach { classCode ->
        val classDesc = classCode.fetchClassDesc(redis, true)
        tr {
          td {
            form {
              style = "margin:0;"
              action = TEACHER_PREFS_POST_ENDPOINT
              method = FormMethod.post
              onSubmit =
                "return confirm('Are you sure you want to delete class $classDesc [$classCode]?');"
              input { type = InputType.hidden; name = CLASS_CODE_NAME; value = classCode.displayedValue }
              input {
                style = "vertical-align:middle; margin-top:1; margin-bottom:0;"
                type = submit; name = USER_PREFS_ACTION; value =
                DELETE_CLASS
              }
            }
          }
        }
      }
    }
  }
}