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

import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.Protocol
import java.net.URI

object RedisUtils {
  private val redisURI by lazy { URI(System.getenv(EnvVars.REDISTOGO_URL) ?: "redis://user:none@localhost:6379") }
  private val colon = Regex(":")
  private val password by lazy { redisURI.userInfo.split(colon, 2)[1] }

  var pool: JedisPool = JedisPool(JedisPoolConfig(),
                                  redisURI.host,
                                  redisURI.port,
                                  Protocol.DEFAULT_TIMEOUT,
                                  password)

  fun withRedisPool(block: (Jedis) -> Unit) {
    pool.resource.use { redis ->
      block.invoke(redis)
    }
  }

  fun withRedis(block: (Jedis) -> Unit) {
    Jedis(redisURI.host,
          redisURI.port,
          Protocol.DEFAULT_TIMEOUT).use { redis ->
      redis.auth(password)
      block.invoke(redis)
    }
  }
}

