/*
 * Copyright © 2023 Paul Ambrose (pambrose@mac.com)
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

import com.github.readingbat.common.FormFields.RETURN_PARAM
import com.github.readingbat.common.TwClasses
import com.github.readingbat.pages.PageUtils.backLink
import com.github.readingbat.pages.PageUtils.bodyTitle
import com.github.readingbat.pages.PageUtils.headDefault
import com.github.readingbat.pages.PageUtils.loadPingdomScript
import com.github.readingbat.server.ServerUtils.queryParam
import io.ktor.server.routing.RoutingContext
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.iframe
import kotlinx.html.stream.createHTML

internal object TOSPage {
  fun RoutingContext.tosPage() =
    createHTML()
      .html {
        head { headDefault() }

        body {
          bodyTitle()

          h2 { +"ReadingBat.com Terms Of Service" }

          div(classes = TwClasses.INDENT_1EM) {
            iframe {
              src =
                "https://docs.google.com/document/d/e/2PACX-1vSgQLk-ulnGo50HbQObbeD_MsoaXsHr96BE6XlyHhMnJ0CN8frRtVmtGWf3n0i50iPxLRJdrkiFl4Ys/pub?embedded=true"
              width = "75%"
              height = "600"
            }
          }

          backLink(queryParam(RETURN_PARAM, "/"))
          loadPingdomScript()
        }
      }
}
