/*
 * Copyright © 2020 Paul Ambrose (pambrose@mac.com)
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

package com.github.readingbat.server

import com.github.pambrose.common.dsl.KtorDsl
import com.github.pambrose.common.dsl.KtorDsl.get
import com.github.readingbat.common.Constants
import com.github.readingbat.common.EnvVar.IPGEOLOCATION_KEY
import com.google.gson.Gson
import com.pambrose.common.exposed.get
import com.pambrose.common.exposed.upsert
import io.ktor.application.*
import io.ktor.client.statement.*
import io.ktor.features.*
import kotlinx.coroutines.runBlocking
import mu.KLogging
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.concurrent.ConcurrentHashMap

internal class GeoInfo(val requireDbmsLookUp: Boolean, val dbmsId: Long, val remoteHost: String, val json: String) {
  private val valid get() = json.isNotBlank()

  //private val map = if (json.isNotBlank()) Json.decodeFromString<Map<String, Any?>>(json) else emptyMap()
  private val map = if (json.isNotBlank()) gson.fromJson(json, Map::class.java) as Map<String, Any?> else emptyMap()

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
    if (valid) listOf(city, state_prov, country_name, organization).joinToString(", ") else Constants.UNKNOWN

  fun insert() {
    transaction {
      GeoInfos
        .upsert(conflictIndex = geoInfosUnique) { row ->
          row[ip] = remoteHost
          row[json] = this@GeoInfo.json

          if (this@GeoInfo.valid) {
            row[continentCode] = this@GeoInfo.continent_code.toString()
            row[continentName] = this@GeoInfo.continent_name.toString()
            row[countryCode2] = this@GeoInfo.country_code2.toString()
            row[countryCode3] = this@GeoInfo.country_code3.toString()
            row[countryName] = this@GeoInfo.country_name.toString()
            row[countryCapital] = this@GeoInfo.country_capital.toString()
            row[district] = this@GeoInfo.district.toString()
            row[city] = this@GeoInfo.city.toString()
            row[stateProv] = this@GeoInfo.state_prov.toString()
            row[zipcode] = this@GeoInfo.zipcode.toString()
            row[latitude] = this@GeoInfo.latitude.toString()
            row[longitude] = this@GeoInfo.longitude.toString()
            row[isEu] = this@GeoInfo.is_eu.toString()
            row[callingCode] = this@GeoInfo.calling_code.toString()
            row[countryTld] = this@GeoInfo.country_tld.toString()
            row[countryFlag] = this@GeoInfo.country_flag.toString()
            row[isp] = this@GeoInfo.isp.toString()
            row[connectionType] = this@GeoInfo.connection_type.toString()
            row[organization] = this@GeoInfo.organization.toString()
            row[timeZone] = this@GeoInfo.time_zone.toString()
          }
        }
    }
  }

  override fun toString() = map.toString()

  companion object : KLogging() {
    val gson = Gson()
    val geoInfoMap = ConcurrentHashMap<String, GeoInfo>()

    private fun callGeoInfoApi(ipAddress: String) =
      runBlocking {
        KtorDsl.httpClient { client ->
          val apiKey = IPGEOLOCATION_KEY.getRequiredEnv()
          client.get("https://api.ipgeolocation.io/ipgeo?apiKey=$apiKey&ip=$ipAddress") { response ->
            val json = response.readText()
            GeoInfo(true, -1, ipAddress, json).apply { logger.info { "API GEO info for $ipAddress: ${summary()}" } }
          }
        }
      }

    fun queryGeoInfo(ipAddress: String) =
      transaction {
        GeoInfos
          .slice(GeoInfos.id, GeoInfos.json)
          .select { GeoInfos.ip eq ipAddress }
          .map { GeoInfo(false, it[GeoInfos.id].value, ipAddress, it[1] as String) }
          .firstOrNull()
      }

    fun lookupGeoInfo(ipAddress: String) =
      geoInfoMap.computeIfAbsent(ipAddress) { ip ->
        queryGeoInfo(ip)?.apply { logger.info { "Postgres GEO info for $ip: ${summary()}" } }
          ?: try {
            callGeoInfoApi(ip)
          } catch (e: Throwable) {
            GeoInfo(true, -1, ip, "")
              .also { logger.info { "Unable to determine IP geolocation data for ${it.remoteHost} (${e.message})" } }
          }.also { it.insert() }
      }
  }
}