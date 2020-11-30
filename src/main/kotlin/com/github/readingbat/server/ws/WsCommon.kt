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

package com.github.readingbat.server.ws

import com.github.pambrose.common.util.isNotNull
import com.github.readingbat.common.ClassCode
import com.github.readingbat.common.Metrics
import com.github.readingbat.common.User
import com.github.readingbat.common.isNotValidUser
import com.github.readingbat.dsl.InvalidRequestException
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.server.GroupName
import com.github.readingbat.server.LanguageName
import com.github.readingbat.server.ws.ChallengeGroupWs.challengeGroupWsEndpoint
import com.github.readingbat.server.ws.ChallengeWs.challengeWsEndpoint
import com.github.readingbat.server.ws.ClassSummaryWs.classSummaryWsEndpoint
import com.github.readingbat.server.ws.LoggingWs.loggingWsEndpoint
import com.github.readingbat.server.ws.StudentSummaryWs.studentSummaryWsEndpoint
import io.ktor.routing.*
import io.ktor.websocket.*
import mu.KLogging

internal object WsCommon : KLogging() {

  const val LANGUAGE_NAME = "languageName"
  const val GROUP_NAME = "groupName"
  const val STUDENT_ID = "studentId"
  const val CLASS_CODE = "classCode"
  const val CHALLENGE_MD5 = "challengeMd5"
  const val LOG_ID = "logId"

  fun Routing.wsRoutes(metrics: Metrics? = null, contentSrc: () -> ReadingBatContent) {
    challengeWsEndpoint(metrics)
    challengeGroupWsEndpoint(metrics, contentSrc)
    classSummaryWsEndpoint(metrics, contentSrc)
    studentSummaryWsEndpoint(metrics, contentSrc)
    loggingWsEndpoint(metrics)
    //clockWsEndpoint()
  }

  fun validateContext(languageName: LanguageName?,
                      groupName: GroupName?,
                      classCode: ClassCode,
                      student: User?,
                      user: User) =
    when {
      languageName.isNotNull() && languageName.isNotValid() -> false to "Invalid language: $languageName"
      groupName.isNotNull() && groupName.isNotValid() -> false to "Invalid group: $groupName"
      classCode.isNotValid() -> false to "Invalid class code: $classCode"
      classCode.isNotEnabled -> false to "Class code not enabled"
      student.isNotNull() && student.isNotValidUser() -> false to "Invalid student id: ${student.userId}"
      student.isNotNull() && student.isNotEnrolled(classCode) -> false to "Student not enrolled in class"
      user.isNotValidUser() -> false to "Invalid user id: ${user.userId}"
      classCode.fetchClassTeacherId() != user.userId -> {
        val teacherId = classCode.fetchClassTeacherId()
        false to "User id ${user.userId} does not match class code's teacher Id $teacherId"
      }
      else -> true to ""
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

