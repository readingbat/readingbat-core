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

import com.readingbat.pages.PageUtils.bodyTitle
import com.readingbat.pages.PageUtils.headDefault
import com.readingbat.pages.PageUtils.loadPingdomScript
import kotlinx.html.body
import kotlinx.html.h2
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.meta
import kotlinx.html.p
import kotlinx.html.stream.createHTML

/**
 * Generated while the DSL content is still loading at server startup. Includes a meta refresh so
 * browsers retry automatically once the content is ready.
 */
internal object ContentLoadingPage {
  /** Suggested retry interval shown to clients (browser meta-refresh, HTTP `Retry-After`). */
  const val RETRY_AFTER_SECS = 5

  // Cached at first access so the per-request readiness intercept doesn't rebuild this static HTML.
  private val rendered: String by lazy {
    createHTML()
      .html {
        head {
          meta {
            httpEquiv = "refresh"
            content = RETRY_AFTER_SECS.toString()
          }
          headDefault()
        }
        body {
          bodyTitle()
          h2 { +"Site is loading" }
          p { +"Site content is loading. Please try again in a moment." }
          loadPingdomScript()
        }
      }
  }

  fun contentLoadingPage(): String = rendered
}
