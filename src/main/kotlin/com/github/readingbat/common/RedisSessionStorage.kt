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

import com.github.pambrose.common.util.isNull
import redis.clients.jedis.Jedis
import kotlin.time.Duration
import kotlin.time.seconds


class RedisSessionStorage(val redis: Jedis,
                          private val prefix: String = "session_",
                          private val ttl: Duration = 3600.seconds) : SimplifiedSessionStorage() {
  private fun buildKey(id: String) = "$prefix$id"

  override suspend fun read(id: String): ByteArray? {
    val key = buildKey(id)
    return try {
      redis[key]?.toByteArray(Charsets.UTF_8)
        .apply {
          redis.expire(key, ttl.inSeconds.toInt()) // refresh
        }
    } catch (e: Exception) {
      e.printStackTrace()
      null
    }
  }

  override suspend fun write(id: String, data: ByteArray?) {
    val key = buildKey(id)
    if (data.isNull()) {
      redis.del(buildKey(id))
    }
    else {
      try {
        redis.set(key, String(data))
        redis.expire(key, ttl.inSeconds.toInt())
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
  }
}
