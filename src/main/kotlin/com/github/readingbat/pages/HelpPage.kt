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
import com.github.readingbat.common.Endpoints.ABOUT_ENDPOINT
import com.github.readingbat.common.Endpoints.TEACHER_PREFS_ENDPOINT
import com.github.readingbat.common.Endpoints.USER_PREFS_ENDPOINT
import com.github.readingbat.common.User
import com.github.readingbat.common.User.Companion.fetchActiveClassCode
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.pages.HelpAndLogin.helpAndLogin
import com.github.readingbat.pages.PageUtils.bodyTitle
import com.github.readingbat.pages.PageUtils.headDefault
import com.github.readingbat.pages.PageUtils.homeLink
import com.github.readingbat.pages.PageUtils.rawHtml
import com.github.readingbat.server.PipelineCall
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import redis.clients.jedis.Jedis

internal object HelpPage {

  fun PipelineCall.helpPage(content: ReadingBatContent, user: User?, redis: Jedis) =
    createHTML()
      .html {
        head {
          headDefault(content)
        }

        body {
          helpAndLogin(user, ABOUT_ENDPOINT, user.fetchActiveClassCode(redis).isEnabled, redis)

          bodyTitle()

          h2 { +"ReadingBat Help" }

          div(classes = INDENT_1EM) {
            h3 { +"Student Tips" }

            div(classes = INDENT_1EM) {
              h4 { rawHtml("&bull;"); +" Join a Class" }

              p {
                +"Your teacher will give you a class code required to join a class. Click on "
                a { href = USER_PREFS_ENDPOINT; b { +" prefs " } }
                +"and paste the value into the"
                i { +" Class Code " }
                +"text entry and then click on the "
                i { +" Join Class " }
                +"button."
              }

              h4 { rawHtml("&bull;"); +" Withdraw from a Class" }

              p {
                +"Click on"
                a { href = USER_PREFS_ENDPOINT; b { +" prefs " } }
                +"and then click on the"
                i { +" Withdraw From Class " }
                +"button."
              }

              h4 { rawHtml("&bull;"); +" Check Answers" }

              p {
                +"Check your challenge answers by clicking on the"
                i { +" Check My Answers " }
                +"button or press the tab key."
              }
            }

            h3 { +"Teacher Tips" }

            p {
              +"The link for the"
              a { href = TEACHER_PREFS_ENDPOINT; b { +" Teacher Preferences " } }
              +"is at the bottom of the "
              a { href = USER_PREFS_ENDPOINT; b { +" prefs " } }
              +"page."
            }


            div(classes = INDENT_1EM) {
              h4 { rawHtml("&bull;"); +" Create a Class" }

              p {
                +"Enter a class description and click on the"
                i { +" Create Class " }
                +"button on the"
                a { href = TEACHER_PREFS_ENDPOINT; b { +" Teacher Preferences " } }
                +"page. Each class will have a unique class code. "
                +"To enroll students in a class, send them the class code and ask them to follow the"
                i { +" Join a Class " }
                +"instructions above."
              }

              h4 { rawHtml("&bull;"); +" Select Active Class" }

              p {
                +"Select an active class to enter teacher mode, which will allow you to see "
                +"student answers in real-time and class results. "
              }

              h4 { rawHtml("&bull;"); +" Student/Teacher Mode" }

              p {
                +"Click on"
                a { href = USER_PREFS_ENDPOINT; b { +" prefs " } }
                +" and then "
                a { href = USER_PREFS_ENDPOINT; b { +" Teacher Preferences " } }

              }
            }
          }

          homeLink()
        }
      }
}
