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

import com.readingbat.withTestApp
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Tests that lookupGeoInfo caches an entry that no longer requires a per-request DB lookup.
 * Previously the API and failure branches cached `GeoInfo(requireDbmsLookUp = true, dbmsId = -1)`,
 * so the request-logging path re-ran queryGeoInfo against Postgres on every subsequent request for
 * that IP. After inserting, the entry must be re-read so the cached value carries the persisted id
 * and requireDbmsLookUp = false.
 */
class GeoCacheTest : StringSpec() {
  init {
    "lookupGeoInfo caches an entry that no longer requires a DB lookup" {
      withTestApp {
        val ip = "198.51.100.77"
        // Force the API/failure branch by clearing any cached or persisted entry for this IP.
        GeoInfo.geoInfoMap.remove(ip)
        transaction { GeoInfosTable.deleteWhere { GeoInfosTable.ip eq ip } }

        val geo = GeoInfo.lookupGeoInfo(ip)

        geo.requireDbmsLookUp shouldBe false
        geo.dbmsId shouldBeGreaterThan 0
        GeoInfo.geoInfoMap[ip]?.requireDbmsLookUp shouldBe false
      }
    }

    "concurrent lookups for the same IP coalesce into one consistent result" {
      withTestApp {
        val ip = "203.0.113.55"
        GeoInfo.geoInfoMap.remove(ip)
        transaction { GeoInfosTable.deleteWhere { GeoInfosTable.ip eq ip } }

        val results = coroutineScope { (1..50).map { async { GeoInfo.lookupGeoInfo(ip) } }.awaitAll() }

        // All concurrent lookups resolve to the same persisted entry without deadlock or error.
        results.map { it.dbmsId }.toSet() shouldHaveSize 1
        results.all { !it.requireDbmsLookUp } shouldBe true
        GeoInfo.geoInfoMap[ip]?.requireDbmsLookUp shouldBe false
      }
    }
  }
}
