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

import com.github.readingbat.misc.BrowserSession
import mu.KLogging
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.schedule
import kotlin.time.Duration
import kotlin.time.milliseconds
import kotlin.time.seconds

internal class Activity(val browserSession: BrowserSession) {
  val lastActivity = AtomicReference(System.currentTimeMillis())

  val age get() = (System.currentTimeMillis() - lastActivity.get()).milliseconds

  fun update() {
    println("Calling update on $browserSession")
    lastActivity.set(System.currentTimeMillis())
  }
}

internal object SessionActivity : KLogging() {

  private val activityMap = ConcurrentHashMap<String, Activity>()
  private val timer = Timer()

  init {
    timer.schedule(10.seconds.toLongMilliseconds(), 10.seconds.toLongMilliseconds()) {
      logger.info { "Running SessionActivity cleanup" }
      activityMap.entries
        .filter { it.value.age > 15.seconds }
        .forEach {
          logger.info { "Removing stale browser session ${it.key} after ${it.value.age}" }
          activityMap.remove(it.key)
        }
    }
  }

  fun markActivity(browserSession: BrowserSession) {
    activityMap.getOrPut(browserSession.id, { Activity((browserSession)) }).update()
  }

  fun activeSessions(maxAge: Duration) =
    activityMap
      .filter { it.value.age <= maxAge }
      .size
}