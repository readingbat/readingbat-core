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

import com.github.readingbat.common.CSSNames.INDENT_1EM
import com.github.readingbat.common.Endpoints.CONFIG_ENDPOINT
import com.github.readingbat.common.Endpoints.SESSIONS_ENDPOINT
import com.github.readingbat.common.Endpoints.SYSTEM_ADMIN_ENDPOINT
import com.github.readingbat.common.FormFields.RETURN_PARAM
import com.github.readingbat.common.Property.PINGDOM_BANNER_ID
import com.github.readingbat.common.User
import com.github.readingbat.common.User.Companion.fetchActiveClassCode
import com.github.readingbat.common.isAdminUser
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.dsl.isProduction
import com.github.readingbat.pages.HelpAndLogin.helpAndLogin
import com.github.readingbat.pages.PageUtils.backLink
import com.github.readingbat.pages.PageUtils.bodyTitle
import com.github.readingbat.pages.PageUtils.headDefault
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.ServerUtils.queryParam
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.h2
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.img
import kotlinx.html.p
import kotlinx.html.stream.createHTML
import kotlinx.html.title
import mu.KLogging

internal object AdminPrefsPage : KLogging() {

  fun PipelineCall.adminPrefsPage(content: ReadingBatContent, user: User?) =
    createHTML()
      .html {
        head { headDefault(content) }

        body {
          val activeClassCode = fetchActiveClassCode(user)
          val returnPath = queryParam(RETURN_PARAM, "/")

          helpAndLogin(content, user, returnPath, activeClassCode.isEnabled)

          bodyTitle()

          h2 { +"Admin Preferences" }

          if (!isProduction() || user.isAdminUser()) {
            p(classes = INDENT_1EM) {
              a { href = "$CONFIG_ENDPOINT?$RETURN_PARAM=$returnPath"; +"System Configuration" }
            }

            p(classes = INDENT_1EM) {
              a { href = "$SESSIONS_ENDPOINT?$RETURN_PARAM=$returnPath"; +"Current Sessions" }
            }

            p(classes = INDENT_1EM) {
              a { href = "$SYSTEM_ADMIN_ENDPOINT?$RETURN_PARAM=$returnPath"; +"System Admin" }
            }

            PINGDOM_BANNER_ID.getPropertyOrNull()
              ?.also {
                p(classes = INDENT_1EM) {
                  a {
                    href = "https://share.pingdom.com/banners/$it"
                    img {
                      src = "https://share.pingdom.com/banners/$it"
                      alt = "Uptime Report for ReadingBat.com: Last 30 days"
                      title = "Uptime Report for ReadingBat.com: Last 30 days"
                      width = "300"
                      height = "165"
                    }
                  }
                }
              }
          }
          else {
            p { +"Not authorized" }
          }

          backLink(returnPath)
        }
      }
}