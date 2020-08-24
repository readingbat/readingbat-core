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
import com.github.readingbat.misc.BrowserSession
import com.github.readingbat.misc.Constants.STATIC
import com.github.readingbat.misc.UserPrincipal
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.sessions.*

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
      val sessionId = call.sessions.get<BrowserSession>()
      val principal = call.sessions.get<UserPrincipal>()
      //ReadingBatServer.logger.info { "${context.request.local.remoteHost} $sessionId ${context.request.path()}" }
      if (sessionId.isNotNull())
        SessionActivity.markActivity(sessionId,
                                     principal,
                                     call.request.local.remoteHost,
                                     call.request.headers[HttpHeaders.UserAgent] ?: "unknown")
    }
  }

  intercept(ApplicationCallPipeline.Call) {
    // Phase for processing a call and sending a response
  }

  intercept(ApplicationCallPipeline.Fallback) {
    // Phase for handling unprocessed calls
  }
}