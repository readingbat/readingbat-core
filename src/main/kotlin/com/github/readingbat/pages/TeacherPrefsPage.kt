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
import com.github.readingbat.misc.Constants
import com.github.readingbat.misc.Constants.RETURN_PATH
import com.github.readingbat.misc.Endpoints.TEACHER_PREFS_ENDPOINT
import com.github.readingbat.misc.FormFields
import com.github.readingbat.misc.FormFields.CLASSES_CHOICE
import com.github.readingbat.misc.FormFields.CLASSES_DISABLED
import com.github.readingbat.misc.FormFields.CLASS_CODE
import com.github.readingbat.misc.FormFields.DELETE_CLASS
import com.github.readingbat.misc.FormFields.UPDATE_ACTIVE_CLASS
import com.github.readingbat.misc.FormFields.USER_PREFS_ACTION
import com.github.readingbat.misc.UserId
import com.github.readingbat.misc.UserPrincipal
import com.github.readingbat.pages.HelpAndLogin.helpAndLogin
import com.github.readingbat.pages.PageCommon.backLink
import com.github.readingbat.pages.PageCommon.bodyTitle
import com.github.readingbat.pages.PageCommon.clickButtonScript
import com.github.readingbat.pages.PageCommon.displayMessage
import com.github.readingbat.pages.PageCommon.headDefault
import com.github.readingbat.pages.PageCommon.privacyStatement
import com.github.readingbat.pages.PageCommon.rawHtml
import com.github.readingbat.pages.UserPrefsPage.fetchClassDesc
import com.github.readingbat.pages.UserPrefsPage.requestLogInPage
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.ServerUtils.fetchPrincipal
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
                                    redis: Jedis,
                                    msg: String,
                                    isErrorMsg: Boolean,
                                    defaultClassDesc: String = ""): String {
    val principal = fetchPrincipal()
    return if (principal != null && com.github.readingbat.misc.UserId.isValidPrincipal(principal, redis))
      teacherPrefsWithLoginPage(content, redis, principal, msg, isErrorMsg, defaultClassDesc)
    else
      requestLogInPage(content, redis)
  }

  private fun PipelineCall.teacherPrefsWithLoginPage(content: ReadingBatContent,
                                                     redis: Jedis,
                                                     principal: UserPrincipal,
                                                     msg: String,
                                                     isErrorMsg: Boolean,
                                                     defaultClassDesc: String) =
    createHTML()
      .html {
        head {
          headDefault(content)
          clickButtonScript(createClassButton)
        }

        body {
          val returnPath = queryParam(RETURN_PATH) ?: "/"

          helpAndLogin(redis, fetchPrincipal(), returnPath)

          bodyTitle()

          h2 { +"ReadingBat User Preferences" }

          p { span { style = "color:${if (isErrorMsg) "red" else "green"};"; this@body.displayMessage(msg) } }

          createClass(defaultClassDesc)
          displayClasses(redis, principal)

          privacyStatement(TEACHER_PREFS_ENDPOINT, returnPath)

          backLink(returnPath)
        }
      }

  private fun BODY.createClass(defaultClassDesc: String) {
    h3 { +"Create a class" }
    div {
      style = UserPrefsPage.divStyle
      p { +"Enter a decription of the class." }
      form {
        action = TEACHER_PREFS_ENDPOINT
        method = FormMethod.post
        table {
          tr {
            td { style = Constants.LABEL_WIDTH; label { +"Class Description" } }
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
            td {
              input {
                type = submit; id = createClassButton; name = USER_PREFS_ACTION; value =
                FormFields.CREATE_CLASS
              }
            }
          }
        }
      }
    }
  }

  private fun BODY.displayClasses(redis: Jedis, principal: UserPrincipal) {
    val userId = UserId(principal.userId)
    val classCodes = redis.smembers(userId.userClassesKey)

    if (classCodes.size > 0) {
      val activeClassCode = userId.fetchActiveClassCode(redis)
      h3 { +"Classes" }
      div {
        style = UserPrefsPage.divStyle

        table {
          //style = "border-spacing: 5px 0px;"
          tr {
            td { style = "vertical-align:top;"; this@displayClasses.classList(activeClassCode, classCodes, redis) }
            td { style = "vertical-align:top;"; this@displayClasses.deleteClassButtons(classCodes, redis) }
          }
        }
      }
    }
  }

  private fun BODY.classList(activeClassCode: String, classCodes: Set<String>, redis: Jedis) {
    table {
      style = "border-spacing: 15px 5px;"
      tr { th { +"Active" }; th { +"Class Code" }; th { +"Description" }; th { +"Enrollees" } }
      form {
        action = TEACHER_PREFS_ENDPOINT
        method = FormMethod.post
        classCodes.forEach { classCode ->
          val classDesc = fetchClassDesc(classCode, redis)
          val enrolleeCount =
            redis.smembers(UserId.classCodeEnrollmentKey(classCode)).filter { it.isNotEmpty() }.count()
          this@table.tr {
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
          td {
            style = "text-align:center;";
            input {
              type = radio; name = CLASSES_CHOICE; value = CLASSES_DISABLED; checked = activeClassCode.isEmpty()
            }
          }
          td { colSpan = "3"; +"Disable active class" }
        }
        this@table.tr {
          td {}
          td {
            input {
              type = submit; name = USER_PREFS_ACTION; value =
              UPDATE_ACTIVE_CLASS
            }
          }
        }
      }
    }
  }

  private fun BODY.deleteClassButtons(classCodes: Set<String>, redis: Jedis) {
    table {
      style = "border-spacing: 5px 5px;"
      tr { th { rawHtml(Entities.nbsp.text) } }
      classCodes.forEach { classCode ->
        val classDesc = fetchClassDesc(classCode, redis)
        tr {
          td {
            form {
              style = "margin:0;"
              action = TEACHER_PREFS_ENDPOINT
              method = FormMethod.post
              onSubmit = "return confirm('Are you sure you want to delete class $classCode [$classDesc]?');"
              input { type = InputType.hidden; name = CLASS_CODE; value = classCode }
              input {
                style = "vertical-align:middle; margin-top:1; margin-bottom:0;";
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