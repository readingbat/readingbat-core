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
import com.github.readingbat.common.CSSNames.INVOC_STAT
import com.github.readingbat.common.CSSNames.INVOC_TABLE
import com.github.readingbat.common.CSSNames.INVOC_TD
import com.github.readingbat.common.CSSNames.UNDERLINE
import com.github.readingbat.common.ClassCode
import com.github.readingbat.common.CommonUtils.pathOf
import com.github.readingbat.common.Constants.CLASS_CODE_QP
import com.github.readingbat.common.Constants.CORRECT_COLOR
import com.github.readingbat.common.Constants.INCOMPLETE_COLOR
import com.github.readingbat.common.Constants.LANG_TYPE_QP
import com.github.readingbat.common.Constants.NO
import com.github.readingbat.common.Constants.USER_ID_QP
import com.github.readingbat.common.Constants.WRONG_COLOR
import com.github.readingbat.common.Constants.YES
import com.github.readingbat.common.Endpoints.CHALLENGE_ROOT
import com.github.readingbat.common.Endpoints.STUDENT_SUMMARY_ENDPOINT
import com.github.readingbat.common.Endpoints.classSummaryEndpoint
import com.github.readingbat.common.FormFields.RETURN_PARAM
import com.github.readingbat.common.User
import com.github.readingbat.common.User.Companion.fetchActiveClassCode
import com.github.readingbat.common.User.Companion.toUser
import com.github.readingbat.common.isNotValidUser
import com.github.readingbat.dsl.InvalidRequestException
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.pages.ChallengePage.headerColor
import com.github.readingbat.pages.ClassSummaryPage.STATS
import com.github.readingbat.pages.HelpAndLogin.helpAndLogin
import com.github.readingbat.pages.PageUtils.backLink
import com.github.readingbat.pages.PageUtils.bodyTitle
import com.github.readingbat.pages.PageUtils.encodeUriElems
import com.github.readingbat.pages.PageUtils.headDefault
import com.github.readingbat.pages.PageUtils.loadPingdomScript
import com.github.readingbat.pages.PageUtils.rawHtml
import com.github.readingbat.server.LanguageName
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.ServerUtils.queryParam
import io.ktor.application.*
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import mu.KLogging
import redis.clients.jedis.Jedis

internal object StudentSummaryPage : KLogging() {

  fun PipelineCall.studentSummaryPage(content: ReadingBatContent, user: User?, redis: Jedis): String {

    val (languageName, student, classCode) =
      Triple(
        call.parameters[LANG_TYPE_QP]?.let { LanguageName(it) } ?: throw InvalidRequestException("Missing language"),
        call.parameters[USER_ID_QP]?.toUser(null) ?: throw InvalidRequestException("Missing user id"),
        call.parameters[CLASS_CODE_QP]?.let { ClassCode(it) } ?: throw InvalidRequestException("Missing class code"))
    val activeClassCode = user.fetchActiveClassCode(redis)

    when {
      classCode.isNotValid(redis) -> throw InvalidRequestException("Invalid class code: $classCode")
      user.isNotValidUser(redis) -> throw InvalidRequestException("Invalid user")
      //classCode != activeClassCode -> throw InvalidRequestException("Class code mismatch")
      classCode.fetchClassTeacherId(redis) != user.id -> {
        val teacherId = classCode.fetchClassTeacherId(redis)
        throw InvalidRequestException("User id ${user.id} does not match class code's teacher Id $teacherId")
      }
      else -> {
      }
    }

    return createHTML()
      .html {
        head { headDefault(content) }

        body {
          val returnPath = queryParam(RETURN_PARAM, "/")
          helpAndLogin(content, user, returnPath, activeClassCode.isEnabled, redis)
          bodyTitle()

          h2 { +"Student Summary" }

          h3 {
            style = "margin-left: 15px; color: $headerColor"
            a(classes = UNDERLINE) {
              href = classSummaryEndpoint(classCode); +classCode.toDisplayString(redis)
            }
          }

          h3 {
            style = "margin-left: 15px; color: $headerColor"
            a(classes = UNDERLINE) {
              href = pathOf(CHALLENGE_ROOT, languageName); +languageName.toLanguageType().toString()
            }
          }

          h3 {
            style = "margin-left: 15px; color: $headerColor"
            +"Student: ${student.name(redis)} ${student.email(redis)}"
          }

          displayChallengeGroups(content, classCode, languageName, redis)
          enableWebSockets(languageName, student, classCode)
          backLink(returnPath)

          loadPingdomScript()
        }
      }
  }

  private fun BODY.displayChallengeGroups(content: ReadingBatContent,
                                          classCode: ClassCode,
                                          languageName: LanguageName,
                                          redis: Jedis) =
    div(classes = INDENT_2EM) {
      table(classes = INVOC_TABLE) {
        content.findLanguage(languageName).challengeGroups
          .forEach { group ->
            tr {
              td {
                a(classes = UNDERLINE) {
                  href = pathOf(CHALLENGE_ROOT, languageName, group.groupName)
                  +group.groupName.toString()
                }
              }

              td {
                table(classes = INVOC_TABLE) {
                  tr {
                    //th { }
                    group.challenges
                      .forEach { challenge ->
                        th {
                          a(classes = UNDERLINE) {
                            href = pathOf(CHALLENGE_ROOT, languageName, group.groupName, challenge.challengeName)
                            +challenge.challengeName.toString()
                          }
                        }
                      }
                  }

                  tr {
                    group.challenges
                      .forEach { challenge ->
                        td {
                          table {
                            tr {
                              challenge.functionInfo(content).invocations
                                .forEachIndexed { i, invocation ->
                                  td(classes = INVOC_TD) {
                                    id = "${group.groupName.encode()}-${challenge.challengeName.encode()}-$i"; +""
                                  }
                                }
                              td(classes = INVOC_STAT) {
                                id = "${group.groupName.encode()}-${challenge.challengeName.encode()}$STATS"; +""
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
    }

  private fun BODY.enableWebSockets(languageName: LanguageName, student: User, classCode: ClassCode) {
    script {
      rawHtml(
        """
          var wshost = location.origin;
          if (wshost.startsWith('https:'))
            wshost = wshost.replace(/^https:/, 'wss:');
          else
            wshost = wshost.replace(/^http:/, 'ws:');

          var wsurl = wshost + '$STUDENT_SUMMARY_ENDPOINT/' + ${encodeUriElems(languageName, student.id, classCode)};
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
              var prefix = obj.groupName + '-' + obj.challengeName
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
}