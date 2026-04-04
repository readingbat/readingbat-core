/*
 * Copyright © 2026 Paul Ambrose (pambrose@mac.com)
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

package com.readingbat.pages

import com.pambrose.common.util.pathOf
import com.readingbat.common.ClassCode
import com.readingbat.common.ClassCodeRepository.fetchClassTeacherId
import com.readingbat.common.ClassCodeRepository.isNotValid
import com.readingbat.common.ClassCodeRepository.toDisplayString
import com.readingbat.common.Constants.CLASS_CODE_QP
import com.readingbat.common.Constants.CORRECT_COLOR
import com.readingbat.common.Constants.INCOMPLETE_COLOR
import com.readingbat.common.Constants.LANG_TYPE_QP
import com.readingbat.common.Constants.NO
import com.readingbat.common.Constants.USER_ID_QP
import com.readingbat.common.Constants.WRONG_COLOR
import com.readingbat.common.Constants.YES
import com.readingbat.common.Endpoints.CHALLENGE_ROOT
import com.readingbat.common.Endpoints.STUDENT_SUMMARY_ENDPOINT
import com.readingbat.common.Endpoints.TEACHER_PREFS_ENDPOINT
import com.readingbat.common.Endpoints.WS_ROOT
import com.readingbat.common.Endpoints.classSummaryEndpoint
import com.readingbat.common.FormFields.PREFS_ACTION_PARAM
import com.readingbat.common.FormFields.REMOVE_FROM_CLASS
import com.readingbat.common.FormFields.RETURN_PARAM
import com.readingbat.common.FormFields.USER_ID_PARAM
import com.readingbat.common.TwClasses
import com.readingbat.common.User
import com.readingbat.common.User.Companion.queryActiveTeachingClassCode
import com.readingbat.common.User.Companion.toUser
import com.readingbat.common.WsProtocol
import com.readingbat.common.isNotValidUser
import com.readingbat.dsl.InvalidRequestException
import com.readingbat.dsl.ReadingBatContent
import com.readingbat.pages.ChallengePage.HEADER_COLOR
import com.readingbat.pages.ClassSummaryPage.LIKE_DISLIKE
import com.readingbat.pages.ClassSummaryPage.STATS
import com.readingbat.pages.HelpAndLogin.helpAndLogin
import com.readingbat.pages.PageUtils.backLink
import com.readingbat.pages.PageUtils.bodyTitle
import com.readingbat.pages.PageUtils.encodeUriElems
import com.readingbat.pages.PageUtils.headDefault
import com.readingbat.pages.PageUtils.loadPingdomScript
import com.readingbat.pages.PageUtils.rawHtml
import com.readingbat.server.LanguageName
import com.readingbat.server.ServerUtils.queryParam
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.routing.RoutingContext
import kotlinx.html.BODY
import kotlinx.html.FormMethod.post
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.head
import kotlinx.html.hiddenInput
import kotlinx.html.html
import kotlinx.html.id
import kotlinx.html.onSubmit
import kotlinx.html.script
import kotlinx.html.stream.createHTML
import kotlinx.html.style
import kotlinx.html.submitInput
import kotlinx.html.table
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.tr

/**
 * Generates the individual student summary page at `/studentsummary`.
 *
 * Shows a teacher-facing overview of a single student's progress across all challenge groups
 * in a language, with per-invocation colored cells updated in real time via WebSockets.
 */
internal object StudentSummaryPage {
  private val logger = KotlinLogging.logger {}

