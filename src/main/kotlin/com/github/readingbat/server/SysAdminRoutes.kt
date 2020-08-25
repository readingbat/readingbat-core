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

import com.github.pambrose.common.response.redirectTo
import com.github.pambrose.common.response.respondWith
import com.github.readingbat.common.Constants.MSG
import com.github.readingbat.common.Constants.RETURN_PATH
import com.github.readingbat.common.Endpoints.GARBAGE_COLLECTOR_ENDPOINT
import com.github.readingbat.common.Endpoints.MESSAGE_ENDPOINT
import com.github.readingbat.common.Endpoints.RESET_CACHE_ENDPOINT
import com.github.readingbat.common.Endpoints.RESET_CONTENT_ENDPOINT
import com.github.readingbat.common.Endpoints.SYSTEM_ADMIN_ENDPOINT
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.pages.MessagePage.messagePage
import com.github.readingbat.server.ServerUtils.authenticatedAction
import com.github.readingbat.server.ServerUtils.get
import io.ktor.routing.*
import kotlin.time.measureTime

internal fun Routing.sysAdminRoutes(metrics: Metrics,
                                    contentSrc: () -> ReadingBatContent,
                                    resetContentFunc: () -> Unit) {


  get(RESET_CONTENT_ENDPOINT, metrics) {
    val msg =
      authenticatedAction {
        measureTime { resetContentFunc.invoke() }.let {
          "Content reset in $it".also { ReadingBatServer.logger.info { it } }
        }
      }
    redirectTo { "$MESSAGE_ENDPOINT?$MSG=$msg&$RETURN_PATH=$SYSTEM_ADMIN_ENDPOINT" }
  }

  get(RESET_CACHE_ENDPOINT, metrics) {
    val msg =
      authenticatedAction {
        val content = contentSrc()
        val cnt = content.sourcesMap.size
        content.clearSourcesMap().let {
          "Challenge cache reset -- $cnt challenges removed".also { ReadingBatServer.logger.info { it } }
        }
      }
    redirectTo { "$MESSAGE_ENDPOINT?$MSG=$msg&$RETURN_PATH=$SYSTEM_ADMIN_ENDPOINT" }
  }

  get(GARBAGE_COLLECTOR_ENDPOINT, metrics) {
    val msg =
      authenticatedAction {
        System.gc()
        "Garbage collector invoked".also { ReadingBatServer.logger.info { it } }
      }
    redirectTo { "$MESSAGE_ENDPOINT?$MSG=$msg&$RETURN_PATH=$SYSTEM_ADMIN_ENDPOINT" }
  }

  get(MESSAGE_ENDPOINT, metrics) {
    respondWith { messagePage(contentSrc()) }
  }
}