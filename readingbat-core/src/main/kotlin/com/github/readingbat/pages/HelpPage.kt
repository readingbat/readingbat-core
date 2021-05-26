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

import com.github.pambrose.common.util.pathOf
import com.github.readingbat.common.CSSNames.INDENT_1EM
import com.github.readingbat.common.Endpoints.ABOUT_ENDPOINT
import com.github.readingbat.common.Endpoints.STATIC_ROOT
import com.github.readingbat.common.Endpoints.TEACHER_PREFS_ENDPOINT
import com.github.readingbat.common.Endpoints.USER_PREFS_ENDPOINT
import com.github.readingbat.common.FormFields.CREATE_CLASS
import com.github.readingbat.common.FormFields.JOIN_A_CLASS
import com.github.readingbat.common.FormFields.NO_ACTIVE_CLASS
import com.github.readingbat.common.FormFields.RETURN_PARAM
import com.github.readingbat.common.FormFields.UPDATE_ACTIVE_CLASS
import com.github.readingbat.common.User
import com.github.readingbat.common.User.Companion.queryActiveTeachingClassCode
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.pages.HelpAndLogin.helpAndLogin
import com.github.readingbat.pages.PageUtils.backLink
import com.github.readingbat.pages.PageUtils.bodyTitle
import com.github.readingbat.pages.PageUtils.headDefault
import com.github.readingbat.pages.PageUtils.loadPingdomScript
import com.github.readingbat.pages.PageUtils.rawHtml
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.ServerUtils.queryParam
import kotlinx.html.*
import kotlinx.html.stream.createHTML

internal object HelpPage {

  fun PipelineCall.helpPage(content: ReadingBatContent, user: User?) =
    createHTML()
      .html {
        head { headDefault() }

        body {
          val activeTeachingClassCode = queryActiveTeachingClassCode(user)
          helpAndLogin(content, user, ABOUT_ENDPOINT, activeTeachingClassCode.isEnabled)
          bodyTitle()

          h2 { +"ReadingBat Help" }

          div(classes = INDENT_1EM) {
            h3 { +"Student Tips" }

            div(classes = INDENT_1EM) {
              h4 { rawHtml("&bull;"); +" $JOIN_A_CLASS" }

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
                i { +" $CREATE_CLASS " }
                +"button on the"
                a { href = TEACHER_PREFS_ENDPOINT; b { +" Teacher Preferences " } }
                +"page. Each class will be assigned a unique class code. "
                +"To enroll students in a class, send them the class code and ask them to follow the"
                i { +" $JOIN_A_CLASS " }
                +"instructions above. For testing purposes, it is fine to enroll in the class you created."
              }

              h4 { rawHtml("&bull;"); +" Select an Active Class" }

              p {
                +"Select the radio button of the class you are interested in monitoring and then click the"
                b { +" $UPDATE_ACTIVE_CLASS " }
                +"button. You will then be in"
                i { +" teacher mode, " }
                +"which will allow you see student progress. Select the"
                b { +" $NO_ACTIVE_CLASS " }
                +"option and then click the"
                b { +" $UPDATE_ACTIVE_CLASS " }
                +"button to exit"
                i { +" teacher mode." }
              }

              h4 { rawHtml("&bull;"); +" Student/Teacher Mode" }

              p {
                +"While an active class is selected, you can toggle back and forth between"
                i { +" teacher " }
                +"and"
                i { +" student mode " }
                +"by clicking the desired mode at the top of the screen. "
              }

              p {
                +"It is sometimes desirable to monitor students in teacher mode and also work with challenges in student mode. "
                +"This is possible, but it requires that you login from a second browser on the same machine (not two windows of the same browser). "
                +"One browser would be in teacher mode while the other would be in student mode."
              }
              p {
                +"You can monitor student inputs on multiple challenges by opening multiple windows in a single browser."
              }

              h4 { rawHtml("&bull;"); +" Self-driven Demo" }

              p {
                +"To see both the student and teacher experience with ReadingBat, follow these steps:"

                val help = "help"
                val h4 = "400"
                val h5 = "500"
                val h6 = "600"
                val s = "margin-top:5; margin-bottom:5"
                ol {
                  li { +"Create a ReadingBat account and sign into it." }
                  img { style = s; height = h4; src = pathOf(STATIC_ROOT, help, "create-account.png") }

                  li { +"Go to the teacher preferences and create a demo class and copy the class code into your copy/paste buffer." }
                  li { +"On the same page, select the radio button of the newly created class and make it your active class." }

                  img { style = s; height = h5; src = pathOf(STATIC_ROOT, help, "teacher-classes.png") }

                  li { +"Go to the user preferences and paste the class code you just copied and enroll as a student in your own class." }
                  li { +"Go to one of the challenges (in teacher mode) and you should see yourself as the only student." }
                  li { +"Open a second browser (not just a second window) and go to ReadingBat.com and login." }

                  li { +"Go to the same challenge as a student and enter some answers. You will see your answers appearing in the other browser." }
                  img { style = s; height = h6; src = pathOf(STATIC_ROOT, help, "challenge-feedback.png") }

                  li { +"Go back to the other browser (in teacher mode) and click on the challenge group link to see statistics for all the challenges in that group." }
                  img { style = s; height = h4; src = pathOf(STATIC_ROOT, help, "group-summary.png") }

                  li { +"Click on the class link to see a class summary." }
                  img { style = s; height = h4; src = pathOf(STATIC_ROOT, help, "class-summary.png") }

                  li { +"Click on your student link to see a student overview." }
                  img { style = s; height = h5; src = pathOf(STATIC_ROOT, help, "student-summary.png") }
                }
              }
            }
          }

          backLink(queryParam(RETURN_PARAM, "/"))
          loadPingdomScript()
        }
      }
}
