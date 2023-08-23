/*
 * Copyright © 2023 Paul Ambrose (pambrose@mac.com)
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

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking

class Limiter(private val maxConcurrencySize: Int = 1) {

  @Suppress("unused")
  class Token(private val limiter: Limiter)

  private val channel = Channel<Token>(maxConcurrencySize)

  init {
    runBlocking {
      repeat(maxConcurrencySize) { channel.send(Token(this@Limiter)) }
    }
  }

  suspend fun request(func: suspend () -> Unit) {
    val token = channel.receive()
    try {
      func.invoke()
    } finally {
      channel.send(token)
    }
  }
}