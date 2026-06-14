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

package com.readingbat.server

import com.readingbat.server.GeoInfo.Companion.MAX_GEO_CACHE_SIZE
import com.readingbat.server.GeoInfo.Companion.geoInfoMap
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Tests that geoInfoMap is bounded: on a public-facing server the client-IP key space is unbounded,
 * so the cache must evict eldest entries past [MAX_GEO_CACHE_SIZE] rather than grow without limit.
 */
class GeoCacheBoundedTest : StringSpec() {
  init {
    "geoInfoMap evicts eldest entries once it exceeds the size cap" {
      geoInfoMap.clear()

      repeat(MAX_GEO_CACHE_SIZE + 500) { i ->
        geoInfoMap["10.$i"] = GeoInfo(false, i.toLong(), "10.$i", "")
      }

      geoInfoMap.size shouldBe MAX_GEO_CACHE_SIZE

      geoInfoMap.clear()
    }
  }
}
