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

import com.google.gson.Gson
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.Protocol
import java.net.URI

object RedisPool {
  private var redisURI: URI = URI(System.getenv(EnvVars.REDISTOGO_URL) ?: "redis://user:none@localhost:6379")
  var pool: JedisPool = JedisPool(JedisPoolConfig(),
                                  redisURI.host,
                                  redisURI.port,
                                  Protocol.DEFAULT_TIMEOUT,
                                  redisURI.userInfo.split(Regex(":"), 2)[1])

  fun redisAction(block: (Jedis) -> Unit) {
    pool.resource.use {
      block.invoke(it)
    }
  }

  val gson = Gson()
}

