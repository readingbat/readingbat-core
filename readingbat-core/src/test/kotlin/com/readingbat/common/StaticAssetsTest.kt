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

package com.readingbat.common

import com.readingbat.BuildConfig
import com.readingbat.common.Constants.ICONS
import com.readingbat.common.Endpoints.STATIC_PATH
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain

/**
 * Covers the URL building in [StaticAssets], in particular the prefix normalization.
 *
 * `pathOf` joins with `/` without collapsing duplicates and drops empty elements, so a configured
 * prefix that is blank, `"/"`, or ends in `/` each fail differently — and each failure is a broken
 * asset URL on every page.
 */
class StaticAssetsTest : StringSpec() {
  private fun withPrefix(value: String, block: () -> Unit) {
    try {
      Property.STATIC_URL_PREFIX.setProperty(value)
      block()
    } finally {
      KtorProperty.configStore.remove(Property.STATIC_URL_PREFIX.propertyName)
    }
  }

  init {
    "asset urls are local and versioned by default" {
      StaticAssets.urlOf(ICONS, "favicon.ico") shouldBe
        "$STATIC_PATH/$ICONS/favicon.ico?v=${BuildConfig.CORE_VERSION}"
    }

    "a configured CDN prefix is used verbatim" {
      withPrefix("https://static.readingbat.com") {
        StaticAssets.urlOf("white-check.jpg") shouldBe
          "https://static.readingbat.com/white-check.jpg?v=${BuildConfig.CORE_VERSION}"
      }
    }

    "a prefix with a trailing slash does not produce a doubled separator" {
      withPrefix("https://static.readingbat.com/") {
        StaticAssets.urlOf("white-check.jpg").shouldNotContain("com//")
      }
    }

    "a root prefix falls back to the static path rather than emitting a relative url" {
      withPrefix("/") {
        StaticAssets.urlOf("white-check.jpg") shouldBe
          "$STATIC_PATH/white-check.jpg?v=${BuildConfig.CORE_VERSION}"
      }
    }

    "a blank prefix falls back to the static path" {
      withPrefix("") {
        StaticAssets.urlOf("white-check.jpg") shouldBe
          "$STATIC_PATH/white-check.jpg?v=${BuildConfig.CORE_VERSION}"
      }
    }
  }
}
