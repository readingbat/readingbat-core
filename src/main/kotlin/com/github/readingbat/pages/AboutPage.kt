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
import com.github.readingbat.misc.CSSNames.INDENT_1EM
import com.github.readingbat.misc.Constants.RETURN_PATH
import com.github.readingbat.pages.PageCommon.backLink
import com.github.readingbat.pages.PageCommon.bodyTitle
import com.github.readingbat.pages.PageCommon.headDefault
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.ServerUtils.queryParam
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.p
import kotlinx.html.stream.createHTML

internal object AboutPage {

  fun PipelineCall.aboutPage(content: ReadingBatContent) =
    createHTML()
      .html {
        head { headDefault(content) }

        body {
          bodyTitle()

          h2 { +"About ReadingBat" }

          div(classes = INDENT_1EM) {
            p {
              +"""
              ReadingBat.com is a father-son effort by Paul and Matthew Ambrose to make learning how to program 
              a little easier.
              We are big fans of 
              """.trimIndent()

              a { href = "https://codingbat.com"; +"CodingBat.com " }

              +"""
              (so much so, that we shamelessly copied its look and feel). However, we observed
              that students often start using it to write code, prior to being equipped
              with the skill of reading code. It is difficult to write code without first learning 
              how to read and follow code! So we set out to create ReadingBat.com, which attempts 
              to make students comfortable reading code challenges and learning code idioms. Once a student
              is comfortable with reading code, they can head straight for 
            """.trimIndent()

              a { href = "https://codingbat.com"; +" CodingBat.com " }

              +"and move on to authoring their own code!"
            }

            p {
              +"If you are interested in creating your own challenges, have a look at how to create your own repo"
              a { href = "https://github.com/readingbat/readingbat-template/blob/master/README.md"; +" here" }
              +"."
            }

            p {
              +"If you have any thoughts or suggestions about ReadingBat.com, please don't hesitate to email us at: "
              a {
                href = "mailto:suggestions@readingbat.com?subject=ReadingBat"
                +"suggestions@readingbat.com"
              }
            }
          }

          backLink(queryParam(RETURN_PATH))
        }
      }
}
