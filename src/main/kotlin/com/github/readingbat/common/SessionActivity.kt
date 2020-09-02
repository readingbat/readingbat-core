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
import com.github.readingbat.common.EnvVars.IPGEOLOCATION_KEY
import com.github.readingbat.common.User.Companion.gson
import com.github.readingbat.common.User.Companion.toUser
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking
import mu.KLogging
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.schedule
import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlin.time.hours

internal object SessionActivity : KLogging() {

  class Session(val browserSession: BrowserSession) {
    private var lastUpdate = TimeSource.Monotonic.markNow()
    private val pages = AtomicInteger(0)
    var remoteHost: String = "unknown"
    var userAgent: String = "unknown"
    var principal: UserPrincipal? = null
    val age get() = lastUpdate.elapsedNow()
    val user by lazy { principal?.userId?.toUser(browserSession) }

    val requests get() = pages.get()

    val city get() = geoInfoMap[remoteHost]?.city?.toString() ?: "Unknown"
    val country get() = geoInfoMap[remoteHost]?.country_name?.toString() ?: "Unknown"
    val organization get() = geoInfoMap[remoteHost]?.organization?.toString() ?: "Unknown"
    val flagUrl get() = geoInfoMap[remoteHost]?.country_flag?.toString() ?: "Unknown"

    fun update(recentPrincipal: UserPrincipal?, remote: String, agent: String) {
      principal = recentPrincipal
      lastUpdate = TimeSource.Monotonic.markNow()
      remoteHost = remote
      userAgent = agent
      pages.incrementAndGet()
    }
  }

  class GeoInfo(private val map: Map<String, Any?>) {

    val ip by map
    val hostname by map
    val continent_code by map
    val continent_name by map
    val country_code2 by map
    val country_code3 by map
    val country_name by map
    val country_capital by map
    val state_prov by map
    val district by map
    val city by map
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

    fun summary() = listOf(ip, hostname, city, country_name, organization).joinToString(", ")

    override fun toString() = map.toString()
  }

  private val delay = 1.hours
  private val period = 1.hours
  private val timeOutAge = 24.hours

  private val sessionsMap = ConcurrentHashMap<String, Session>()
  private val geoInfoMap = ConcurrentHashMap<String, GeoInfo>()

  private val timer = Timer()

  private val ignoreHosts = mutableListOf("localhost", "0.0.0.0", "127.0.0.1")

  val sessionsMapSize get() = sessionsMap.size

  init {
    timer.schedule(delay.toLongMilliseconds(), period.toLongMilliseconds()) {
      logger.info { "Running session activity cleanup for sessions over $timeOutAge" }
      try {
        sessionsMap.entries
          .filter { it.value.age > timeOutAge }
          .forEach {
            logger.info { "Removing stale browser session ${it.key} after ${it.value.age}" }
            sessionsMap.remove(it.key)
          }
      } catch (e: Throwable) {
        logger.error(e) { "Exception when removing stale browser sessions" }
      }
    }
  }

  fun markActivity(browserSession: BrowserSession, principal: UserPrincipal?, remoteHost: String, userAgent: String) {
    sessionsMap.getOrPut(browserSession.id, { Session(browserSession) }).update(principal, remoteHost, userAgent)

    try {
      if (IPGEOLOCATION_KEY.isDefined() && remoteHost !in ignoreHosts) {
        geoInfoMap.computeIfAbsent(remoteHost) { ipAddress ->
          runBlocking {
            httpClient { client ->
              val geoKey = IPGEOLOCATION_KEY.getRequiredEnv()
              client.get("https://api.ipgeolocation.io/ipgeo?apiKey=$geoKey&ip=$ipAddress") { response ->
                val body = response.readText()
                val map = gson.fromJson(body, Map::class.java) as Map<String, Any>
                GeoInfo(map).apply {
                  logger.info { "IP info for $ipAddress: ${summary()}" }
                }
              }
            }
          }
        }
      }
    } catch (e: Throwable) {
      logger.warn { "Unable to determine IP geolocation data for $remoteHost" }
      ignoreHosts += remoteHost
    }
  }

  fun activeSessions(duration: Duration) = sessionsMap.filter { it.value.age <= duration }.size

  fun allSessions(): List<Session> = sessionsMap.map { it.value }.toList()
}