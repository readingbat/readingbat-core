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

package com.readingbat.dsl

import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.html.HtmlRenderer.SOFT_BREAK
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet

/**
 * Converts Markdown text to HTML for rendering challenge and group descriptions.
 *
 * Uses the [flexmark](https://github.com/vsch/flexmark-java) library with soft breaks
 * configured to render as `<br />` tags. This parser is used by both [ChallengeGroup] and
 * [Challenge][com.readingbat.dsl.challenge.Challenge] to transform their `description` properties
 * into displayable HTML.
 */
internal object MarkdownParser {
  private val options by lazy { MutableDataSet().apply { set(SOFT_BREAK, "<br />\n") } }
  private val parser by lazy { Parser.builder(options).build() }
  private val renderer by lazy { HtmlRenderer.builder(options).build() }

  /** Converts a [markdown] string to HTML. Thread-safe; parsing is synchronized. */
  fun toHtml(markdown: String) =
    synchronized(this) {
      parser.parse(markdown.trimIndent())
        .let {
          renderer.render(it)
        }
    }
}
