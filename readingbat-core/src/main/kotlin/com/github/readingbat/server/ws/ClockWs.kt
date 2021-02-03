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

package com.github.readingbat.server.ws

import com.github.pambrose.common.time.format
import com.github.pambrose.common.util.simpleClassName
import com.github.readingbat.common.Endpoints.CLOCK_ENDPOINT
import com.github.readingbat.common.Endpoints.WS_ROOT
import com.github.readingbat.server.ws.ChallengeWs.PingMessage
import com.github.readingbat.server.ws.WsCommon.closeChannels
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.runBlocking
import mu.KLogging
import java.util.*
import kotlin.collections.LinkedHashSet
import kotlin.concurrent.timer
import kotlin.math.max
import kotlin.time.TimeSource
import kotlin.time.seconds

@Suppress("unused")
internal object ClockWs : KLogging() {
  private val clock = TimeSource.Monotonic
  private val wsConnections = Collections.synchronizedSet(LinkedHashSet<SessionContext>())
  private var maxWsConnections = 0

  private fun assignMaxConnections() {
    synchronized(this) {
      maxWsConnections = max(maxWsConnections, wsConnections.size)
    }
  }

  data class SessionContext(val wsSession: DefaultWebSocketServerSession) {
    val start = clock.markNow()
  }

  init {
    timer("clock msg sender", false, 0L, 1.seconds.toLongMilliseconds()) {
      for (sessionContext in wsConnections)
        try {
          val elapsed = sessionContext.start.elapsedNow().format()
          val json = PingMessage("$elapsed [${wsConnections.size}/$maxWsConnections]").toJson()
          runBlocking {
            sessionContext.wsSession.outgoing.send(Frame.Text(json))
          }
        } catch (e: Throwable) {
          logger.error { "Exception in pinger ${e.simpleClassName} ${e.message}" }
        }
    }
  }

  fun Routing.clockWsEndpoint() {
    webSocket("$WS_ROOT$CLOCK_ENDPOINT") {
      val wsContext = SessionContext(this)
      try {
        outgoing.invokeOnClose {
          incoming.cancel()
        }

        wsConnections += wsContext
        assignMaxConnections()

        incoming
          .consumeAsFlow()
          .mapNotNull { it as? Frame.Text }
          .collect {}
      } finally {
        wsConnections -= wsContext
        closeChannels()
        close(CloseReason(CloseReason.Codes.GOING_AWAY, "Client disconnected"))
        logger.info { "Closed clock websocket: ${wsConnections.size} $maxWsConnections" }
      }
    }
  }
}