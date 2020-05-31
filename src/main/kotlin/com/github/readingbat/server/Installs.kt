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

import com.github.pambrose.common.util.simpleClassName
import com.github.readingbat.config.HerokuHttpsRedirect
import com.github.readingbat.dsl.InvalidPathException
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.Constants.STATIC_ROOT
import com.github.readingbat.misc.Endpoints.CSS_ENDPOINT
import com.github.readingbat.misc.Endpoints.FAV_ICON
import com.github.readingbat.server.ConfigureCookies.configureAuthCookie
import com.github.readingbat.server.ConfigureCookies.configureSessionIdCookie
import com.github.readingbat.server.ConfigureFormAuth.configureFormAuth
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.features.*
import io.ktor.http.ContentType.Text.Plain
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.withCharset
import io.ktor.locations.Locations
import io.ktor.request.path
import io.ktor.response.respond
import io.ktor.server.engine.ShutDownUrl
import io.ktor.sessions.Sessions
import io.ktor.websocket.WebSockets
import mu.KLogging
import org.slf4j.event.Level
import kotlin.text.Charsets.UTF_8

internal object Installs : KLogging() {

  fun Application.installs(content: ReadingBatContent) {

    install(Locations)

    install(Sessions) {
      configureSessionIdCookie()
      configureAuthCookie()
    }

    install(Authentication) {
      //configureSessionAuth()
      configureFormAuth()
    }

    install(WebSockets)

    if (content.production) {
      install(HerokuHttpsRedirect) {
        host = content.siteUrlPrefix.substringAfter("://")
        permanentRedirect = false

        excludePrefix("$STATIC_ROOT/")
        excludeSuffix(CSS_ENDPOINT)
        excludeSuffix(FAV_ICON)
      }
    }

    install(Compression) {
      gzip {
        priority = 1.0
      }
      deflate {
        priority = 10.0
        minimumSize(1024) // condition
      }
    }

    install(CallLogging) {
      level = Level.INFO
      filter { call -> call.request.path().startsWith("/") }
    }

    install(DefaultHeaders) {
      header("X-Engine", "Ktor")
    }

    /*
    install(DropwizardMetrics) {
      val reporter =
        Slf4jReporter.forRegistry(registry)
          .outputTo(log)
          .convertRatesTo(TimeUnit.SECONDS)
          .convertDurationsTo(TimeUnit.MILLISECONDS)
          .build();
      //reporter.start(1, TimeUnit.HOURS);
    }
    */

    install(StatusPages) {
      exception<InvalidPathException> { cause ->
        call.respond(HttpStatusCode.NotFound)
        //call.respondHtml { errorPage(cause.message?:"") }
      }

      //statusFile(HttpStatusCode.NotFound, HttpStatusCode.Unauthorized, filePattern = "error#.html")

      // Catch all
      exception<Throwable> { cause ->
        logger.info(cause) { " Throwable caught: ${cause.simpleClassName}" }
        call.respond(HttpStatusCode.NotFound)
      }

      status(HttpStatusCode.NotFound) {
        call.respond(TextContent("${it.value} ${it.description}", Plain.withCharset(UTF_8), it))
      }
    }

    if (!content.production) {
      install(ShutDownUrl.ApplicationCallFeature) {
        // The URL that will be intercepted (you can also use the application.conf's ktor.deployment.shutdown.url key)
        shutDownUrl = "/ktor/application/shutdown"
        // A function that will be executed to get the exit code of the process
        exitCodeSupplier = { 0 } // ApplicationCall.() -> Int
      }
    }
  }
}