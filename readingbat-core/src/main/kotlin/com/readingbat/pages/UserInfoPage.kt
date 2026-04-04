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

import com.pambrose.common.util.pluralize
import com.readingbat.common.FormFields.RETURN_PARAM
import com.readingbat.common.KeyConstants.KEY_SEP
import com.readingbat.common.Message
import com.readingbat.common.Message.Companion.EMPTY_MESSAGE
import com.readingbat.common.User
import com.readingbat.common.browserSession
import com.readingbat.common.isNotValidUser
import com.readingbat.common.userPrincipal
import com.readingbat.dsl.ReadingBatContent
import com.readingbat.pages.HelpAndLogin.helpAndLogin
import com.readingbat.pages.PageUtils.backLink
import com.readingbat.pages.PageUtils.bodyTitle
import com.readingbat.pages.PageUtils.displayMessage
import com.readingbat.pages.PageUtils.headDefault
import com.readingbat.pages.PageUtils.loadPingdomScript
import com.readingbat.server.ServerUtils.queryParam
import io.ktor.server.routing.RoutingContext
import kotlinx.html.body
import kotlinx.html.br
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.stream.createHTML
import kotlinx.html.style
import kotlinx.html.table
import kotlinx.html.td
import kotlinx.html.tr

/**
 * Generates the user info display page at `/userinfo`.
 *
 * Shows detailed account information including session IDs, challenge counts,
 * correct answers, like/dislikes, and class code enrollments.
 */
internal object UserInfoPage {
  fun RoutingContext.userInfoPage(content: ReadingBatContent, user: User?, msg: Message = EMPTY_MESSAGE) =
    createHTML()
      .html {
        head { headDefault() }

        body {
          val returnPath = queryParam(RETURN_PARAM, "/")

          helpAndLogin(content, user, returnPath, false)
          bodyTitle()

          when {
            user.isNotValidUser() -> {
              br { +"Must be logged in for this function" }
            }

            else -> {
              val name = user.fullName
              val email = user.email
              val browserSessions = user.browserSessions().map { it.split(KEY_SEP).last() }
              val idCnt = browserSessions.size
              val challenges = user.challenges()
              val invocations = user.invocations()
              val correctAnswers = user.correctAnswers()
              val likeDislikes = user.likeDislikes()
              val classCodes = user.classCodes()

              if (msg.isAssigned())
                p {
                  span {
                    style = "color:${msg.color}"
                    this@body.displayMessage(msg)
                  }
                }

              val principal = call.userPrincipal
              val sessionId = call.browserSession

              p {
                table(classes = "border-separate border-spacing-x-[5px] border-spacing-y-[10px]") {
                  tr {
                    td { +"User Principal" }
                    td { +principal.toString() }
                  }
                  tr {
                    td { +"Session Id" }
                    td { +sessionId.toString() }
                  }
                  tr {
                    td { +"Name" }
                    td { +name.value }
                  }
                  tr {
                    td { +"Id" }
                    td { +user.userId }
                  }
                  tr {
                    td { +"Email" }
                    td { +email.value }
                  }
                  tr {
                    td { +"Challenges" }
                    td { +challenges.size.toString() }
                  }
                  tr {
                    td { +"Invocations" }
                    td { +invocations.size.toString() }
                  }
                  tr {
                    td { +"Correct answers" }
                    td { +correctAnswers.size.toString() }
                  }
                  tr {
                    td { +"Likes/Dislikes" }
                    td { +likeDislikes.size.toString() }
                  }
                  tr {
                    td { +"Class Codes" }
                    td { +classCodes.joinToString(", ") }
                  }
                  tr {
                    td { +"$idCnt Session ${"Id".pluralize(idCnt)}" }
                    td { +browserSessions.joinToString(", ") }
                  }
                }
              }
            }
          }

          backLink(returnPath)
          loadPingdomScript()
        }
      }
}
