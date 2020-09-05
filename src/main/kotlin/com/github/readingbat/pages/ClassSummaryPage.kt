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
import com.github.readingbat.common.ClassCode
import com.github.readingbat.common.Constants.CLASS_CODE_QP
import com.github.readingbat.common.Endpoints.CLASS_SUMMARY_ENDPOINT
import com.github.readingbat.common.Endpoints.TEACHER_PREFS_POST_ENDPOINT
import com.github.readingbat.common.FormFields.CLASSES_CHOICE_PARAM
import com.github.readingbat.common.FormFields.CLASS_CODE_NAME_PARAM
import com.github.readingbat.common.FormFields.DELETE_CLASS
import com.github.readingbat.common.FormFields.DISABLED_MODE
import com.github.readingbat.common.FormFields.RETURN_PARAM
import com.github.readingbat.common.FormFields.UPDATE_ACTIVE_CLASS
import com.github.readingbat.common.FormFields.USER_PREFS_ACTION_PARAM
import com.github.readingbat.common.Message
import com.github.readingbat.common.Message.Companion.EMPTY_MESSAGE
import com.github.readingbat.common.User
import com.github.readingbat.common.User.Companion.fetchActiveClassCode
import com.github.readingbat.common.isNotValidUser
import com.github.readingbat.dsl.InvalidRequestException
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.pages.ChallengePage.headerColor
import com.github.readingbat.pages.HelpAndLogin.helpAndLogin
import com.github.readingbat.pages.PageUtils.backLink
import com.github.readingbat.pages.PageUtils.bodyTitle
import com.github.readingbat.pages.PageUtils.clickButtonScript
import com.github.readingbat.pages.PageUtils.headDefault
import com.github.readingbat.pages.PageUtils.rawHtml
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.ServerUtils.queryParam
import io.ktor.application.*
import kotlinx.html.*
import kotlinx.html.Entities.nbsp
import kotlinx.html.InputType.radio
import kotlinx.html.InputType.submit
import kotlinx.html.stream.createHTML
import mu.KLogging
import redis.clients.jedis.Jedis

internal object ClassSummaryPage : KLogging() {

  private const val createClassButton = "CreateClassButton"

  fun PipelineCall.classSummaryPage(
    content: ReadingBatContent,
    user: User?,
    redis: Jedis,
    msg: Message = EMPTY_MESSAGE,
    defaultClassDesc: String = "",
                                   ): String {

    val classCode =
      call.parameters[CLASS_CODE_QP]?.let { ClassCode(it) } ?: throw InvalidRequestException("Missing class code")

    when {
      classCode.isNotValid(redis) -> throw InvalidRequestException("Invalid classCode $classCode")
      user.isNotValidUser(redis) -> throw InvalidRequestException("Invalid user")
      classCode.fetchClassTeacherId(redis) != user.id -> {
        val teacherId = classCode.fetchClassTeacherId(redis)
        throw InvalidRequestException("User id ${user.id} does not match classCode teacher Id $teacherId")
      }
      else -> {
      }
    }

    return createHTML()
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

          h2 { +"ReadingBat Class Summary" }

          displayStudents(user, activeClassCode, redis)

          backLink(returnPath)
        }
      }
  }

  private fun BODY.displayStudents(user: User, classCode: ClassCode, redis: Jedis) {
    val classDesc = classCode.fetchClassDesc(redis, true)
    val enrollees = classCode.fetchEnrollees(redis)
    h3 {
      style = "margin-left: 5px; color: $headerColor"
      +"$classDesc [$classCode]"
    }

    div(classes = INDENT_2EM) {
      table {
        style = "border-spacing: 15px 5px"
        enrollees
          .forEach { student ->
            tr {
              td { a { style = "text-decoration:underline"; href = "./"; +student.name(redis) } }
              td { a { style = "text-decoration:underline"; href = "./"; +student.email(redis).toString() } }
            }
          }
      }
    }
  }

  private fun BODY.classList(activeClassCode: ClassCode, classCodes: List<ClassCode>, redis: Jedis) {
    table {
      style = "border-spacing: 15px 5px"
      tr { th { +"Active" }; th { +"Class Code" }; th { +"Description" }; th { +"Enrollees" } }
      form {
        action = TEACHER_PREFS_POST_ENDPOINT
        method = FormMethod.post

        classCodes.forEach { code ->
          val classDesc = code.fetchClassDesc(redis)
          val enrolleeCount = code.fetchEnrollees(redis).count()
          this@table.tr {
            td {
              style = "text-align:center"
              input { type = radio; name = CLASSES_CHOICE_PARAM; value = code.value; checked = activeClassCode == code }
            }
            td {
              code.displayedValue
                .also {
                  if (enrolleeCount == 0)
                    +it
                  else
                    a { href = "$CLASS_SUMMARY_ENDPOINT?$CLASS_CODE_QP=${code.displayedValue}"; +it }
                }
            }
            td { +classDesc }
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
        val classDesc = classCode.fetchClassDesc(redis, true)
        tr {
          td {
            form {
              style = "margin:0"
              action = TEACHER_PREFS_POST_ENDPOINT
              method = FormMethod.post
              onSubmit =
                "return confirm('Are you sure you want to delete class $classDesc [$classCode]?')"
              input { type = InputType.hidden; name = CLASS_CODE_NAME_PARAM; value = classCode.displayedValue }
              input {
                style = "vertical-align:middle; margin-top:1; margin-bottom:0"
                type = submit; name = USER_PREFS_ACTION_PARAM; value =
                DELETE_CLASS
              }
            }
          }
        }
      }
    }
  }
}