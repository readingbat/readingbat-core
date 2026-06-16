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

import com.pambrose.common.dsl.KtorDsl.get
import com.pambrose.common.dsl.KtorDsl.withHttpClient
import com.pambrose.common.exposed.get
import com.pambrose.common.exposed.readonlyTx
import com.pambrose.common.exposed.upsert
import com.readingbat.common.Constants
import com.readingbat.common.EnvVar
import com.readingbat.server.GeoInfo.Companion.MAX_GEO_CACHE_SIZE
import com.readingbat.server.GeoInfo.Companion.lookupGeoInfo
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * IP geolocation data fetched from the ipgeolocation.io API.
 *
 * Instances are cached in-memory by IP address and persisted to [GeoInfosTable]. When
 * [requireDbmsLookUp] is true, the data has not yet been stored in the database and
 * needs to be inserted. The [json] field holds the raw API response, and individual
 * geographic fields are lazily delegated from the parsed JSON map.
 */
class GeoInfo(val requireDbmsLookUp: Boolean, val dbmsId: Long, val remoteHost: String, val json: String) {
  /** True when the entry carries real geo data; false for an Unknown/failure placeholder (blank json). */
  val valid = json.isNotBlank()

  private val map: Map<String, Any?> =
    if (valid)
      Json.decodeFromString<JsonObject>(json).mapValues { (_, v) ->
        if (v is JsonPrimitive) v.content else v.toString()
      }
    else
      emptyMap()

  @Suppress("unused")
  private val ip by map
  private val continent_code by map
  private val continent_name by map
  private val country_code2 by map
  private val country_code3 by map
  private val country_name by map
  private val country_capital by map
  private val district by map
  private val city by map
  private val state_prov by map
  private val zipcode by map
  private val latitude by map
  private val longitude by map
  private val is_eu by map
  private val calling_code by map
  private val country_tld by map
  private val country_flag by map
  private val isp by map
  private val connection_type by map
  private val organization by map
  private val time_zone by map

  fun summary() =
    if (valid)
      try {
        listOf(city, state_prov, country_name, organization).joinToString(", ")
      } catch (_: NoSuchElementException) {
        "Missing Geo data"
      }
    else
      Constants.UNKNOWN

  fun mapVal(block: () -> String) =
    try {
      block()
    } catch (e: NoSuchElementException) {
      logger.warn { e.message }
      "Unknown"
    }

  fun insert() {
    transaction {
      with(GeoInfosTable) {
        upsert(conflictIndex = geoInfosUnique) { row ->
          row[ip] = remoteHost
          row[json] = this@GeoInfo.json

          if (this@GeoInfo.valid) {
            row[continentCode] = mapVal { this@GeoInfo.continent_code.toString() }
            row[continentName] = mapVal { this@GeoInfo.continent_name.toString() }
            row[countryCode2] = mapVal { this@GeoInfo.country_code2.toString() }
            row[countryCode3] = mapVal { this@GeoInfo.country_code3.toString() }
            row[countryName] = mapVal { this@GeoInfo.country_name.toString() }
            row[countryCapital] = mapVal { this@GeoInfo.country_capital.toString() }
            row[district] = mapVal { this@GeoInfo.district.toString() }
            row[city] = mapVal { this@GeoInfo.city.toString() }
            row[stateProv] = mapVal { this@GeoInfo.state_prov.toString() }
            row[zipcode] = mapVal { this@GeoInfo.zipcode.toString() }
            row[latitude] = mapVal { this@GeoInfo.latitude.toString() }
            row[longitude] = mapVal { this@GeoInfo.longitude.toString() }
            row[isEu] = mapVal { this@GeoInfo.is_eu.toString() }
            row[callingCode] = mapVal { this@GeoInfo.calling_code.toString() }
            row[countryTld] = mapVal { this@GeoInfo.country_tld.toString() }
            row[countryFlag] = mapVal { this@GeoInfo.country_flag.toString() }
            row[isp] = mapVal { this@GeoInfo.isp.toString() }
            row[connectionType] = mapVal { this@GeoInfo.connection_type.toString() }
            row[organization] = mapVal { this@GeoInfo.organization.toString() }
            row[timeZone] = mapVal { this@GeoInfo.time_zone.toString() }
          }
        }
      }
    }
  }

  override fun toString() = map.toString()

