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
import com.github.readingbat.common.Constants.GROUP_NAME_QP
import com.github.readingbat.common.Constants.LANG_TYPE_QP
import com.github.readingbat.common.Endpoints.CLASS_SUMMARY_ENDPOINT
import com.github.readingbat.common.FormFields.RETURN_PARAM
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
import com.github.readingbat.pages.PageUtils.headDefault
import com.github.readingbat.server.GroupName
import com.github.readingbat.server.GroupName.Companion.EMPTY_GROUP
import com.github.readingbat.server.LanguageName
import com.github.readingbat.server.LanguageName.Companion.EMPTY_LANGUAGE
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.ServerUtils.queryParam
import io.ktor.application.*
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import mu.KLogging
import redis.clients.jedis.Jedis

internal object ClassSummaryPage : KLogging() {

  fun PipelineCall.classSummaryPage(content: ReadingBatContent,
                                    user: User?,
                                    redis: Jedis,
                                    msg: Message = EMPTY_MESSAGE,
                                    defaultClassDesc: String = ""): String {

    val classCode =
      call.parameters[CLASS_CODE_QP]?.let { ClassCode(it) } ?: throw InvalidRequestException("Missing class code")

    val langName = call.parameters[LANG_TYPE_QP]?.let { LanguageName(it) } ?: EMPTY_LANGUAGE
    val groupName = call.parameters[GROUP_NAME_QP]?.let { GroupName(it) } ?: EMPTY_GROUP

    logger.info { "Lang = $langName" }
    logger.info { "Group = $groupName" }

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

          link { rel = "stylesheet"; href = "https://maxcdn.bootstrapcdn.com/bootstrap/3.4.1/css/bootstrap.min.css" }
          script { src = "https://ajax.googleapis.com/ajax/libs/jquery/3.5.1/jquery.min.js" }
          script { src = "https://maxcdn.bootstrapcdn.com/bootstrap/3.4.1/js/bootstrap.min.js" }
        }

        body {
          val returnPath = queryParam(RETURN_PARAM, "/")
          helpAndLogin(user, returnPath, activeClassCode.isEnabled, redis)
          bodyTitle()

          h2 { +"ReadingBat Class Summary" }

          h3 { style = "margin-left: 5px; color: $headerColor"; +classCode.toDisplayString(redis) }

          displayClassChoices(content, classCode, redis)

          displayStudents(user, classCode, redis)

          backLink(returnPath)
        }
      }
  }

  private fun BODY.displayClassChoices(content: ReadingBatContent, classCode: ClassCode, redis: Jedis) {
    table {
      style = "border-collapse: separate; border-spacing: 15px"
      tr {
        td { style = "font-size:120%"; +"Challenge Groups: " }
        listOf(content.java, content.python, content.kotlin)
          .forEach { langGroup ->
            if (langGroup.challengeGroups.isNotEmpty()) {
              td {
                ul {
                  style =
                    "padding-left:0; margin-bottom:0; text-align:center; vertical-align:middle; list-style-type:none"
                  dropdown {
                    val lang = langGroup.languageName.toLanguageType().name
                    dropdownToggle { +lang }
                    dropdownMenu {
                      dropdownHeader(lang)
                      //divider()
                      langGroup.challengeGroups
                        .forEach {
                          li {
                            a("$CLASS_SUMMARY_ENDPOINT?$CLASS_CODE_QP=$classCode&$LANG_TYPE_QP=${langGroup.languageName.value}&$GROUP_NAME_QP=${it.groupName.value}")
                            { +it.groupName.value }
                          }
                        }
                    }
                  }
                }
              }
            }
          }
      }
    }
  }

  private fun BODY.displayStudents(user: User, classCode: ClassCode, redis: Jedis) {
    val enrollees = classCode.fetchEnrollees(redis)

    div(classes = INDENT_2EM) {
      table {
        style = "border-collapse: separate; border-spacing: 15px 5px"
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

  fun UL.dropdown(block: LI.() -> Unit) {
    li("dropdown") { block() }
  }

  fun LI.dropdownToggle(block: A.() -> Unit) {
    a("#", null, "dropdown-toggle") {
      style = "font-size:120%"
      attributes["data-toggle"] = "dropdown"
      role = "button"
      attributes["aria-expanded"] = "false"
      block()

      span { classes = setOf("caret") }
    }
  }

  fun LI.dropdownMenu(block: UL.() -> Unit): Unit = ul("dropdown-menu") {
    role = "menu"
    block()
  }

  fun UL.dropdownHeader(text: String): Unit =
    li { style = "font-size:120%"; classes = setOf("dropdown-header"); +text }

  fun UL.divider(): Unit = li { classes = setOf("divider") }

}