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

package com.readingbat.server.ws

import com.readingbat.common.ClassCode
import com.readingbat.common.ClassCodeRepository.fetchClassTeacherId
import com.readingbat.common.ClassCodeRepository.isNotValid
import com.readingbat.common.Metrics
import com.readingbat.common.User
import com.readingbat.common.isNotValidUser
import com.readingbat.dsl.InvalidRequestException
import com.readingbat.dsl.ReadingBatContent
import com.readingbat.server.GroupName
import com.readingbat.server.LanguageName
import com.readingbat.server.ws.ChallengeGroupWs.challengeGroupWsEndpoint
import com.readingbat.server.ws.ChallengeWs.challengeWsEndpoint
import com.readingbat.server.ws.ClassSummaryWs.classSummaryWsEndpoint
import com.readingbat.server.ws.LoggingWs.loggingWsEndpoint
import com.readingbat.server.ws.StudentSummaryWs.studentSummaryWsEndpoint
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.routing.Routing
import io.ktor.server.websocket.DefaultWebSocketServerSession

/**
 * Shared WebSocket utilities and parameter constants.
 *
 * Provides WebSocket route registration via [wsRoutes], context validation for
 * teacher-facing WebSocket endpoints (verifying language, group, class code, student
 * enrollment, and teacher ownership), and a helper to close WebSocket channels.
 */
object WsCommon {
  private val logger = KotlinLogging.logger {}

  const val LANGUAGE_NAME = "languageName"
  const val GROUP_NAME = "groupName"
  const val STUDENT_ID = "studentId"
  const val CLASS_CODE = "classCode"
  const val CHALLENGE_MD5 = "challengeMd5"
  const val LOG_ID = "logId"

  fun Routing.wsRoutes(metrics: Metrics, contentSrc: () -> ReadingBatContent) {
    challengeWsEndpoint(metrics)
    challengeGroupWsEndpoint(metrics, contentSrc)
    classSummaryWsEndpoint(metrics, contentSrc)
    studentSummaryWsEndpoint(metrics, contentSrc)
    loggingWsEndpoint(metrics)
    // clockWsEndpoint()
  }

  /**
   * Validates the WebSocket connection context for teacher-facing endpoints.
   * Checks language, group, class code validity, student enrollment, and teacher ownership.
   * Throws [InvalidRequestException] with a descriptive message on failure.
   */
  fun validateContext(
    languageName: LanguageName?,
    groupName: GroupName?,
    classCode: ClassCode,
    student: User?,
    user: User,
  ) =
    when {
      languageName != null && languageName.isNotValid() -> {
        false to "Invalid language: $languageName"
      }

      groupName != null && groupName.isNotValid() -> {
        false to "Invalid group: $groupName"
      }

      classCode.isNotValid() -> {
        false to "Invalid class code: $classCode"
      }

      classCode.isNotEnabled -> {
        false to "Class code not enabled"
      }

      student != null && student.isNotValidUser() -> {
        false to "Invalid student id: ${student.userId}"
      }

      student != null && student.isNotEnrolled(classCode) -> {
        false to "Student not enrolled in class"
      }

      user.isNotValidUser() -> {
        false to "Invalid user id: ${user.userId}"
      }

      classCode.fetchClassTeacherId() != user.userId -> {
        val teacherId = classCode.fetchClassTeacherId()
        false to "User id ${user.userId} does not match class code's teacher Id $teacherId"
      }

      else -> {
        true to ""
      }
    }.also { (valid, msg) -> if (!valid) throw InvalidRequestException(msg) }

  fun validateLogContext(user: User) =
    when {
      user.isNotValidUser() -> false to "Invalid user id: ${user.userId}"
      else -> true to ""
    }.also { (valid, msg) -> if (!valid) throw InvalidRequestException(msg) }

  fun DefaultWebSocketServerSession.closeChannels() {
    outgoing.close()
    incoming.cancel()
  }
}
