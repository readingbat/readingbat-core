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
import com.github.readingbat.misc.*
import com.github.readingbat.misc.CSSNames.INDENT_2EM
import com.github.readingbat.misc.Constants.LABEL_WIDTH
import com.github.readingbat.misc.Constants.RETURN_PATH
import com.github.readingbat.misc.Endpoints.TEACHER_PREFS_ENDPOINT
import com.github.readingbat.misc.FormFields.CLASSES_CHOICE
import com.github.readingbat.misc.FormFields.CLASSES_DISABLED
import com.github.readingbat.misc.FormFields.CLASS_CODE
import com.github.readingbat.misc.FormFields.CREATE_CLASS
import com.github.readingbat.misc.FormFields.DELETE_CLASS
import com.github.readingbat.misc.FormFields.UPDATE_ACTIVE_CLASS
import com.github.readingbat.misc.FormFields.USER_PREFS_ACTION
import com.github.readingbat.misc.Message.Companion.EMPTY_MESSAGE
import com.github.readingbat.misc.User.Companion.fetchActiveClassCode
import com.github.readingbat.pages.HelpAndLogin.helpAndLogin
import com.github.readingbat.pages.PageCommon.backLink
import com.github.readingbat.pages.PageCommon.bodyTitle
import com.github.readingbat.pages.PageCommon.clickButtonScript
import com.github.readingbat.pages.PageCommon.displayMessage
import com.github.readingbat.pages.PageCommon.headDefault
import com.github.readingbat.pages.PageCommon.privacyStatement
import com.github.readingbat.pages.PageCommon.rawHtml
import com.github.readingbat.pages.UserPrefsPage.requestLogInPage
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.ServerUtils.queryParam
import kotlinx.html.*
import kotlinx.html.InputType.radio
import kotlinx.html.InputType.submit
import kotlinx.html.stream.createHTML
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
        head {
          headDefault(content)
          clickButtonScript(createClassButton)
        }

        body {
          val returnPath = queryParam(RETURN_PATH, "/")

          helpAndLogin(user, returnPath, redis)
          bodyTitle()

          h2 { +"ReadingBat User Preferences" }

          p { span { style = "color:${if (msg.isError) "red" else "green"};"; this@body.displayMessage(msg) } }

          createClass(defaultClassDesc)
          displayClasses(user, redis)

          privacyStatement(TEACHER_PREFS_ENDPOINT, returnPath)

          backLink(returnPath)
        }
      }

  private fun BODY.createClass(defaultClassDesc: String) {
    h3 { +"Create a class" }
    div(classes = INDENT_2EM) {
      p { +"Enter a decription of the class." }
      form {
        action = TEACHER_PREFS_ENDPOINT
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

  private fun BODY.displayClasses(user: User, redis: Jedis) {
    val classCodes = redis.smembers(user.userClassesKey).map { ClassCode(it) }
    if (classCodes.isNotEmpty()) {
      val activeClassCode = user.fetchActiveClassCode(redis)
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
        action = TEACHER_PREFS_ENDPOINT
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
            td { +classCode.value }
            td { +classDesc }
            td { style = "text-align:center;"; +enrolleeCount.toString() }
          }
        }
        this@table.tr {
          td {
            style = "text-align:center;"
            input {
              type = radio; name = CLASSES_CHOICE; value = CLASSES_DISABLED; checked = activeClassCode.isStudentMode
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
      tr { th { rawHtml(Entities.nbsp.text) } }
      classCodes.forEach { classCode ->
        val classDesc = classCode.fetchClassDesc(redis)
        tr {
          td {
            form {
              style = "margin:0;"
              action = TEACHER_PREFS_ENDPOINT
              method = FormMethod.post
              onSubmit = "return confirm('Are you sure you want to delete class $classDesc [$classCode]?');"
              input { type = InputType.hidden; name = CLASS_CODE; value = classCode.value }
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