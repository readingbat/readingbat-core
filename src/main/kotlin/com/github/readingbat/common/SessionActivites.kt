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

package com.github.readingbat.common

import com.github.pambrose.common.dsl.KtorDsl.get
import com.github.pambrose.common.dsl.KtorDsl.httpClient
import com.github.pambrose.common.util.isInt
import com.github.readingbat.common.Constants.UNKNOWN
import com.github.readingbat.common.EnvVar.IPGEOLOCATION_KEY
import com.github.readingbat.common.SessionActivites.RemoteHost.Companion.unknown
import com.github.readingbat.common.User.Companion.toUser
import com.github.readingbat.dsl.InvalidConfigurationException
import com.github.readingbat.dsl.isPostgresEnabled
import com.github.readingbat.server.GeoInfos
import com.github.readingbat.server.geoInfosUnique
import com.github.readingbat.server.get
import com.github.readingbat.server.upsert
import com.google.gson.Gson
import io.ktor.application.*
import io.ktor.client.statement.*
import io.ktor.features.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import mu.KLogging
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.timer
import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlin.time.hours

internal object SessionActivites : KLogging() {

  val gson = Gson()

  class RemoteHost(val remoteHost: String) {
    val city get() = geoInfoMap[remoteHost]?.takeIf { it.valid }?.city?.toString() ?: UNKNOWN
    val state get() = geoInfoMap[remoteHost]?.takeIf { it.valid }?.state_prov?.toString() ?: UNKNOWN
    val country get() = geoInfoMap[remoteHost]?.takeIf { it.valid }?.country_name?.toString() ?: UNKNOWN
    val organization get() = geoInfoMap[remoteHost]?.takeIf { it.valid }?.organization?.toString() ?: UNKNOWN
    val flagUrl get() = geoInfoMap[remoteHost]?.takeIf { it.valid }?.country_flag?.toString() ?: UNKNOWN

    fun isIpAddress() = remoteHost.split(".").run { size == 4 && all { it.isInt() } }

    override fun toString() = remoteHost

    internal companion object {
      val unknown = RemoteHost(UNKNOWN)
    }
  }

  class Session(val browserSession: BrowserSession, val userAgent: String) {
    private var lastUpdate = TimeSource.Monotonic.markNow()
    private val pages = AtomicInteger(0)

    var remoteHost: RemoteHost = unknown
    var principal: UserPrincipal? = null

    val user by lazy { principal?.userId?.let { toUser(it, browserSession) } }
    val age get() = lastUpdate.elapsedNow()
    val requests get() = pages.get()

    fun update(recentPrincipal: UserPrincipal?, recentRemoteHost: RemoteHost) {
      principal = recentPrincipal
      remoteHost = recentRemoteHost
      lastUpdate = TimeSource.Monotonic.markNow()
      pages.incrementAndGet()
    }
  }

  class GeoInfo(val remoteHost: String, val json: String) {
    val valid get() = json.isNotBlank()

    //private val map = if (json.isNotBlank()) Json.decodeFromString<Map<String, Any?>>(json) else emptyMap()
    private val map = if (json.isNotBlank()) gson.fromJson(json, Map::class.java) as Map<String, Any?> else emptyMap()

    val ip by map
    val continent_code by map
    val continent_name by map
    val country_code2 by map
    val country_code3 by map
    val country_name by map
    val country_capital by map
    val district by map
    val city by map
    val state_prov by map
    val zipcode by map
    val latitude by map
    val longitude by map
    val is_eu by map
    val calling_code by map
    val country_tld by map
    val country_flag by map
    val isp by map
    val connection_type by map
    val organization by map
    val time_zone by map

    fun summary() = if (valid) listOf(city, state_prov, country_name, organization).joinToString(", ") else UNKNOWN

    fun save() {
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
  }

  private val delay = 1.hours
  private val period = 1.hours
  private val timeOutAge = 24.hours
  val sessionsMap = ConcurrentHashMap<String, Session>()
  val geoInfoMap = ConcurrentHashMap<String, GeoInfo>()

  init {
    timer("stale session admin", true, delay.toLongMilliseconds(), period.toLongMilliseconds()) {
      try {
        val staleCnt =
          sessionsMap.entries
            .filter { it.value.age > timeOutAge }
            .onEach {
              logger.info { "Removing stale browser session ${it.key} after ${it.value.age}" }
              sessionsMap.remove(it.key)
            }
            .count()

        val probeCnt =
          sessionsMap.entries
            .filter { it.value.requests == 1 }
            .onEach {
              logger.debug { "Removing probe browser session ${it.key}" }
              sessionsMap.remove(it.key)
            }
            .count()

        logger.info { "Running session cleanup - sessions over $timeOutAge: $staleCnt, probe sessionss: $probeCnt" }
      } catch (e: Throwable) {
        logger.error(e) { "Exception when removing stale browser sessions" }
      }
    }
  }

  private fun fetchGeoInfo(ipAddress: String) =
    runBlocking {
      httpClient { client ->
        val apiKey = IPGEOLOCATION_KEY.getRequiredEnv()
        client.get("https://api.ipgeolocation.io/ipgeo?apiKey=$apiKey&ip=$ipAddress") { response ->
          val json = response.readText()
          GeoInfo(ipAddress, json).apply { logger.info { "API GEO info for $ipAddress: ${summary()}" } }
        }
      }
    }

  fun queryGeoDbmsIdByIpAddress(ipAddress: String) =
    GeoInfos
      .slice(GeoInfos.id)
      .select { GeoInfos.ip eq ipAddress }
      .map { it[GeoInfos.id].value }
      .firstOrNull() ?: throw InvalidConfigurationException("Missing ip address: $ipAddress")

  fun BrowserSession.markActivity(source: String, call: ApplicationCall) {

    val principal = call.userPrincipal
    val remoteHost = RemoteHost(call.request.origin.remoteHost)

    logger.debug { "Marking activity for $source $remoteHost" }

    // Use https://ipgeolocation.io/documentation/user-agent-api.html to parse userAgent data
    val userAgent = call.request.headers[HttpHeaders.UserAgent] ?: UNKNOWN

    // Update session
    sessionsMap.getOrPut(id, { Session(this, userAgent) }).update(principal, remoteHost)

    if (isPostgresEnabled() && IPGEOLOCATION_KEY.isDefined()) {
      geoInfoMap.computeIfAbsent(remoteHost.remoteHost) { ipAddress ->
        transaction {
          GeoInfos
            .slice(GeoInfos.json)
            .select { GeoInfos.ip eq ipAddress }
            .map { GeoInfo(ipAddress, it[0] as String) }
            .firstOrNull()
            ?.also { logger.info { "Postgres GEO info for $ipAddress: ${it.summary()}" } }
            ?: try {
              fetchGeoInfo(ipAddress)
            } catch (e: Throwable) {
              GeoInfo(ipAddress, "")
                .also { logger.info { "Unable to determine IP geolocation data for ${it.remoteHost} (${e.message})" } }
            }.also { it.save() }
        }
      }
    }
  }

  fun activeSessions(duration: Duration) =
    sessionsMap.filter { it.value.requests > 1 && it.value.age <= duration }.size

  fun allSessions(): List<Session> = sessionsMap.map { it.value }.toList()
}