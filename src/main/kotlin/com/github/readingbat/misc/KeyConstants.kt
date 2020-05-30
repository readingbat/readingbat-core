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

import com.github.readingbat.misc.Constants.DBMS_DOWN
import redis.clients.jedis.exceptions.JedisException

internal object KeyConstants {
  const val USER_EMAIL_KEY = "userId"
  const val USER_INFO_KEY = "user-info"
  const val USER_CLASSES_KEY = "user-classes"
  const val CORRECT_ANSWERS_KEY = "correct-answers"
  const val CHALLENGE_ANSWERS_KEY = "challenge-answers"
  const val ANSWER_HISTORY_KEY = "answer-history"
  const val RESET_KEY = "password-reset"
  const val USERID_RESET_KEY = "userid_password-reset"
  const val AUTH_KEY = "auth"
  const val NO_AUTH_KEY = "noauth"
  const val CLASS_CODE_KEY = "class-code"
  const val CLASS_DESC_KEY = "class-desc"

  const val EMAIL_FIELD = "email"
  const val SALT_FIELD = "salt"
  const val DIGEST_FIELD = "digest"
  const val NAME_FIELD = "name"

  // This is the class code you will report results to
  const val ENROLLED_CLASS_CODE_FIELD = "enrolled-class-code"

  // This is the class code you will observe update on
  const val ACTIVE_CLASS_CODE_FIELD = "active-class-code"

  const val KEY_SEP = "|"
}

class RedisDownException : JedisException(DBMS_DOWN)

class DataException(msg: String) : JedisException(msg)
