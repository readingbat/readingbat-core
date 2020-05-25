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
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.Endpoints.CLASSROOM
import io.ktor.http.cio.websocket.CloseReason
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
import redis.clients.jedis.JedisPubSub

internal object WsEndoints {

  fun Routing.wsEndpoints(content: ReadingBatContent) {

    webSocket(CLASSROOM) {

      incoming
        .receiveAsFlow()
        .mapNotNull { it as? Frame.Text }
        .collect { frame ->
          val text = frame.readText()

          repeat(10) { i ->
            delay(100)
            outgoing.send(Frame.Text("" + i))
          }

          var i = 0
          withRedis { redis ->
            redis?.subscribe(object : JedisPubSub() {
              override fun onMessage(channel: String?, message: String?) {
                runBlocking {
                  delay(100)
                  outgoing.send(Frame.Text("$channel $message ${i++}"))
                }
              }
            }, "channel")
          }

          if (text.equals("bye", ignoreCase = true)) {
            close(CloseReason(CloseReason.Codes.NORMAL, "Client said BYE"))
          }
        }
    }
  }
}