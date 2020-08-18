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

import com.github.pambrose.common.util.isNull
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.Constants.RETURN_PATH
import com.github.readingbat.misc.Message
import com.github.readingbat.misc.Message.Companion.EMPTY_MESSAGE
import com.github.readingbat.misc.User
import com.github.readingbat.pages.HelpAndLogin.helpAndLogin
import com.github.readingbat.pages.PageCommon.backLink
import com.github.readingbat.pages.PageCommon.bodyTitle
import com.github.readingbat.pages.PageCommon.displayMessage
import com.github.readingbat.pages.PageCommon.headDefault
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.ServerUtils.queryParam
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import redis.clients.jedis.Jedis


internal object UserInfoPage {

  fun PipelineCall.userInfoPage(content: ReadingBatContent,
                                user: User?,
                                redis: Jedis,
                                msg: Message = EMPTY_MESSAGE) =
    createHTML()
      .html {

        head { headDefault(content) }
        body {
          val returnPath = queryParam(RETURN_PATH, "/")

          helpAndLogin(user, returnPath, false, redis)

          bodyTitle()

          when {
            user.isNull() -> {
              br { +"Must be logged in for this function" }
            }
            else -> {
              val name = user.name(redis)
              val email = user.email(redis)
              val classCodes = user.classCodes(redis)
              val browserSessions = user.browserSessions(redis)
              val correctAnswers = user.correctAnswers(redis)
              val likeDislikes = user.likeDislikes(redis)
              val challenges = user.challenges(redis)
              val invocations = user.invocations(redis)

              p {
                span {
                  style = "color:${if (msg.isError) "red" else "green"};"
                  this@body.displayMessage(msg)
                }
              }

              p {
                table {
                  tr { td { +"Name" }; td { +name } }
                  tr { td { +"Id" }; td { +user.id } }
                  tr { td { +"Email" }; td { +email.value } }
                  tr { td { +"User Info browser sessions" }; td { +browserSessions.size.toString() } }
                  tr { td { +"Correct Answers" }; td { +correctAnswers.size.toString() } }
                  tr { td { +"Likes/Dislikes" }; td { +likeDislikes.size.toString() } }
                  tr { td { +"Challenges" }; td { +challenges.size.toString() } }
                  tr { td { +"Invocations" }; td { +invocations.size.toString() } }
                  tr { td { +"User Classes" }; td { +classCodes.size.toString() } }
                }
              }
            }
          }

          backLink(returnPath)
        }
      }
}