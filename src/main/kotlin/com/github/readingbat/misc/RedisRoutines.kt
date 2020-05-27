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

import com.github.pambrose.common.redis.RedisUtils.withRedis
import redis.clients.jedis.exceptions.JedisDataException

object RedisRoutines {

  @JvmStatic
  fun main(args: Array<String>) {
    showAllKeys()
    //deleteAllKeys()
  }

  internal fun deleteAllKeys() {
    withRedis { redis ->
      redis?.keys("*")?.forEach { redis.del(it) }
    }
  }

  internal fun showAllKeys() {
    withRedis { redis ->
      if (redis != null) {
        println(redis.keys("*")
                  .joinToString("\n") {
                    try {
                      "$it - ${redis[it]}"
                    } catch (e: JedisDataException) {
                      try {
                        "$it - ${redis.hgetAll(it)}"
                      } catch (e: JedisDataException) {
                        "$it - ${redis.smembers(it)}"
                      }
                    }
                  })
      }
    }
  }
}