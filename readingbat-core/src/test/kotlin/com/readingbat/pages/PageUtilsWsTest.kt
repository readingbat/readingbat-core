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

import com.readingbat.common.WsProtocol
import com.readingbat.pages.PageUtils.summaryOnMessageJs
import com.readingbat.pages.PageUtils.wsHostRewriteJs
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain

/**
 * Tests the shared WebSocket JS helpers extracted from the page objects so the origin-rewrite and
 * summary onmessage loop live in one place instead of being copy-pasted across pages.
 */
class PageUtilsWsTest : StringSpec() {
  init {
    "wsHostRewriteJs emits the https->wss / http->ws origin rewrite once" {
      val js = wsHostRewriteJs()
      js shouldContain "var wshost = location.origin;"
      js shouldContain "wshost.replace(/^https:/, 'wss:')"
      js shouldContain "wshost.replace(/^http:/, 'ws:')"
    }

    "summaryOnMessageJs parameterizes only the prefix field" {
      val classJs = summaryOnMessageJs(WsProtocol.USER_ID_FIELD)
      val studentJs = summaryOnMessageJs(WsProtocol.GROUP_NAME_FIELD)

      classJs shouldContain """obj["${WsProtocol.USER_ID_FIELD}"]"""
      studentJs shouldContain """obj["${WsProtocol.GROUP_NAME_FIELD}"]"""

      // Both share the results-loop body; they differ only by the prefix field.
      classJs shouldContain "for (i = 0; i < results.length; i++)"
      studentJs shouldContain "for (i = 0; i < results.length; i++)"
      classJs shouldNotBe studentJs
    }
  }
}
