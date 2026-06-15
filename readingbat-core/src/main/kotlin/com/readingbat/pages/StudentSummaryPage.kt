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
import com.readingbat.common.Constants.LANG_TYPE_QP
import com.readingbat.common.Constants.USER_ID_QP
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
import com.readingbat.dsl.challenge.Challenge
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
import org.apache.commons.text.StringEscapeUtils.escapeEcmaScript

/**
 * Generates the individual student summary page at `/studentsummary`.
 *
 * Shows a teacher-facing overview of a single student's progress across all challenge groups
 * in a language, with per-invocation colored cells updated in real time via WebSockets.
 */
internal object StudentSummaryPage {
  private val logger = KotlinLogging.logger {}

  suspend fun RoutingContext.studentSummaryPage(content: ReadingBatContent, user: User?): String {
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

      else -> {
        // classCode != activeClassCode -> throw InvalidRequestException("Class code mismatch")
        // Look up the teacher id once and reuse it for both the check and the message.
        val teacherId = classCode.fetchClassTeacherId()
        if (teacherId != user.userId)
          throw InvalidRequestException("User id ${user.userId} does not match class code's teacher Id $teacherId")
      }
    }

    // Pre-compute each challenge's invocation count before the (non-suspend) kotlinx.html builder,
    // since functionInfo() suspends. Keyed by Challenge identity to avoid cross-group name clashes.
    val invocationCounts: Map<Challenge, Int> =
      content.findLanguage(languageName).challengeGroups
        .flatMap { it.challenges }
        .associateWith { it.functionInfo().invocationCount }

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

          displayChallengeGroups(content, classCode, languageName, invocationCounts)
          enableWebSockets(languageName, student, classCode)
          backLink(returnPath)
          loadPingdomScript()
        }
      }
  }

  /**
   * Builds the inline `onSubmit` confirmation handler for the remove-from-class button.
   *
   * The student name is user-controlled and is embedded inside a single-quoted JS string literal,
   * so it is escaped with [escapeEcmaScript] to neutralize quotes and other JS metacharacters and
   * prevent script injection into the teacher's page.
   */
  internal fun confirmRemovalScript(studentName: String): String =
    "return confirm('Are you sure you want to remove ${escapeEcmaScript(studentName)} from the class?')"

  internal fun BODY.removeFromClassButton(student: User, studentName: String) {
    form(classes = "m-0") {
      action = TEACHER_PREFS_ENDPOINT
      method = post
      onSubmit = confirmRemovalScript(studentName)
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
    invocationCounts: Map<Challenge, Int>,
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
                              repeat(invocationCounts[challenge] ?: 0) { i ->
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
          ${PageUtils.wsHostRewriteJs()}

          var wsurl = wshost + '$WS_ROOT$STUDENT_SUMMARY_ENDPOINT/' + ${encodeUriElems(langName, studentId, classCode)};
          var ws = new WebSocket(wsurl);

          ws.onopen = function (event) {
            ws.send("$classCode");
          };

          ${PageUtils.summaryOnMessageJs(WsProtocol.GROUP_NAME_FIELD)}
        """.trimIndent(),
      )
    }
  }
}
