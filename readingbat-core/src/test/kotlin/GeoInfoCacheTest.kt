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

import com.github.readingbat.server.GeoInfo
import com.github.readingbat.server.GeoInfo.Companion.geoInfoMap
import com.github.readingbat.server.GeoInfo.Companion.lookupGeoInfo
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

class GeoInfoCacheTest : StringSpec() {
  init {
    beforeEach {
      geoInfoMap.clear()
    }

    "GeoInfo with empty json returns UNKNOWN summary" {
      val info = GeoInfo(true, -1, "1.2.3.4", "")
      info.summary() shouldBe "Unknown"
      info.remoteHost shouldBe "1.2.3.4"
    }

    "GeoInfo with valid json parses summary fields" {
      val json =
        """{"ip":"8.8.8.8","city":"Mountain View","state_prov":"California","country_name":"United States","organization":"Google LLC"}"""
      val info = GeoInfo(false, 1, "8.8.8.8", json)
      info.summary() shouldBe "Mountain View, California, United States, Google LLC"
    }

    "Cache returns pre-populated entry without DB or API call" {
      val cached = GeoInfo(false, 42, "10.0.0.1", "")
      geoInfoMap["10.0.0.1"] = cached

      val result = lookupGeoInfo("10.0.0.1")
      result shouldBe cached
      result.dbmsId shouldBe 42
    }

    "Concurrent lookups for same IP return same cached instance" {
      val cached = GeoInfo(false, 99, "192.168.1.1", "")
      geoInfoMap["192.168.1.1"] = cached

      val results =
        (1..10).map {
          async { lookupGeoInfo("192.168.1.1") }
        }.awaitAll()

      results.forEach { it shouldBe cached }
    }

    "Different IPs return different cached entries" {
      val info1 = GeoInfo(false, 1, "10.0.0.1", "")
      val info2 = GeoInfo(false, 2, "10.0.0.2", "")
      geoInfoMap["10.0.0.1"] = info1
      geoInfoMap["10.0.0.2"] = info2

      lookupGeoInfo("10.0.0.1").dbmsId shouldBe 1
      lookupGeoInfo("10.0.0.2").dbmsId shouldBe 2
    }
  }
}
