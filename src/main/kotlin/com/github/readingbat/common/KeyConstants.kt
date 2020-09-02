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

import redis.clients.jedis.exceptions.JedisException

internal object KeyConstants {
  const val AUTH_KEY = "auth"
  const val NO_AUTH_KEY = "noauth"
  const val USER_EMAIL_KEY = "user-email"
  const val USER_INFO_KEY = "user-info"
  const val USER_INFO_BROWSER_KEY = "user-info-browser"
  const val USER_CLASSES_KEY = "user-classes"
  const val CORRECT_ANSWERS_KEY = "correct-answers"
  const val LIKE_DISLIKE_KEY = "like-dislike"
  const val CHALLENGE_ANSWERS_KEY = "challenge-answers"
  const val ANSWER_HISTORY_KEY = "answer-history"
  const val RESET_KEY = "password-reset"
  const val USER_RESET_KEY = "user-password-reset"
  const val CLASS_CODE_KEY = "class-code"
  const val CLASS_INFO_KEY = "class-info"
  const val IPGEO_KEY = "ip-geo"

  // For CLASS_INFO_KEY
  const val TEACHER_FIELD = "teacher"
  const val DESC_FIELD = "desc"

  const val KEY_SEP = "|"
}

class DataException(val msg: String) : JedisException(msg)
