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
import com.github.readingbat.common.FormFields.RETURN_PARAM
import com.github.readingbat.common.User
import com.github.readingbat.common.User.Companion.fetchActiveClassCode
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.pages.HelpAndLogin.helpAndLogin
import com.github.readingbat.pages.PageUtils.backLink
import com.github.readingbat.pages.PageUtils.bodyTitle
import com.github.readingbat.pages.PageUtils.headDefault
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.ServerUtils.queryParam
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
            h3 {
              +"Student Tips"
            }

            p {
              +"""
              ReadingBat.com is a father-son effort by Paul and Matthew Ambrose to make learning how to program 
              a little easier.
              We are big fans of 
              """.trimIndent()

              h3 {
                +"Teacher Tips"
              }

              +"""
              (so much so, that we shamelessly copied its look and feel). However, we observed
              that students often start using it to write code, prior to being equipped
              with the skill of reading code. It is difficult to write code without first learning 
              how to read and follow code! So we set out to create ReadingBat.com, which attempts 
              to make students comfortable reading code challenges and learning code idioms. Once a student
              is comfortable with reading code, they can head straight for 
            """.trimIndent()

            }
          }

          backLink(queryParam(RETURN_PARAM))
        }
      }
}
