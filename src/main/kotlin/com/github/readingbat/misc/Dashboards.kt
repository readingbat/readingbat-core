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

import com.github.pambrose.common.redis.RedisUtils.withRedisPool
import com.github.readingbat.misc.RedisConstants.CLASS_CODE_KEY
import com.github.readingbat.misc.RedisConstants.KEY_SEP

internal object Dashboards {

  fun classCodeEnrollmentKey(classCode: String) = listOf(CLASS_CODE_KEY, classCode).joinToString(KEY_SEP)

  fun UserId.enrollIntoClass(classCode: String) {
    if (classCode.isBlank()) {
      throw DataException("Empty class code")
    }
    else {
      val classCodeEnrollmentKey = classCodeEnrollmentKey(classCode)
      withRedisPool { redis ->
        when {
          redis == null -> throw RedisDownException()
          redis.smembers(classCodeEnrollmentKey) == null -> throw DataException("Invalid class code $classCode")
          redis.sismember(classCodeEnrollmentKey, id) -> throw DataException("Already joined class $classCode")
          else -> redis.sadd(classCodeEnrollmentKey, id)
        }
      }
    }
  }
}