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
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.img
import kotlinx.html.p
import kotlinx.html.stream.createHTML

/**
 * Generates a generic server error page displayed when an unhandled exception occurs.
 */
internal object ErrorPage {
  fun errorPage() =
    createHTML()
      .html {
        head { headDefault() }

        body {
          bodyTitle()

          p {
            img(classes = TwClasses.CENTER) { src = pathOf(STATIC_ROOT, "bscod-small.jpg") }
          }

          h2 { +"Ouch! Not sure what happened, but we seem to have had a problem!" }

          div(classes = TwClasses.INDENT_1EM) {
            p {
              +"Sorry for the inconvenience. We will look into the problem. In the mean time, please head back to the"
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
