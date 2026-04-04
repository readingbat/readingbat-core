/*
 * Copyright © 2026 Paul Ambrose (pambrose@mac.com)
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

package com.readingbat.server

import com.pambrose.common.exposed.get
import com.pambrose.common.exposed.readonlyTx
import com.readingbat.common.User
import com.readingbat.dsl.isDbmsEnabled
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select

/**
 * Service for querying whether a user has correctly completed a challenge.
 *
 * Looks up the `all_correct` flag in [UserChallengeInfoTable] for a given user and
 * challenge MD5 hash. Returns false when the DBMS is disabled or the user is null.
 */
internal object ChallengeProgressService {
  /** Returns true if [user] has answered all invocations of the challenge identified by [challengeMd5] correctly. */
  fun isCorrect(user: User?, challengeMd5: String): Boolean =
    when {
      !isDbmsEnabled() || user == null -> {
        false
      }

      else -> {
        readonlyTx {
          with(UserChallengeInfoTable) {
            select(allCorrect)
              .where { (userRef eq user.userDbmsId) and (md5 eq challengeMd5) }
              .map { it[0] as Boolean }
              .firstOrNull() ?: false
          }
        }
      }
    }
}
