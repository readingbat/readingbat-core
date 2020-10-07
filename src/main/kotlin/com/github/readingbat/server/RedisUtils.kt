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

import redis.clients.jedis.Jedis
import redis.clients.jedis.ScanParams

object RedisUtils {

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
}