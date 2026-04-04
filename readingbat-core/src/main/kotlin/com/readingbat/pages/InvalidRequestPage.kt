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

import com.pambrose.common.util.pathOf
import com.readingbat.common.Endpoints.STATIC_ROOT
import com.readingbat.common.TwClasses
import com.readingbat.pages.PageUtils.backLink
import com.readingbat.pages.PageUtils.bodyTitle
import com.readingbat.pages.PageUtils.headDefault
import com.readingbat.pages.PageUtils.loadPingdomScript
import kotlinx.html.a
import kotlinx.html.b
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.i
import kotlinx.html.img
import kotlinx.html.p
import kotlinx.html.stream.createHTML

/**
 * Generates an error page for invalid or malformed requests, displaying the URI and problem description.
 */
internal object InvalidRequestPage {
  fun invalidRequestPage(uri: String, msg: String) =
    createHTML()
      .html {
        head { headDefault() }

        body {
          bodyTitle()

          p { img(classes = TwClasses.CENTER) { src = pathOf(STATIC_ROOT, "panic.png") } }

          h2 { +"There seems to be a misunderstanding" }

          div(classes = TwClasses.INDENT_1EM) {
            p {
              +"The request "
              i { +uri }
              +" has this problem: "
            }
            p { b { +msg } }
            p {
              +"Please head back to the"
              a {
                href = "/"
                +" home directory."
              }
            }
          }

          backLink("/")
          loadPingdomScript()
        }
      }
}
