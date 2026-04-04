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

import com.readingbat.common.FormFields.RETURN_PARAM
import com.readingbat.common.TwClasses
import com.readingbat.pages.PageUtils.backLink
import com.readingbat.pages.PageUtils.bodyTitle
import com.readingbat.pages.PageUtils.headDefault
import com.readingbat.pages.PageUtils.loadPingdomScript
import com.readingbat.server.ServerUtils.queryParam
import io.ktor.server.routing.RoutingContext
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.iframe
import kotlinx.html.stream.createHTML

/**
 * Generates the privacy policy page at `/privacypolicy`, embedding the policy via a Google Docs iframe.
 */
internal object PrivacyPolicyPage {
  fun RoutingContext.privacyPolicyPage() =
    createHTML()
      .html {
        head { headDefault() }

        body {
          bodyTitle()

          h2 { +"ReadingBat.com Privacy Policy" }

          div(classes = TwClasses.INDENT_1EM) {
//            p {
//              +"""
//            ReadingBat.com is free -- anyone can access the site to learn and practice reading code. We will not send
//            you any marketing email (spam), and we will not sell your name or contact information to anyone for marketing.
//            We will not identify you, your name or email address (if we should know them) in anything we make public.
//            We collect regular web server logs, and may use the data and submitted answers as part of research into
//            teaching technology, but we will never make public specific names or email addresses.
//              """.trimIndent()
//            }

            iframe {
              src =
                "https://docs.google.com/document/d/e/2PACX-1vRzLz7fCGLoyyYckQ6VUmy0q-KL5noI8VIqWGHIFoNJDgQL5VTcnegcmTnv8dYs4SSnAscltBIvL3kF/pub?embedded=true"
              width = "75%"
              height = "600"
            }
          }

          backLink(queryParam(RETURN_PARAM, "/"))
          loadPingdomScript()
        }
      }
}
