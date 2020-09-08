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
import com.github.readingbat.common.Endpoints.classSummaryEndpoint
import com.github.readingbat.common.Endpoints.studentSummaryEndpoint
import com.github.readingbat.common.FormFields.RETURN_PARAM
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
    val isGroupNameValid = groupName.isDefined(content, languageName)
    val activeClassCode = user.fetchActiveClassCode(redis)
    val enrollees = classCode.fetchEnrollees(redis)

    when {
      classCode.isNotValid(redis) -> throw InvalidRequestException("Invalid classCode $classCode")
      user.isNotValidUser(redis) -> throw InvalidRequestException("Invalid user")
      classCode != activeClassCode -> throw InvalidRequestException("Class code mismatch")
      classCode.fetchClassTeacherId(redis) != user.id -> {
        val teacherId = classCode.fetchClassTeacherId(redis)
        throw InvalidRequestException("User id ${user.id} does not match classCode teacher Id $teacherId")
      }
      else -> {
      }
    }

    return createHTML()
      .html {

        head {
          headDefault(content)
          loadBootstrap()
        }

        body {
          val returnPath = queryParam(RETURN_PARAM, "/")
          helpAndLogin(user, returnPath, activeClassCode.isEnabled, redis)
          bodyTitle()

          h2 { +"ReadingBat Class Summary" }

          displayClassChoices(content, classCode, redis)

          h3 {
            style = "margin-left: 15px; color: $headerColor"
            +classCode.toDisplayString(redis)
          }

          if (isGroupNameValid) {
            h3 {
              style = "margin-left: 15px; color: $headerColor"
              +" "
              a(classes = UNDERLINE) {
                href = pathOf(CHALLENGE_ROOT, languageName)
                +languageName.toLanguageType().toString()
              }
              span { style = "padding-left:2px; padding-right:2px"; rawHtml("&rarr;") }
              a(classes = UNDERLINE) {
                href = pathOf(CHALLENGE_ROOT, languageName, groupName)
                +groupName.toString()
              }
            }
          }

          displayStudents(content, enrollees, classCode, isGroupNameValid, languageName, groupName, redis)

          if (enrollees.isNotEmpty() && languageName.isValid() && groupName.isValid())
            enableWebSockets(languageName, groupName, classCode)

          homeLink(if (languageName.isValid()) pathOf(CHALLENGE_ROOT, languageName) else "")
        }
      }
  }

  private fun BODY.displayClassChoices(content: ReadingBatContent, classCode: ClassCode, redis: Jedis) {
    table {
      style = "border-collapse: separate; border-spacing: 15px 5px" // 5px is vertical
      tr {
        td { style = "font-size:140%"; +"Challenge Groups: " }
        LanguageType.values()
          .map { content.findLanguage(it) }
          .forEach { langGroup ->
            if (langGroup.challengeGroups.isNotEmpty()) {
              td {
                ul {
                  style = "padding-left:0; margin-bottom:0; list-style-type:none"
                  dropdown {
                    val lang = langGroup.languageName.toLanguageType().name
                    dropdownToggle { +lang }
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

  private fun BODY.displayStudents(content: ReadingBatContent,
                                   enrollees: List<User>,
                                   classCode: ClassCode,
                                   isValidGroupName: Boolean,
                                   languageName: LanguageName,
                                   groupName: GroupName,
                                   redis: Jedis) =
    div(classes = INDENT_2EM) {
      table {
        style = "border-collapse: separate; border-spacing: 15px 5px"

        tr {
          th { +"Name" }
          th { +"Email" }
          if (isValidGroupName) {
            content.findLanguage(languageName).findGroup(groupName.value).challenges
              .forEach { challenge ->
                th {
                  a(classes = UNDERLINE) {
                    href = pathOf(CHALLENGE_ROOT, languageName, groupName, challenge.challengeName)
                    +challenge.challengeName.toString()
                  }
                }
              }
          }
        }

        enrollees
          .forEach { student ->
            tr {
              if (isValidGroupName) {
                val return_param = classSummaryEndpoint(classCode, languageName, groupName)
                "${studentSummaryEndpoint(classCode, languageName, student)}&$RETURN_PARAM=${return_param.encode()}"
                  .also {
                    td { a(classes = UNDERLINE) { href = it; +student.name(redis) } }
                    td { a(classes = UNDERLINE) { href = it; +student.email(redis).toString() } }
                  }
              }
              else {
                td { +student.name(redis) }
                td { +student.email(redis).toString() }
              }

              if (isValidGroupName) {
                content.findGroup(languageName, groupName).challenges
                  .forEach { challenge ->
                    td {
                      table {
                        tr {
                          challenge.functionInfo(content).invocations
                            .forEachIndexed { i, invocation ->
                              td(classes = INVOC_TD) {
                                id = "${student.id}-${challenge.challengeName.encode()}-$i"
                                +""
                              }
                            }
                          td(classes = INVOC_STAT) {
                            id = "${student.id}-${challenge.challengeName.encode()}$STATS"
                            +""
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
      style = "font-size:140%; text-decoration:none"
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