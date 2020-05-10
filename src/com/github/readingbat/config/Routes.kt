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

import com.codahale.metrics.jvm.ThreadDump
import com.github.readingbat.config.ThreadDumpInfo.threadDump
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.CheckAnswers.checkUserAnswers
import com.github.readingbat.misc.Constants.checkAnswers
import com.github.readingbat.misc.Constants.cssName
import com.github.readingbat.misc.Constants.icons
import com.github.readingbat.misc.Constants.root
import com.github.readingbat.misc.Constants.staticRoot
import com.github.readingbat.misc.cssContent
import com.github.readingbat.pages.defaultTab
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
import java.io.ByteArrayOutputStream
import java.lang.management.ManagementFactory


internal fun Application.routes(readingBatContent: ReadingBatContent) {

  routing {
    get("/") {
      call.respondRedirect(defaultTab(readingBatContent))
    }

    get("/$root") {
      call.respondRedirect(defaultTab(readingBatContent))
    }

    get("/favicon.ico") {
      call.respondRedirect("/$staticRoot/$icons/favicon.ico")
    }

    get("/$cssName") {
      call.respondCss {
        cssContent()
      }
    }

    post("/$checkAnswers") {
      checkUserAnswers(readingBatContent)
    }

    get("/ping") {
      call.respondText { "pong" }
    }

    get("/threaddump") {
      try {
        val baos = ByteArrayOutputStream()
        baos.use { threadDump.dump(true, true, it) }
        val output = String(baos.toByteArray(), Charsets.UTF_8)
        call.respondText { output }
      } catch (e: NoClassDefFoundError) {
        call.respondText { "Sorry, your runtime environment does not allow dump threads." }
      }
    }

    static("/$staticRoot") {
      resources(staticRoot)
    }
  }
}

private object ThreadDumpInfo {
  internal val threadDump by lazy { ThreadDump(ManagementFactory.getThreadMXBean()) }
}

private suspend inline fun ApplicationCall.respondCss(builder: CSSBuilder.() -> Unit) {
  respondText(CSSBuilder().apply(builder).toString(), ContentType.Text.CSS)
}