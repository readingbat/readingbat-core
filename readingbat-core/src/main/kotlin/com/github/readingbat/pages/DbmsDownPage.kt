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

import com.github.pambrose.common.util.pathOf
import com.github.readingbat.common.Constants.DBMS_DOWN
import com.github.readingbat.common.CssNames.CENTER
import com.github.readingbat.common.Endpoints.STATIC_ROOT
import com.github.readingbat.pages.PageUtils.backLink
import com.github.readingbat.pages.PageUtils.bodyTitle
import com.github.readingbat.pages.PageUtils.headDefault
import com.github.readingbat.pages.PageUtils.loadPingdomScript
import kotlinx.html.body
import kotlinx.html.h2
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.img
import kotlinx.html.p
import kotlinx.html.stream.createHTML

internal object DbmsDownPage {
  fun dbmsDownPage() =
    createHTML()
      .html {
        head { headDefault() }
        body {
          bodyTitle()
          p { img(classes = CENTER) { src = pathOf(STATIC_ROOT, "dbmsdown.jpg") } }
          h2 { +DBMS_DOWN.toString() }
          p { +"We seem to be having problems with our database. Please check back later." }
          backLink("/")
          loadPingdomScript()
        }
      }
}
