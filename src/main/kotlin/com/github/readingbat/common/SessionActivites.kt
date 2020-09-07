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
import com.github.pambrose.common.redis.RedisUtils.withRedisPool
import com.github.pambrose.common.util.isInt
import com.github.pambrose.common.util.isNotNull
import com.github.readingbat.common.EnvVars.IPGEOLOCATION_KEY
import com.github.readingbat.common.KeyConstants.IPGEO_KEY
import com.github.readingbat.common.SessionActivites.RemoteHost.Companion.unknown
import com.github.readingbat.common.User.Companion.gson
import com.github.readingbat.common.User.Companion.toUser
import com.github.readingbat.server.ReadingBatServer.redisPool
import com.github.readingbat.server.keyOf
import io.ktor.application.*
import io.ktor.client.statement.*
import io.ktor.features.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import mu.KLogging
import redis.clients.jedis.params.SetParams
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.schedule
import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlin.time.days
import kotlin.time.hours

internal object SessionActivites : KLogging() {

  class RemoteHost(val value: String) {
    val city get() = geoInfoMap[value]?.city?.toString() ?: "Unknown"
    val state get() = geoInfoMap[value]?.state_prov?.toString() ?: "Unknown"
    val country get() = geoInfoMap[value]?.country_name?.toString() ?: "Unknown"
    val organization get() = geoInfoMap[value]?.organization?.toString() ?: "Unknown"
    val flagUrl get() = geoInfoMap[value]?.country_flag?.toString() ?: "Unknown"

    fun isIpAddress() = value.split(".").run { size == 4 && all { it.isInt() } }

    override fun toString() = value

    internal companion object {
      val unknown = RemoteHost("unknown")
    }
  }

  internal class Session(val browserSession: BrowserSession, val userAgent: String) {
    private var lastUpdate = TimeSource.Monotonic.markNow()
    private val pages = AtomicInteger(0)

    var remoteHost: RemoteHost = unknown
    var principal: UserPrincipal? = null

    val user by lazy { principal?.userId?.toUser(browserSession) }
    val age get() = lastUpdate.elapsedNow()
    val requests get() = pages.get()

    fun update(recentPrincipal: UserPrincipal?, recentRemoteHost: RemoteHost) {
      principal = recentPrincipal
      remoteHost = recentRemoteHost
      lastUpdate = TimeSource.Monotonic.markNow()
      pages.incrementAndGet()
    }
  }

  class GeoInfo(private val map: Map<String, Any?>) {

    constructor(json: String) : this(gson.fromJson(json, Map::class.java) as Map<String, Any?>)

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

    fun summary() = listOf(city, state_prov, country_name, organization).joinToString(", ")

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

        sessionsMap.entries
          .filter { it.value.requests == 1 }
          .forEach {
            logger.info { "Removing probe browser session ${it.key}" }
            sessionsMap.remove(it.key)
          }
      } catch (e: Throwable) {
        logger.error(e) { "Exception when removing stale browser sessions" }
      }
    }
  }

  fun BrowserSession.markActivity(call: ApplicationCall) {

    val principal = call.userPrincipal
    val remoteHost = RemoteHost(call.request.origin.remoteHost)

    // Use https://ipgeolocation.io/documentation/user-agent-api.html to parse userAgent data
    val userAgent = call.request.headers[HttpHeaders.UserAgent] ?: "unknown"

    // Update session
    sessionsMap.getOrPut(id, { Session(this, userAgent) }).update(principal, remoteHost)

    fun lookUpGeoInfo(ipAddress: String) =
      runBlocking {
        httpClient { client ->
          val apiKey = IPGEOLOCATION_KEY.getRequiredEnv()
          client.get("https://api.ipgeolocation.io/ipgeo?apiKey=$apiKey&ip=$ipAddress") { response ->
            val json = response.readText()
            json to GeoInfo(json).apply {
              logger.info { "API GEO info for $ipAddress: ${summary()}" }
            }
          }
        }
      }

    if (!remoteHost.isIpAddress()) {
      logger.debug { "Skipped looking up $remoteHost" }
      return
    }

    try {
      if (IPGEOLOCATION_KEY.isDefined() && remoteHost !in ignoreHosts && !geoInfoMap.containsKey(remoteHost)) {
        redisPool.withRedisPool { redis ->
          if (redis.isNotNull()) {
            geoInfoMap.computeIfAbsent(remoteHost.value) { ipAddress ->
              val geoKey = keyOf(IPGEO_KEY, remoteHost)
              val json = redis.get(geoKey) ?: ""
              if (json.isNotBlank())
                GeoInfo(json).apply { logger.info { "Redis GEO info for $remoteHost: ${summary()}" } }
              else
                lookUpGeoInfo(ipAddress)
                  .let {
                    redis.set(geoKey, it.first, SetParams().ex(7.days.inSeconds.toInt()))
                    it.second
                  }
            }
          }
        }
      }
    } catch (e: Throwable) {
      logger.warn { "Unable to determine IP geolocation data for $remoteHost (${e.message})" }
      ignoreHosts += remoteHost.value
    }
  }

  fun activeSessions(duration: Duration) = sessionsMap.filter { it.value.requests > 1 && it.value.age <= duration }.size

  fun allSessions(): List<Session> = sessionsMap.map { it.value }.toList()
}