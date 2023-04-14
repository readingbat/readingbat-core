/*
 * Copyright © 2021 Paul Ambrose (pambrose@mac.com)
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

import com.github.pambrose.common.util.pathOf
import com.github.readingbat.common.ClassCode
import com.github.readingbat.common.Constants.CLASS_CODE_QP
import com.github.readingbat.common.Constants.CORRECT_COLOR
import com.github.readingbat.common.Constants.INCOMPLETE_COLOR
import com.github.readingbat.common.Constants.LANG_TYPE_QP
import com.github.readingbat.common.Constants.NO
import com.github.readingbat.common.Constants.USER_ID_QP
import com.github.readingbat.common.Constants.WRONG_COLOR
import com.github.readingbat.common.Constants.YES
import com.github.readingbat.common.CssNames.INDENT_2EM
import com.github.readingbat.common.CssNames.INVOC_STAT
import com.github.readingbat.common.CssNames.INVOC_TABLE
import com.github.readingbat.common.CssNames.INVOC_TD
import com.github.readingbat.common.CssNames.UNDERLINE
import com.github.readingbat.common.Endpoints.CHALLENGE_ROOT
import com.github.readingbat.common.Endpoints.STUDENT_SUMMARY_ENDPOINT
import com.github.readingbat.common.Endpoints.TEACHER_PREFS_ENDPOINT
import com.github.readingbat.common.Endpoints.WS_ROOT
import com.github.readingbat.common.Endpoints.classSummaryEndpoint
import com.github.readingbat.common.FormFields.PREFS_ACTION_PARAM
import com.github.readingbat.common.FormFields.REMOVE_FROM_CLASS
import com.github.readingbat.common.FormFields.RETURN_PARAM
import com.github.readingbat.common.FormFields.USER_ID_PARAM
import com.github.readingbat.common.User
import com.github.readingbat.common.User.Companion.queryActiveTeachingClassCode
import com.github.readingbat.common.User.Companion.toUser
import com.github.readingbat.common.isNotValidUser
import com.github.readingbat.dsl.InvalidRequestException
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.pages.ChallengePage.headerColor
import com.github.readingbat.pages.ClassSummaryPage.LIKE_DISLIKE
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
import io.ktor.server.application.*
import kotlinx.html.*
import kotlinx.html.FormMethod.post
import kotlinx.html.stream.createHTML
import mu.two.KLogging

internal object StudentSummaryPage : KLogging() {

  fun PipelineCall.studentSummaryPage(content: ReadingBatContent, user: User?): String {
    val p = call.parameters
    val languageName = p[LANG_TYPE_QP]?.let { LanguageName(it) } ?: throw InvalidRequestException("Missing language")
    val student = p[USER_ID_QP]?.toUser() ?: throw InvalidRequestException("Missing user id")
    val classCode = p[CLASS_CODE_QP]?.let { ClassCode(it) } ?: throw InvalidRequestException("Missing class code")
    val activeTeachingClassCode = queryActiveTeachingClassCode(user)

    when {
      classCode.isNotValid() -> throw InvalidRequestException("Invalid class code: $classCode")
      user.isNotValidUser() -> throw InvalidRequestException("Invalid user")
      //classCode != activeClassCode -> throw InvalidRequestException("Class code mismatch")
      classCode.fetchClassTeacherId() != user.userId -> {
        val teacherId = classCode.fetchClassTeacherId()
        throw InvalidRequestException("User id ${user.userId} does not match class code's teacher Id $teacherId")
      }

      else -> {
        // Do nothing
      }
    }

    return createHTML()
      .html {
        val studentName = student.fullName.value

        head { headDefault() }

        body {
          val returnPath = queryParam(RETURN_PARAM, "/")

          helpAndLogin(content, user, returnPath, activeTeachingClassCode.isEnabled)
          bodyTitle()

          h2 { +"Student Summary" }

          h3 {
            style = "margin-left:15px; color: $headerColor"
            a(classes = UNDERLINE) {
              href = pathOf(CHALLENGE_ROOT, languageName); +languageName.toLanguageType().toString()
            }
          }

          h3 {
            style = "margin-left:15px; color: $headerColor"
            a(classes = UNDERLINE) { href = classSummaryEndpoint(classCode); +classCode.toDisplayString() }
          }

          h3 {
            style = "margin-left:15px; color: $headerColor"
            +"Student: $studentName ${student.email} "
          }

          div {
            style = "margin-left:15px; margin-bottom:10px"
            this@body.removeFromClassButton(student, studentName)
          }

          displayChallengeGroups(content, classCode, languageName)
          enableWebSockets(languageName, student, classCode)
          backLink(returnPath)
          loadPingdomScript()
        }
      }
  }

  internal fun BODY.removeFromClassButton(student: User, studentName: String) {
    form {
      style = "margin:0"
      action = TEACHER_PREFS_ENDPOINT
      method = post
      onSubmit = "return confirm('Are you sure you want to remove $studentName from the class?')"
      hiddenInput { name = USER_ID_PARAM; value = student.userId }
      submitInput {
        style = "vertical-align:middle; margin-top:1; margin-bottom:0; border-radius: 8px; font-size:12px"
        name = PREFS_ACTION_PARAM
        value = REMOVE_FROM_CLASS
      }
    }
  }

  private fun BODY.displayChallengeGroups(
    content: ReadingBatContent,
    @Suppress("UNUSED_PARAMETER") classCode: ClassCode,
    languageName: LanguageName
  ) =
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
                            val encodedGroup = group.groupName.encode()
                            val encodedName = challenge.challengeName.encode()
                            tr {
                              challenge.functionInfo().invocations
                                .forEachIndexed { i, _ ->
                                  td(classes = INVOC_TD) {
                                    id = "$encodedGroup-$encodedName-$i"; +""
                                  }
                                }
                              td(classes = INVOC_STAT) {
                                id = "$encodedGroup-$encodedName$STATS"; +""
                              }
                              td {
                                id = "$encodedGroup-$encodedName$LIKE_DISLIKE"; +""
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

  private fun BODY.enableWebSockets(langName: LanguageName, student: User, classCode: ClassCode) {
    val studentId = student.userId
    script {
      rawHtml(
        """
          var wshost = location.origin;
          if (wshost.startsWith('https:'))
            wshost = wshost.replace(/^https:/, 'wss:');
          else
            wshost = wshost.replace(/^http:/, 'ws:');
      
          var wsurl = wshost + '$WS_ROOT$STUDENT_SUMMARY_ENDPOINT/' + ${encodeUriElems(langName, studentId, classCode)};
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
              document.getElementById(prefix + '$STATS').innerText = obj.stats;
              document.getElementById(prefix + '$LIKE_DISLIKE').innerHTML = obj.likeDislike;
   }
          };
        """.trimIndent()
      )
    }
  }
}