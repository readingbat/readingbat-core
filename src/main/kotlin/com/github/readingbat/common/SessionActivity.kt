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

import com.github.readingbat.common.User.Companion.toUser
import mu.KLogging
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.schedule
import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlin.time.hours

internal object SessionActivity : KLogging() {

  // Activity is overkill, but we might want to eventually do something with browserSession value
  class Session(val browserSession: BrowserSession) {
    private var lastUpdate = TimeSource.Monotonic.markNow()
    var remoteHost: String = "unknown"
    var userAgent: String = "unknown"
    var principal: UserPrincipal? = null
    val age get() = lastUpdate.elapsedNow()
    val user by lazy { principal?.userId?.toUser(browserSession) }

    fun update(recentPrincipal: UserPrincipal?, remote: String, agent: String) {
      principal = recentPrincipal
      lastUpdate = TimeSource.Monotonic.markNow()
      remoteHost = remote
      userAgent = agent
    }
  }

  private val delay = 1.hours
  private val period = 1.hours
  private val timeOutAge = 2.hours

  private val sessionsMap = ConcurrentHashMap<String, Session>()
  private val timer = Timer()

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
  }

  fun activeSessions(duration: Duration) = sessionsMap.filter { it.value.age <= duration }.size

  fun allSessions(): List<Session> = sessionsMap.map { it.value }.toList()
}