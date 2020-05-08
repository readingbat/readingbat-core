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

import com.codahale.metrics.Slf4jReporter
import com.github.readingbat.InvalidPathException
import com.github.readingbat.misc.Constants.production
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.application.log
import io.ktor.features.*
import io.ktor.http.ContentType.Text.Plain
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.withCharset
import io.ktor.locations.Locations
import io.ktor.metrics.dropwizard.DropwizardMetrics
import io.ktor.request.path
import io.ktor.response.respond
import io.ktor.server.engine.ShutDownUrl
import mu.KotlinLogging
import org.slf4j.event.Level
import java.util.concurrent.TimeUnit
import kotlin.text.Charsets.UTF_8

private val logger = KotlinLogging.logger {}

internal fun Application.installs() {

  install(Compression) {
    gzip {
      priority = 1.0
    }
    deflate {
      priority = 10.0
      minimumSize(1024) // condition
    }
  }

  install(Locations)

  install(CallLogging) {
    level = Level.INFO
    filter { call -> call.request.path().startsWith("/") }
  }

  install(DefaultHeaders) {
    header("X-Engine", "Ktor")
  }

  install(DropwizardMetrics) {
    val reporter =
      Slf4jReporter.forRegistry(registry)
        .outputTo(log)
        .convertRatesTo(TimeUnit.SECONDS)
        .convertDurationsTo(TimeUnit.MILLISECONDS)
        .build();
    //reporter.start(1, TimeUnit.HOURS);
  }

  install(StatusPages) {
    exception<InvalidPathException> { cause ->
      call.respond(HttpStatusCode.NotFound)
      //call.respondHtml { errorPage(cause.message?:"") }
    }

    //statusFile(HttpStatusCode.NotFound, HttpStatusCode.Unauthorized, filePattern = "error#.html")

    status(HttpStatusCode.NotFound) {
      call.respond(TextContent("${it.value} ${it.description}", Plain.withCharset(UTF_8), it))
    }

    // Catch all
    exception<Throwable> { cause ->
      logger.info(cause) {}
      call.respond(HttpStatusCode.NotFound)
    }
  }

  if (!production) {
    install(ShutDownUrl.ApplicationCallFeature) {
      // The URL that will be intercepted (you can also use the application.conf's ktor.deployment.shutdown.url key)
      shutDownUrl = "/ktor/application/shutdown"
      // A function that will be executed to get the exit code of the process
      exitCodeSupplier = { 0 } // ApplicationCall.() -> Int
    }
  }
}