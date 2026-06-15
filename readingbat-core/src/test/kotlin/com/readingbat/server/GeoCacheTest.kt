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

import com.readingbat.common.Constants
import com.readingbat.server.GeoInfo.Companion.geoInfoMap
import com.readingbat.server.GeoInfo.Companion.lookupGeoInfo
import com.readingbat.server.GeoInfo.Companion.queryGeoInfo
import com.readingbat.withTestApp
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Tests the geo-cache resolution semantics:
 *  - A DB-resolved entry is cached with requireDbmsLookUp = false (the persisted id), so the
 *    request-logging path does not re-run queryGeoInfo against Postgres on every request.
 *  - A transient API failure persists a blank Unknown placeholder row (so the request-logging
 *    geo_ref FK can resolve) but is left out of the in-memory cache, so the failure is not
 *    permanently poisoned and the next request retries the API to upgrade the row.
 *
 * The failure tests rely on the API call failing in CI (no working IPGEOLOCATION_KEY / blocked
 * network egress), the same assumption the previous version of this test depended on.
 */
class GeoCacheTest : StringSpec() {
  init {
    "lookupGeoInfo caches a DB-resolved entry that no longer requires a DB lookup" {
      withTestApp {
        val ip = "198.51.100.77"
        geoInfoMap.remove(ip)
        transaction { GeoInfosTable.deleteWhere { GeoInfosTable.ip eq ip } }

        // Seed a valid persisted entry, as if a prior successful API lookup stored it.
        val json =
          """{"ip":"$ip","city":"Testville","state_prov":"TS","country_name":"Testland","organization":"Test Org"}"""
        GeoInfo(true, -1, ip, json).insert()

        val geo = lookupGeoInfo(ip)

        geo.requireDbmsLookUp shouldBe false
        geo.dbmsId shouldBeGreaterThan 0
        geoInfoMap[ip]?.requireDbmsLookUp shouldBe false
      }
    }

    "a transient API failure persists a placeholder for the FK but is not cached, so it can be retried" {
      withTestApp {
        val ip = "203.0.113.55"
        geoInfoMap.remove(ip)
        transaction { GeoInfosTable.deleteWhere { GeoInfosTable.ip eq ip } }

        val geo = lookupGeoInfo(ip)

        // The API call failed: an Unknown placeholder is persisted so server_requests.geo_ref can
        // resolve, but it is left out of the in-memory cache so the next request retries the API.
        geo.summary() shouldBe Constants.UNKNOWN
        geoInfoMap[ip].shouldBeNull()
        queryGeoInfo(ip)?.summary() shouldBe Constants.UNKNOWN
      }
    }

    "concurrent lookups for the same IP coalesce into one consistent result" {
      withTestApp {
        val ip = "203.0.113.56"
        geoInfoMap.remove(ip)
        transaction { GeoInfosTable.deleteWhere { GeoInfosTable.ip eq ip } }

        val results = coroutineScope { (1..50).map { async { lookupGeoInfo(ip) } }.awaitAll() }

        // All concurrent lookups resolve to the same (coalesced) outcome without deadlock or error.
        results.map { it.requireDbmsLookUp }.toSet() shouldHaveSize 1
      }
    }
  }
}
