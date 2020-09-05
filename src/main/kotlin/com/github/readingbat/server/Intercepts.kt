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

import com.github.pambrose.common.util.isNotNull
import com.github.readingbat.common.Constants.STATIC
import com.github.readingbat.common.SessionActivites.markActivity
import com.github.readingbat.common.browserSession
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.request.*

internal fun Application.intercepts() {
  intercept(ApplicationCallPipeline.Setup) {
    // Phase for preparing call and it's attributes for processing
  }

  intercept(ApplicationCallPipeline.Monitoring) {
    // Phase for tracing calls, useful for logging, metrics, error handling and so on
  }

  intercept(ApplicationCallPipeline.Features) {
    // Phase for features. Most features should intercept this phase
    if (!context.request.path().startsWith("/$STATIC/")) {
      val browserSession = call.browserSession
      //ReadingBatServer.logger.info { "${context.request.origin.remoteHost} $sessionId ${context.request.path()}" }
      if (browserSession.isNotNull())
        browserSession.markActivity(call)
      else
        ReadingBatServer.logger.info { "Null browser sessions for ${call.request.origin.remoteHost}" }
    }
  }

  intercept(ApplicationCallPipeline.Call) {
    // Phase for processing a call and sending a response
  }

  intercept(ApplicationCallPipeline.Fallback) {
    // Phase for handling unprocessed calls
  }
}