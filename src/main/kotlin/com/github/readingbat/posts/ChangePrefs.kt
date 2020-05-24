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

package com.github.readingbat.posts

import com.github.pambrose.common.redis.RedisUtils.withRedisPool
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.Constants.RETURN_PATH
import com.github.readingbat.misc.FormFields
import com.github.readingbat.misc.FormFields.PREF_ACTION
import com.github.readingbat.misc.FormFields.UPDATE_PASSWORD
import com.github.readingbat.misc.UserPrincipal
import com.github.readingbat.pages.requestLogInPage
import io.ktor.http.Parameters
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

internal fun changePrefs(content: ReadingBatContent, parameters: Parameters, principal: UserPrincipal?): String {
  val returnPath = parameters[RETURN_PATH] ?: "/"

  logger.debug { "Return path = $returnPath" }

  return withRedisPool { redis ->
    val userId = lookupUserId(redis, principal)
    logger.info { "UserId: $userId" }

    if (userId == null) {
      requestLogInPage(content, returnPath, principal)
    }
    else {
      val action = parameters[PREF_ACTION] ?: ""
      if (action == UPDATE_PASSWORD) {

        val currPassword = parameters[FormFields.CURR_PASSWORD] ?: ""
        val newPassword = parameters[FormFields.NEW_PASSWORD] ?: ""
        logger.info { "Curr: $currPassword New: $newPassword Action: $action" }

      }
      ""
    }
  }
}

