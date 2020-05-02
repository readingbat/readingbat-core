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

import com.github.readingbat.Constants.checkAnswers
import com.github.readingbat.Constants.cssName
import com.github.readingbat.Constants.static
import com.github.readingbat.dsl.LanguageType
import com.github.readingbat.misc.CheckAnswers.checkUserAnswers
import com.github.readingbat.misc.cssContent
import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.content.resources
import io.ktor.http.content.static
import io.ktor.response.respondRedirect
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import kotlinx.css.CSSBuilder

internal fun Application.routes() {
  routing {
    get("/") {
      call.respondRedirect("/${LanguageType.Java.lowerName}")
    }

    get("/$cssName") {
      call.respondCss {
        cssContent()
      }
    }

    post("/$checkAnswers") {
      checkUserAnswers()
    }

    static("/$static") {
      resources(static)
    }
  }
}

private suspend inline fun ApplicationCall.respondCss(builder: CSSBuilder.() -> Unit) {
  respondText(CSSBuilder().apply(builder).toString(), ContentType.Text.CSS)
}