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

import com.github.pambrose.common.util.encode
import com.github.readingbat.common.CSSNames.BTN
import com.github.readingbat.common.CSSNames.INDENT_2EM
import com.github.readingbat.common.CSSNames.INVOC_STAT
import com.github.readingbat.common.CSSNames.INVOC_TD
import com.github.readingbat.common.CSSNames.UNDERLINE
import com.github.readingbat.common.ClassCode
import com.github.readingbat.common.CommonUtils.pathOf
import com.github.readingbat.common.Constants.CLASS_CODE_QP
import com.github.readingbat.common.Constants.CORRECT_COLOR
import com.github.readingbat.common.Constants.GROUP_NAME_QP
import com.github.readingbat.common.Constants.INCOMPLETE_COLOR
import com.github.readingbat.common.Constants.LANG_TYPE_QP
import com.github.readingbat.common.Constants.NO
import com.github.readingbat.common.Constants.WRONG_COLOR
import com.github.readingbat.common.Constants.YES
import com.github.readingbat.common.Endpoints.CHALLENGE_ROOT
import com.github.readingbat.common.Endpoints.CLASS_SUMMARY_ENDPOINT
import com.github.readingbat.common.Endpoints.TEACHER_PREFS_POST_ENDPOINT
import com.github.readingbat.common.Endpoints.classSummaryEndpoint
import com.github.readingbat.common.Endpoints.studentSummaryEndpoint
import com.github.readingbat.common.FormFields.CLASS_CODE_NAME_PARAM
import com.github.readingbat.common.FormFields.RETURN_PARAM
import com.github.readingbat.common.FormFields.UPDATE_ACTIVE_CLASS
import com.github.readingbat.common.FormFields.USER_PREFS_ACTION_PARAM
import com.github.readingbat.common.User
import com.github.readingbat.common.User.Companion.fetchActiveClassCode
import com.github.readingbat.common.isNotValidUser
import com.github.readingbat.dsl.InvalidRequestException
import com.github.readingbat.dsl.LanguageType
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.pages.ChallengePage.headerColor
import com.github.readingbat.pages.HelpAndLogin.helpAndLogin
import com.github.readingbat.pages.PageUtils.bodyTitle
import com.github.readingbat.pages.PageUtils.encodeUriElems
import com.github.readingbat.pages.PageUtils.headDefault
import com.github.readingbat.pages.PageUtils.homeLink
import com.github.readingbat.pages.PageUtils.loadBootstrap
import com.github.readingbat.pages.PageUtils.rawHtml
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

  internal const val STATS = "-stats"

  fun PipelineCall.classSummaryPage(content: ReadingBatContent, user: User?, redis: Jedis): String {

    val (languageName, groupName, classCode) =
      Triple(
        call.parameters[LANG_TYPE_QP]?.let { LanguageName(it) } ?: EMPTY_LANGUAGE,
        call.parameters[GROUP_NAME_QP]?.let { GroupName(it) } ?: EMPTY_GROUP,
        call.parameters[CLASS_CODE_QP]?.let { ClassCode(it) } ?: throw InvalidRequestException("Missing class code"))

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

        val hasGroup = groupName.isDefined(content, languageName)
        val activeClassCode = user.fetchActiveClassCode(redis)
        val enrollees = classCode.fetchEnrollees(redis)

        head {
          headDefault(content)
          loadBootstrap()
        }

        body {
          val returnPath = queryParam(RETURN_PARAM, "/")
          helpAndLogin(user, returnPath, activeClassCode.isEnabled, redis)
          bodyTitle()

          h2 { +"ReadingBat Class Summary" }

          table {
            tr {
              td {
                h3 {
                  style = "margin-left: 15px; color: $headerColor"
                  +classCode.toDisplayString(redis)
                }
              }
              if (classCode != activeClassCode) {
                td {
                  form {
                    style = "margin:0"
                    action = TEACHER_PREFS_POST_ENDPOINT
                    method = FormMethod.post
                    input { type = InputType.hidden; name = CLASS_CODE_NAME_PARAM; value = classCode.displayedValue }
                    input {
                      style = "vertical-align:middle; margin-left:10; margin-top:1; margin-bottom:0"
                      type = InputType.submit
                      name = USER_PREFS_ACTION_PARAM
                      value = UPDATE_ACTIVE_CLASS
                    }
                  }
                }
              }
            }
          }

          displayClassChoices(content, classCode, redis)

          if (hasGroup)
            displayGroupInfo(classCode, activeClassCode, languageName, groupName)

          displayStudents(content, enrollees, classCode, activeClassCode, hasGroup, languageName, groupName, redis)

          if (enrollees.isNotEmpty() && languageName.isValid() && groupName.isValid())
            enableWebSockets(languageName, groupName, classCode)

          homeLink(if (languageName.isValid()) pathOf(CHALLENGE_ROOT, languageName) else "")
        }
      }
  }

  private fun BODY.displayClassChoices(content: ReadingBatContent, classCode: ClassCode, redis: Jedis) {
    table {
      style = "border-collapse: separate; border-spacing: 15px 15px"
      tr {
        td { style = "font-size:140%"; +"Challenge Group: " }
        LanguageType.values()
          .map { content.findLanguage(it) }
          .forEach { langGroup ->
            if (langGroup.challengeGroups.isNotEmpty()) {
              td {
                ul {
                  style = "padding-left:0; margin-bottom:0; list-style-type:none"
                  dropdown {
                    val lang = langGroup.languageType.name
                    dropdownToggle {
                      classes = setOf(BTN)
                      +"$lang "
                    }
                    dropdownMenu {
                      dropdownHeader(lang)
                      //divider()
                      langGroup.challengeGroups
                        .forEach {
                          li {
                            style = "font-size:110%"
                            a(classSummaryEndpoint(classCode, langGroup.languageName, it.groupName))
                            { +it.groupName.toString() }
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

  private fun BODY.displayGroupInfo(classCode: ClassCode,
                                    activeClassCode: ClassCode,
                                    languageName: LanguageName,
                                    groupName: GroupName) {
    h3 {
      style = "margin-left: 15px; color: $headerColor"
      +" "

      languageName.toLanguageType().toString()
        .also {
          if (classCode == activeClassCode)
            a(classes = UNDERLINE) { href = pathOf(CHALLENGE_ROOT, languageName); +it }
          else
            +it
        }

      span { style = "padding-left:2px; padding-right:2px"; rawHtml("&rarr;") }

      groupName.toString()
        .also {
          if (classCode == activeClassCode)
            a(classes = UNDERLINE) { href = pathOf(CHALLENGE_ROOT, languageName, groupName); +it }
          else
            +it
        }
    }
  }

  private fun BODY.displayStudents(content: ReadingBatContent,
                                   enrollees: List<User>,
                                   classCode: ClassCode,
                                   activeClassCode: ClassCode,
                                   hasGroup: Boolean,
                                   languageName: LanguageName,
                                   groupName: GroupName,
                                   redis: Jedis) =
    div(classes = INDENT_2EM) {
      if (enrollees.isNotEmpty())
        table {
          style = "border-collapse: separate; border-spacing: 15px 5px"
          tr {
            th { +"Name" }
            th { +"Email" }
            if (hasGroup) {
              content.findLanguage(languageName).findGroup(groupName.value).challenges
                .forEach { challenge ->
                  th {
                    challenge.challengeName.toString()
                      .also {
                        if (classCode == activeClassCode)
                          a(classes = UNDERLINE) {
                            href = pathOf(CHALLENGE_ROOT, languageName, groupName, challenge.challengeName)
                            +it
                          }
                        else
                          +it
                      }
                  }
                }
            }
          }

          enrollees
            .forEach { student ->
              tr {
                if (hasGroup && classCode == activeClassCode) {
                  val returnUrl = classSummaryEndpoint(classCode, languageName, groupName)
                  "${studentSummaryEndpoint(classCode, languageName, student)}&$RETURN_PARAM=${returnUrl.encode()}"
                    .also {
                      td { a(classes = UNDERLINE) { href = it; +student.name(redis) } }
                      td { a(classes = UNDERLINE) { href = it; +student.email(redis).toString() } }
                    }
                }
                else {
                  td { +student.name(redis) }
                  td { +student.email(redis).toString() }
                }

                if (hasGroup) {
                  content.findGroup(languageName, groupName).challenges
                    .forEach { challenge ->
                      td {
                        table {
                          tr {
                            challenge.functionInfo(content).invocations
                              .forEachIndexed { i, invocation ->
                                td(classes = INVOC_TD) {
                                  id = "${student.id}-${challenge.challengeName.encode()}-$i"; +""
                                }
                              }
                            td(classes = INVOC_STAT) {
                              id = "${student.id}-${challenge.challengeName.encode()}$STATS"; +""
                            }
                          }
                        }
                      }
                    }
                }
              }
            }
        }
      else {
        h4 { style = "padding-left:15px"; +"No students enrolled." }
      }
    }

  private fun BODY.enableWebSockets(languageName: LanguageName, groupName: GroupName, classCode: ClassCode) {
    script {
      rawHtml(
        """
          var wshost = location.origin;
          if (wshost.startsWith('https:'))
            wshost = wshost.replace(/^https:/, 'wss:');
          else
            wshost = wshost.replace(/^http:/, 'ws:');

          var wsurl = wshost + '$CLASS_SUMMARY_ENDPOINT/' + ${encodeUriElems(languageName, groupName, classCode)};
          var ws = new WebSocket(wsurl);

          ws.onopen = function (event) {
            ws.send("$classCode"); 
          };
          
          ws.onmessage = function (event) {
            console.log(event.data);
            var obj = JSON.parse(event.data)
            var results = obj.results
            var i;
            for (i = 0; i < results.length; i++) {
              var prefix = obj.userId + '-' + obj.challengeName
              var answers = document.getElementById(prefix + '-' + i)
              answers.style.backgroundColor = obj.results[i] == '$YES' ? '$CORRECT_COLOR' 
                                                                    : (obj.results[i] == '$NO' ? '$WRONG_COLOR' 
                                                                                             : '$INCOMPLETE_COLOR');

              document.getElementById(prefix + '$STATS').innerHTML = obj.msg;
            }
          };
        """.trimIndent())
    }
  }

  // See: https://github.com/Kotlin/kotlinx.html/wiki/Micro-templating-and-DSL-customizing
  private fun UL.dropdown(block: LI.() -> Unit) {
    li("dropdown") { block() }
  }

  private fun LI.dropdownToggle(block: A.() -> Unit) {
    a("#", null, "dropdown-toggle") {
      style =
        "font-size:140%; text-decoration:none; border-radius: 5px; padding: 1px 7px; cursor: pointer; color: black; border:1px solid black;"
      attributes["data-toggle"] = "dropdown"
      role = "button"
      attributes["aria-expanded"] = "false"
      block()

      span { classes = setOf("caret") }
    }
  }

  private fun LI.dropdownMenu(block: UL.() -> Unit): Unit = ul("dropdown-menu") {
    role = "menu"
    block()
  }

  private fun UL.dropdownHeader(text: String) =
    li { style = "font-size:120%"; classes = setOf("dropdown-header"); +text }

  private fun UL.divider() = li { classes = setOf("divider") }
}