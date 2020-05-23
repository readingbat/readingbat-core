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

import com.github.readingbat.PipelineCall
import com.github.readingbat.config.fetchPrincipal
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.Constants.RETURN_PATH
import com.github.readingbat.misc.FormFields
import com.github.readingbat.misc.RedisUtils
import io.ktor.application.call
import io.ktor.request.receiveParameters
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}


internal suspend fun PipelineCall.changePrefs(content: ReadingBatContent) {
  val parameters = call.receiveParameters()
  val username = parameters[FormFields.USERNAME] ?: ""
  val password = parameters[FormFields.PASSWORD] ?: ""
  val returnPath = parameters[RETURN_PATH] ?: "/"
  logger.debug { "Return path = $returnPath" }

  RedisUtils.withRedisPool { redis ->
    val principal = fetchPrincipal()
    val userId = lookupUserId(redis, principal)

    logger.info { "UserId: $userId" }

  }
}

