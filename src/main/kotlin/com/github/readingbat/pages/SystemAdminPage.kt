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

import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.dsl.isProduction
import com.github.readingbat.misc.Constants.RETURN_PATH
import com.github.readingbat.misc.Endpoints.RESET_CACHE_ENDPOINT
import com.github.readingbat.misc.Endpoints.RESET_CONTENT_ENDPOINT
import com.github.readingbat.misc.Message
import com.github.readingbat.misc.Message.Companion.EMPTY_MESSAGE
import com.github.readingbat.misc.User
import com.github.readingbat.misc.User.Companion.fetchActiveClassCode
import com.github.readingbat.misc.isValidUser
import com.github.readingbat.pages.HelpAndLogin.helpAndLogin
import com.github.readingbat.pages.PageCommon.backLink
import com.github.readingbat.pages.PageCommon.bodyTitle
import com.github.readingbat.pages.PageCommon.button
import com.github.readingbat.pages.PageCommon.headDefault
import com.github.readingbat.pages.UserPrefsPage.requestLogInPage
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.ServerUtils.queryParam
import kotlinx.html.body
import kotlinx.html.h2
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.p
import kotlinx.html.stream.createHTML
import mu.KLogging
import redis.clients.jedis.Jedis

internal object SystemAdminPage : KLogging() {

  fun PipelineCall.systemAdminPage(content: ReadingBatContent,
                                   user: User?,
                                   redis: Jedis,
                                   msg: Message = EMPTY_MESSAGE,
                                   defaultClassDesc: String = "") =
    if (user.isValidUser(redis))
      systemAdminLoginPage(content, user, msg, defaultClassDesc, redis)
    else
      requestLogInPage(content, redis)

  private fun PipelineCall.systemAdminLoginPage(content: ReadingBatContent,
                                                user: User,
                                                msg: Message,
                                                defaultClassDesc: String,
                                                redis: Jedis) =
    createHTML()
      .html {
        val activeClassCode = user.fetchActiveClassCode(redis)

        head {
          headDefault(content)
        }

        body {
          val returnPath = queryParam(RETURN_PATH, "/")

          helpAndLogin(user, returnPath, activeClassCode.isEnabled, redis)
          bodyTitle()

          h2 { +"ReadingBat System Admin" }

          if (!isProduction() || user.isAdmin(redis)) {
            p {
              this@body.button("Reset ReadingBat Content",
                               RESET_CONTENT_ENDPOINT,
                               "Are you sure you want to reset the content? (This can take a while)")
            }

            p {
              this@body.button("Reset Challenge Cache",
                               RESET_CACHE_ENDPOINT,
                               "Are you sure you want to reset the challenge cache?")
            }
          }
          else {
            p {
              +"Not authorized"
            }
          }

          backLink(returnPath)
        }
      }
}