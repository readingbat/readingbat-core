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

import com.github.readingbat.common.CSSNames
import com.github.readingbat.common.CSSNames.CENTER
import com.github.readingbat.common.CommonUtils.pathOf
import com.github.readingbat.common.Endpoints.STATIC_ROOT
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.pages.PageUtils.backLink
import com.github.readingbat.pages.PageUtils.bodyTitle
import com.github.readingbat.pages.PageUtils.headDefault
import kotlinx.html.*
import kotlinx.html.stream.createHTML

internal object NotFoundPage {

  fun notFoundPage(content: ReadingBatContent, uri: String) =
    createHTML()
      .html {
        head { headDefault(content) }

        body {
          bodyTitle()

          p {
            img(classes = CENTER) { src = pathOf(STATIC_ROOT, "nervous.png") }
          }

          h2 { +"On no! We are afraid that page that does not exist" }

          div(classes = CSSNames.INDENT_1EM) {
            p {
              +"We cannot find the page: \""
              b { +uri }
              +"\""
            }

            p {
              +"Please head back to the"
              a { href = "/"; +" home directory." }
            }
          }

          backLink("/")

          content.pingdomUrl.also { if (it.isNotBlank()) script { src = it; async = true } }
        }
      }
}