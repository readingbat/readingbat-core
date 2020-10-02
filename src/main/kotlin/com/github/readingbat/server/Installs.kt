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

import com.github.pambrose.common.features.HerokuHttpsRedirect
import com.github.pambrose.common.response.respondWith
import com.github.pambrose.common.util.simpleClassName
import com.github.readingbat.common.Constants.STATIC
import com.github.readingbat.common.Constants.UNKNOWN
import com.github.readingbat.common.Endpoints.CSS_ENDPOINT
import com.github.readingbat.common.Endpoints.FAV_ICON_ENDPOINT
import com.github.readingbat.common.Endpoints.STATIC_ROOT
import com.github.readingbat.common.EnvVar.FILTER_LOG
import com.github.readingbat.dsl.InvalidRequestException
import com.github.readingbat.dsl.RedisUnavailableException
import com.github.readingbat.pages.DbmsDownPage.dbmsDownPage
import com.github.readingbat.pages.ErrorPage.errorPage
import com.github.readingbat.pages.InvalidRequestPage.invalidRequestPage
import com.github.readingbat.pages.NotFoundPage.notFoundPage
import com.github.readingbat.server.ConfigureCookies.configureAuthCookie
import com.github.readingbat.server.ConfigureCookies.configureSessionIdCookie
import com.github.readingbat.server.ConfigureFormAuth.configureFormAuth
import com.github.readingbat.server.ServerUtils.fetchEmail
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.HttpHeaders.Location
import io.ktor.http.HttpStatusCode.Companion.Found
import io.ktor.locations.Locations
import io.ktor.request.*
import io.ktor.server.engine.*
import io.ktor.sessions.*
import io.ktor.websocket.*
import mu.KLogging
import org.slf4j.event.Level

internal object Installs : KLogging() {

  fun Application.installs(production: Boolean,
                           redirectHostname: String,
                           forwardedHeaderSupportEnabled: Boolean,
                           xforwardedHeaderSupportEnabled: Boolean) {

    install(Locations)

    install(Sessions) {
      configureSessionIdCookie()
      configureAuthCookie()
    }

    install(Authentication) {
      //configureSessionAuth()
      configureFormAuth()
    }

    install(WebSockets) {
      pingPeriodMillis = 5000L   // Duration between pings or `0` to disable pings
    }

    if (forwardedHeaderSupportEnabled) {
      logger.info { "Enabling ForwardedHeaderSupport" }
      install(ForwardedHeaderSupport)
    }
    else {
      logger.info { "Not enabling ForwardedHeaderSupport" }
    }

    if (xforwardedHeaderSupportEnabled) {
      logger.info { "Enabling XForwardedHeaderSupport" }
      install(XForwardedHeaderSupport)
    }
    else {
      logger.info { "Not enabling XForwardedHeaderSupport" }
    }

    if (production && redirectHostname.isNotBlank()) {
      logger.info { "Installing HerokuHttpsRedirect using: $redirectHostname" }
      install(HerokuHttpsRedirect) {
        host = redirectHostname
        permanentRedirect = false

        excludePrefix("$STATIC_ROOT/")
        excludeSuffix(CSS_ENDPOINT)
        excludeSuffix(FAV_ICON_ENDPOINT)
      }
    }
    else {
      logger.info { "Not installing HerokuHttpsRedirect" }
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
      if (FILTER_LOG.getEnv(true))
        filter { call ->
          call.request.path().let { it.startsWith("/") && !it.startsWith("/$STATIC/") && it != "/ping" }
        }

      format { call ->
        val request = call.request
        val response = call.response
        val logStr = request.toLogString()
        val remote = request.origin.remoteHost
        val email = call.fetchEmail()

        when (val status = response.status() ?: HttpStatusCode(-1, "Unknown")) {
          Found -> "Redirect: $logStr -> ${response.headers[Location]} - $remote - $email"
          else -> "$status: $logStr - $remote - $email"
        }
      }
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

      exception<InvalidRequestException> { cause ->
        logger.info { " InvalidRequestException caught: ${cause.message}" }
        respondWith {
          invalidRequestPage(ReadingBatServer.content.get(), call.request.uri, cause.message ?: UNKNOWN)
        }
      }

      exception<RedisUnavailableException> { cause ->
        logger.info(cause) { " RedisUnavailableException caught: ${cause.message}" }
        respondWith {
          dbmsDownPage(ReadingBatServer.content.get())
        }
      }

      // Catch all
      exception<Throwable> { cause ->
        logger.info(cause) { " Throwable caught: ${cause.simpleClassName}" }
        respondWith {
          errorPage(ReadingBatServer.content.get())
        }
      }

      status(HttpStatusCode.NotFound) {
        //call.respond(TextContent("${it.value} ${it.description}", Plain.withCharset(UTF_8), it))
        respondWith {
          notFoundPage(ReadingBatServer.content.get(), call.request.uri.replaceAfter("?", "").replace("?", ""))
        }
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
}