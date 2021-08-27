/*
 * Copyright © 2021 Paul Ambrose (pambrose@mac.com)
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
import com.github.readingbat.common.FormFields.RETURN_PARAM
import com.github.readingbat.pages.PageUtils.backLink
import com.github.readingbat.pages.PageUtils.bodyTitle
import com.github.readingbat.pages.PageUtils.headDefault
import com.github.readingbat.pages.PageUtils.loadPingdomScript
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

internal object PrivacyPage {

  fun PipelineCall.privacyPage() =
    createHTML()
      .html {
        head { headDefault() }

        body {
          bodyTitle()

          h2 { +"ReadingBat Privacy" }

          div(classes = INDENT_1EM) {
            p {
              +"""
            ReadingBat.com is free -- anyone can access the site to learn and practice reading code. We will not send  
            you any marketing email (spam), and we will not sell your name or contact information to anyone for marketing. 
            We will not identify you, your name or email address (if we should know them) in anything we make public. 
            We collect regular web server logs, and may use the data and submitted answers as part of research into 
            teaching technology, but we will never make public specific names or email addresses.
              """.trimIndent()
            }

            p {
              +"If you have any thoughts or suggestions about ReadingBat.com, please don't hesitate to email us at: "
              a {
                href = "mailto:suggestions@readingbat.com?subject=ReadingBat"
                +"suggestions@readingbat.com"
              }
            }
          }

          backLink(queryParam(RETURN_PARAM, "/"))
          loadPingdomScript()
        }
      }
}