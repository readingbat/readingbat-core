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

package com.github.readingbat.config

import io.ktor.application.Application
import io.ktor.application.ApplicationCallPipeline

internal fun Application.intercepts() {
  intercept(ApplicationCallPipeline.Call) {
  }

  intercept(ApplicationCallPipeline.Features) {
    /*
    val origin = call.request.origin
    val uri = origin.uri
    println("uri = $uri")
    if (origin.scheme == "http" && excludePredicates.none { predicate -> predicate(uri) }) {
      val redirectUrl = call.url { protocol = URLProtocol.HTTPS; host = "readingbat.com" }
      println("Redirecting to: $redirectUrl")
      //call.respondRedirect(redirectUrl )
      //finish()
    }
    else {
      println("Ignoring: $uri")
    }
     */
  }

  intercept(ApplicationCallPipeline.Monitoring) {
    // Set up metrics here
  }

  intercept(ApplicationCallPipeline.Fallback) {
    // Count not found pages here
  }
}