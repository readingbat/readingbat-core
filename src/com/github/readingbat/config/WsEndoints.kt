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

package com.github.readingbat.config

import com.github.readingbat.dsl.ReadingBatContent
import io.ktor.http.cio.websocket.CloseReason
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.close
import io.ktor.http.cio.websocket.readText
import io.ktor.routing.Routing
import io.ktor.websocket.webSocket
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

internal fun Routing.wsEndpoints(content: ReadingBatContent) {

  webSocket("/ws") {

    fun foo(): Flow<Int> =
      flow { // flow builder
        repeat(1000) { i ->
          delay(100)
          emit(i)
        }
      }

    incoming
      .receiveAsFlow()
      .mapNotNull { it as? Frame.Text }
      .collect { frame ->
        val text = frame.readText()

        foo().collect { value ->
          outgoing.send(Frame.Text("Count $value"))
        }

        if (text.equals("bye", ignoreCase = true)) {
          close(CloseReason(CloseReason.Codes.NORMAL, "Client said BYE"))
        }
      }
  }

}