  fun RoutingContext.studentSummaryPage(content: ReadingBatContent, user: User?): String {
    val p = call.parameters
    val languageName = p[LANG_TYPE_QP]?.let { LanguageName(it) } ?: throw InvalidRequestException("Missing language")
    val student = p[USER_ID_QP]?.toUser() ?: throw InvalidRequestException("Missing user id")
    val classCode = p[CLASS_CODE_QP]?.let { ClassCode(it) } ?: throw InvalidRequestException("Missing class code")
    val activeTeachingClassCode = queryActiveTeachingClassCode(user)

    when {
      classCode.isNotValid() -> {
        throw InvalidRequestException("Invalid class code: $classCode")
      }

      user.isNotValidUser() -> {
        throw InvalidRequestException("Invalid user")
      }

      // classCode != activeClassCode -> throw InvalidRequestException("Class code mismatch")
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

          h3(classes = "ml-[15px] text-rb-header") {
            style = "margin-left:15px; color: $HEADER_COLOR"
            a(classes = TwClasses.UNDERLINE) {
              href = pathOf(CHALLENGE_ROOT, languageName)
              +languageName.toLanguageType().toString()
            }
          }

          h3(classes = "ml-[15px] text-rb-header") {
            style = "margin-left:15px; color: $HEADER_COLOR"
            a(classes = TwClasses.UNDERLINE) {
              href = classSummaryEndpoint(classCode)
              +classCode.toDisplayString()
            }
          }

          h3(classes = "ml-[15px] text-rb-header") {
            style = "margin-left:15px; color: $HEADER_COLOR"
            +"Student: $studentName ${student.email} "
          }

          div(classes = "ml-[15px] mb-2.5") {
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
    form(classes = "m-0") {
      action = TEACHER_PREFS_ENDPOINT
      method = post
      onSubmit = "return confirm('Are you sure you want to remove $studentName from the class?')"
      hiddenInput {
        name = USER_ID_PARAM
        value = student.userId
      }
      submitInput(classes = "align-middle mt-px mb-0 rounded-lg text-xs") {
        name = PREFS_ACTION_PARAM
        value = REMOVE_FROM_CLASS
      }
    }
  }

  private fun BODY.displayChallengeGroups(
    content: ReadingBatContent,
    @Suppress("UNUSED_PARAMETER") classCode: ClassCode,
    languageName: LanguageName,
  ) =
    div(classes = TwClasses.INDENT_2EM) {
      table(classes = TwClasses.INVOC_TABLE) {
        content.findLanguage(languageName).challengeGroups
          .forEach { group ->
            tr {
              td {
                a(classes = TwClasses.UNDERLINE) {
                  href = pathOf(CHALLENGE_ROOT, languageName, group.groupName)
                  +group.groupName.toString()
                }
              }

              td {
                table(classes = TwClasses.INVOC_TABLE) {
                  tr {
                    // th { }
                    group.challenges
                      .forEach { challenge ->
                        th {
                          a(classes = TwClasses.UNDERLINE) {
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
                                  td(classes = TwClasses.INVOC_TD) {
                                    id = "$encodedGroup-$encodedName-$i"
                                    +""
                                  }
                                }
                              td(classes = TwClasses.INVOC_STAT) {
                                id = "$encodedGroup-$encodedName$STATS"
                                +""
                              }
                              td {
                                id = "$encodedGroup-$encodedName$LIKE_DISLIKE"
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
            var results = obj["${WsProtocol.RESULTS_FIELD}"]
            var i;
            for (i = 0; i < results.length; i++) {
              var prefix = obj["${WsProtocol.GROUP_NAME_FIELD}"] + '-' + obj["${WsProtocol.CHALLENGE_NAME_FIELD}"]
              var answers = document.getElementById(prefix + '-' + i)
              answers.style.backgroundColor = results[i] == '$YES' ? '$CORRECT_COLOR'
                                                                    : (results[i] == '$NO' ? '$WRONG_COLOR'
                                                                                             : '$INCOMPLETE_COLOR');
              document.getElementById(prefix + '$STATS').innerText = obj["${WsProtocol.STATS_FIELD}"];
              document.getElementById(prefix + '$LIKE_DISLIKE').innerHTML = obj["${WsProtocol.LIKE_DISLIKE_FIELD}"];
   }
          };
        """.trimIndent(),
      )
    }
  }
}
