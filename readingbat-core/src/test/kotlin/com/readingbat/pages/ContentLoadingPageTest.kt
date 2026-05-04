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

import com.readingbat.kotest.TestSupport.initTestProperties
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs

class ContentLoadingPageTest : StringSpec() {
  init {
    "RETRY_AFTER_SECS is the single source for the meta-refresh and the Retry-After header" {
      ContentLoadingPage.RETRY_AFTER_SECS shouldBe 5
    }

    "rendered HTML contains the meta-refresh tag and the loading message" {
      initTestProperties()
      val html = ContentLoadingPage.contentLoadingPage()
      html shouldContain """http-equiv="refresh""""
      html shouldContain """content="${ContentLoadingPage.RETRY_AFTER_SECS}""""
      html shouldContain "Site is loading"
    }

    "repeated calls return the cached instance" {
      initTestProperties()
      val a = ContentLoadingPage.contentLoadingPage()
      val b = ContentLoadingPage.contentLoadingPage()
      a shouldBeSameInstanceAs b
    }
  }
}
