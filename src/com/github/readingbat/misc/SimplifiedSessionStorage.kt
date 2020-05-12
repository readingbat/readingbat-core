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

package com.github.readingbat.misc

import io.ktor.sessions.SessionStorage
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.reader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.io.ByteArrayOutputStream
import kotlin.coroutines.coroutineContext

abstract class SimplifiedSessionStorage : SessionStorage {
  abstract suspend fun read(id: String): ByteArray?
  abstract suspend fun write(id: String, data: ByteArray?)

  override suspend fun invalidate(id: String) {
    write(id, null)
  }

  override suspend fun <R> read(id: String, consumer: suspend (ByteReadChannel) -> R): R {
    val data = read(id) ?: throw NoSuchElementException("Session $id not found")
    return consumer(ByteReadChannel(data))
  }

  override suspend fun write(id: String, provider: suspend (ByteWriteChannel) -> Unit) {
    return provider(CoroutineScope(Dispatchers.IO).reader(coroutineContext, autoFlush = true) {
      write(id, channel.readAvailable())
    }.channel)
  }
}

suspend fun ByteReadChannel.readAvailable(): ByteArray {
  val data = ByteArrayOutputStream()
  val temp = ByteArray(1024)
  while (!isClosedForRead) {
    val read = readAvailable(temp, 0, temp.size)
    if (read <= 0) break
    data.write(temp, 0, read)
  }
  return data.toByteArray()
}
