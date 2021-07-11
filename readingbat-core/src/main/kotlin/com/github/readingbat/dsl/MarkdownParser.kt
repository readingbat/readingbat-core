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

package com.github.readingbat.dsl

import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.html.HtmlRenderer.SOFT_BREAK
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet

internal object MarkdownParser {
  private val options by lazy { MutableDataSet().apply { set(SOFT_BREAK, "<br />\n") } }
  private val parser by lazy { Parser.builder(options).build() }
  private val renderer by lazy { HtmlRenderer.builder(options).build() }

  fun toHtml(markdown: String) =
    synchronized(this) {
      parser.parse(markdown.trimIndent())
        .let {
          renderer.render(it)
        }
    }
}