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

import com.github.readingbat.common.CSSNames.INDENT_2EM
import com.github.readingbat.common.CSSNames.UNDERLINE
import com.github.readingbat.common.ClassCode
import com.github.readingbat.common.Constants.LABEL_WIDTH
import com.github.readingbat.common.Endpoints.TEACHER_PREFS_ENDPOINT
import com.github.readingbat.common.Endpoints.TEACHER_PREFS_POST_ENDPOINT
import com.github.readingbat.common.Endpoints.classSummaryEndpoint
import com.github.readingbat.common.FormFields.CLASSES_CHOICE_PARAM
import com.github.readingbat.common.FormFields.CLASS_CODE_NAME_PARAM
import com.github.readingbat.common.FormFields.CLASS_DESC_PARAM
import com.github.readingbat.common.FormFields.CREATE_CLASS
import com.github.readingbat.common.FormFields.DELETE_CLASS
import com.github.readingbat.common.FormFields.DISABLED_MODE
import com.github.readingbat.common.FormFields.RETURN_PARAM
import com.github.readingbat.common.FormFields.UPDATE_ACTIVE_CLASS
import com.github.readingbat.common.FormFields.USER_PREFS_ACTION_PARAM
import com.github.readingbat.common.Message
import com.github.readingbat.common.Message.Companion.EMPTY_MESSAGE
import com.github.readingbat.common.User
import com.github.readingbat.common.User.Companion.fetchActiveClassCode
import com.github.readingbat.common.isValidUser
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.pages.HelpAndLogin.helpAndLogin
import com.github.readingbat.pages.PageUtils.bodyTitle
import com.github.readingbat.pages.PageUtils.clickButtonScript
import com.github.readingbat.pages.PageUtils.displayMessage
import com.github.readingbat.pages.PageUtils.headDefault
import com.github.readingbat.pages.PageUtils.homeLink
import com.github.readingbat.pages.PageUtils.privacyStatement
import com.github.readingbat.pages.PageUtils.rawHtml
import com.github.readingbat.pages.UserPrefsPage.requestLogInPage
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.ServerUtils.queryParam
import kotlinx.html.*
import kotlinx.html.Entities.nbsp
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
        val activeClassCode = user.fetchActiveClassCode(redis)

        head {
          headDefault(content)
          clickButtonScript(createClassButton)
        }

        body {
          val returnPath = queryParam(RETURN_PARAM, "/")

          helpAndLogin(user, returnPath, activeClassCode.isEnabled, redis)
          bodyTitle()

          h2 { +"ReadingBat Teacher Preferences" }

          p { span { style = "color:${msg.color}"; this@body.displayMessage(msg) } }

          createClass(defaultClassDesc)

          displayClasses(user, activeClassCode, redis)

          privacyStatement(TEACHER_PREFS_ENDPOINT, TEACHER_PREFS_ENDPOINT)

          homeLink()
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
                name = CLASS_DESC_PARAM
                value = defaultClassDesc
                onKeyPress = "click${createClassButton}(event)"
              }
            }
          }
          tr {
            td {}
            td { input { type = submit; id = createClassButton; name = USER_PREFS_ACTION_PARAM; value = CREATE_CLASS } }
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
            td { style = "vertical-align:top"; this@displayClasses.classList(activeClassCode, classCodes, redis) }
            td { style = "vertical-align:top"; this@displayClasses.deleteClassButtons(classCodes, redis) }
          }
        }
      }
    }
  }

  private fun BODY.classList(activeClassCode: ClassCode, classCodes: List<ClassCode>, redis: Jedis) {
    table {
      style = "border-spacing: 15px 5px"
      tr { th { +"Active" }; th { +"Description" }; th { +"Class Code" }; th { +"Enrollees" } }
      form {
        action = TEACHER_PREFS_POST_ENDPOINT
        method = FormMethod.post

        classCodes
          .forEach { classCode ->
            val enrolleeCount = classCode.fetchEnrollees(redis).count()
            this@table.tr {
              td {
                style = "text-align:center"
                input {
                  type = radio
                  name = CLASSES_CHOICE_PARAM
                  value = classCode.value
                  checked = activeClassCode == classCode
                }
              }

              val summary = classSummaryEndpoint(classCode)
              td { a(classes = UNDERLINE) { href = summary; +classCode.fetchClassDesc(redis) } }
              td { a(classes = UNDERLINE) { href = summary; +classCode.displayedValue } }
              td { style = "text-align:center"; +enrolleeCount.toString() }
            }
          }

        this@table.tr {
          td {
            style = "text-align:center"
            input {
              type = radio; name = CLASSES_CHOICE_PARAM; value = DISABLED_MODE; checked = activeClassCode.isNotEnabled
            }
          }
          td { colSpan = "3"; +"Student mode" }
        }

        this@table.tr {
          td {}
          td { input { type = submit; name = USER_PREFS_ACTION_PARAM; value = UPDATE_ACTIVE_CLASS } }
        }
      }
    }
  }

  private fun BODY.deleteClassButtons(classCodes: List<ClassCode>, redis: Jedis) {
    table {
      style = "border-spacing: 5px 5px"
      tr { th { rawHtml(nbsp.text) } }
      classCodes.forEach { classCode ->
        tr {
          td {
            form {
              style = "margin:0"
              action = TEACHER_PREFS_POST_ENDPOINT
              method = FormMethod.post
              onSubmit = "return confirm('Are you sure you want to delete class ${classCode.toDisplayString(redis)}?')"
              input { type = InputType.hidden; name = CLASS_CODE_NAME_PARAM; value = classCode.displayedValue }
              input {
                style = "vertical-align:middle; margin-top:1; margin-bottom:0"
                type = submit
                name = USER_PREFS_ACTION_PARAM
                value = DELETE_CLASS
              }
            }
          }
        }
      }
    }
  }
}