  companion object {
    private val logger = KotlinLogging.logger {}

    /**
     * Upper bound on the number of cached IP geolocation entries. The client-IP key space on a
     * public-facing server is unbounded, so the cache is capped to avoid unbounded memory growth.
     */
    const val MAX_GEO_CACHE_SIZE = 10_000

    /**
     * In-memory geo cache keyed by client IP, bounded to [MAX_GEO_CACHE_SIZE] with LRU eviction
     * (access-ordered [LinkedHashMap]). Wrapped in a synchronized map because it is read and
     * written from concurrent request-handling coroutines.
     */
    val geoInfoMap: MutableMap<String, GeoInfo> =
      Collections.synchronizedMap(
        object : LinkedHashMap<String, GeoInfo>(16, 0.75f, true) {
          override fun removeEldestEntry(eldest: Map.Entry<String, GeoInfo>) = size > MAX_GEO_CACHE_SIZE
        },
      )
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Per-IP in-flight lookups: concurrent requests for the same IP share one Deferred.
    private val inflight = ConcurrentHashMap<String, Deferred<GeoInfo>>()
    private val httpClient =
      HttpClient(CIO) {
        // A hung ipgeolocation.io endpoint must not stall the request-logging path indefinitely.
        install(HttpTimeout) {
          requestTimeoutMillis = 10_000
          connectTimeoutMillis = 5_000
          socketTimeoutMillis = 10_000
        }
      }

    private suspend fun callGeoInfoApi(ipAddress: String) =
      withHttpClient(httpClient) {
        val apiKey = EnvVar.IPGEOLOCATION_KEY.getRequiredEnv()
        get("https://api.ipgeolocation.io/ipgeo?apiKey=$apiKey&ip=$ipAddress") { response ->
          val json = response.bodyAsText()
          GeoInfo(true, -1, ipAddress, json).apply {
            logger.debug { "API GEO info for $ipAddress: ${summary()}" }
          }
        }
      }

    fun queryGeoInfo(ipAddress: String) =
      readonlyTx {
        with(GeoInfosTable) {
          select(id, json)
            .where { ip eq ipAddress }
            .map { GeoInfo(false, it[id].value, ipAddress, it[1] as String) }
            .firstOrNull()
        }
      }

    /**
     * Resolves geo info for [ipAddress] from Postgres, else the ipgeolocation.io API, always
     * leaving a persisted row behind so the request-logging foreign key (`server_requests.geo_ref`)
     * can resolve.
     *
     * - An existing **valid** Postgres row is authoritative and returned as-is.
     * - Otherwise (no row, or only a prior blank/failure placeholder) the API is (re)tried. On
     *   success the row is upserted and re-read (carrying the persisted id, requireDbmsLookUp=false).
     * - On API failure a blank Unknown placeholder is persisted (so the FK resolves) but is left
     *   **invalid** (blank json); [lookupGeoInfo] only caches valid entries, so the failure is not
     *   poisoned into the in-memory cache and the next request retries the API to upgrade the row.
     */
    private suspend fun resolveGeoInfo(ipAddress: String): GeoInfo {
      val existing = queryGeoInfo(ipAddress)
      if (existing != null && existing.valid) {
        logger.debug { "Postgres GEO info for $ipAddress: ${existing.summary()}" }
        return existing
      }

      return runCatching { callGeoInfoApi(ipAddress) }
        .map { built ->
          built.insert()
          queryGeoInfo(ipAddress) ?: built
        }
        .getOrElse { e ->
          logger.info { "Unable to determine IP geolocation data for $ipAddress (${e.message})" }
          // Reuse a prior placeholder row if present; otherwise persist one so the geo_ref FK can
          // resolve. Either way the returned entry is invalid (blank), so it is not cached.
          existing ?: GeoInfo(true, -1, ipAddress, "").also { it.insert() }.let { queryGeoInfo(ipAddress) ?: it }
        }
    }

    /**
     * Looks up geolocation for [ipAddress], checking the in-memory cache first, then PostgreSQL,
     * and finally the ipgeolocation.io API as a last resort.
     *
     * Concurrent lookups for the same IP share one in-flight request (per-IP coalescing) while
     * different IPs resolve in parallel, so a slow/hung API call for one IP no longer head-of-line
     * blocks geo resolution for every other request — unlike the previous single process-wide lock.
     */
    suspend fun lookupGeoInfo(ipAddress: String): GeoInfo =
      geoInfoMap[ipAddress] ?: run {
        val deferred =
          inflight.computeIfAbsent(ipAddress) {
            scope.async {
              resolveGeoInfo(ipAddress).also {
                // Cache only valid entries. A blank Unknown placeholder (failure) is left uncached
                // so the next request retries the API and can upgrade the persisted row.
                if (it.valid) geoInfoMap[ipAddress] = it
              }
            }
          }
        try {
          deferred.await()
        } finally {
          inflight.remove(ipAddress, deferred)
        }
      }
  }
}
