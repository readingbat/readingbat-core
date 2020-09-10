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

import com.github.pambrose.common.redis.RedisUtils.withRedis
import com.github.pambrose.common.util.isNotNull
import com.github.readingbat.dsl.InvalidConfigurationException
import redis.clients.jedis.Jedis
import redis.clients.jedis.ScanParams
import redis.clients.jedis.exceptions.JedisDataException

internal object RedisAdmin {

  private const val docean = ""
  private const val heroku = ""
  private const val local = "redis://user:none@localhost:6379"

  @JvmStatic
  fun main(args: Array<String>) {
    showAll(local)
    //deleteAll(docean)
    //copy(heroku, docean)
    //println("Heroku count: ${count(heroku)}")
    //println("DO count: ${count(docean)}")
    //count(docean)
  }

  private fun copy(urlFrom: String, urlTo: String) {
    var cnt = 0
    withRedis(urlFrom) { redisFrom ->
      require(redisFrom != null)
      val allKeys = redisFrom.scanKeys("*")
      withRedis(urlTo) { redisTo ->
        require(redisTo != null)
        allKeys.forEach { key ->
          val dump = redisFrom.dump(key)
          println("($cnt) Transferring key: $key - $dump")
          redisTo.restore(key, 0, dump)
          cnt++
        }
      }
    }
  }

  private fun count(url: String) =
    withRedis(url) { redis ->
      redis?.scanKeys("*")?.count() ?: throw InvalidConfigurationException("No connection")
    }

  fun Jedis.scanKeys(pattern: String, count: Int = 100): Sequence<String> =
    sequence {
      val scanParams = ScanParams().match(pattern).count(count)
      var cursorVal = "0"
      while (true) {
        cursorVal =
          scan(cursorVal, scanParams).run {
            result.forEach { yield(it) }
            cursor
          }
        if (cursorVal == "0")
          break
      }
    }

  fun deleteAll(url: String) {
    withRedis(url) { redis ->
      redis?.scanKeys("*")?.forEach { redis.del(it) }
    }
  }

  private fun showAll(url: String) {
    withRedis(url) { redis ->
      if (redis.isNotNull()) {
        println(
          redis.scanKeys("*")
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