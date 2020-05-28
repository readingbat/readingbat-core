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

import com.github.pambrose.common.redis.RedisUtils.withRedis
import com.github.readingbat.misc.Endpoints.CLASS_PREFIX
import io.ktor.http.cio.websocket.CloseReason
import io.ktor.http.cio.websocket.CloseReason.Codes
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.close
import io.ktor.http.cio.websocket.readText
import io.ktor.routing.Routing
import io.ktor.websocket.webSocket
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import mu.KLogging
import redis.clients.jedis.JedisPubSub

internal object WsEndoints : KLogging() {

  fun Routing.wsEndpoints() {
    webSocket("$CLASS_PREFIX/{classCode}") {
      val classCode = call.parameters["classCode"]
      incoming
        .receiveAsFlow()
        .mapNotNull { it as? Frame.Text }
        .collect { frame ->
          val inboundMsg = frame.readText()

          /*
          repeat(10) { i ->
            delay(100)
            outgoing.send(Frame.Text("" + i))
          }
           */

          withRedis { redis ->
            redis?.subscribe(object : JedisPubSub() {
              override fun onMessage(channel: String?, message: String?) {
                if (message != null)
                  runBlocking {
                    delay(100)
                    logger.debug { "Sending data $message to $channel" }
                    outgoing.send(Frame.Text(message))
                  }
              }
            }, classCode)
          }

          if (inboundMsg.equals("bye", ignoreCase = true)) {
            close(CloseReason(Codes.NORMAL, "Client said BYE"))
          }
        }
    }
  }
}