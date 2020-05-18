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
import kotlinx.html.*
import kotlinx.html.stream.createHTML

internal fun prefsPage(readingBatContent: ReadingBatContent) =
  createHTML()
    .html {

      head {
        headDefault(readingBatContent)
      }

      body {
        bodyTitle()

        div {
          h2 { +"ReadingBat Prefs" }
        }
      }
    }

internal fun privacy(readingBatContent: ReadingBatContent) =
  createHTML()
    .html {

      head {
        headDefault(readingBatContent)
      }

      body {
        bodyTitle()

        h2 { +"ReadingBat Privacy" }
        p {
          +"""
            ReadingBat is free -- anyone can access the site to learn and practice coding. We will not send you any 
            marketing email (spam), and we will not sell your name or contact information to anyone for marketing. 
            We will not identify you, your name or email address (if we should know them) in anything we make public. 
            We collect regular web server logs, and may use the data and submitted answers as part of research into 
            teaching technology, but we will never make public specific names or email addresses.
              """.trimIndent()
        }

        p {
          +"If you have any thoughts or suggestions about this server, please don't hesitate to email me at: "
          a {
            href = "mailto:pambrose@mac.com?subject=ReadingBat"
            +"pambrose@mac.com"
          }
        }
      }
    }
