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
import com.github.readingbat.common.EnvVar.FORWARDED_ENABLED
import com.github.readingbat.common.EnvVar.REDIRECT_HOSTNAME
import com.github.readingbat.common.EnvVar.XFORWARDED_ENABLED
import com.github.readingbat.common.Property.FORWARDED_ENABLED_PROPERTY
import com.github.readingbat.common.Property.REDIRECT_HOSTNAME_PROPERTY
import com.github.readingbat.common.Property.XFORWARDED_ENABLED_PROPERTY
import com.github.readingbat.dsl.InvalidRequestException
import com.github.readingbat.dsl.RedisUnavailableException
import com.github.readingbat.dsl.isPostgresEnabled
import com.github.readingbat.pages.DbmsDownPage.dbmsDownPage
import com.github.readingbat.pages.ErrorPage.errorPage
import com.github.readingbat.pages.InvalidRequestPage.invalidRequestPage
import com.github.readingbat.pages.NotFoundPage.notFoundPage
import com.github.readingbat.server.ConfigureCookies.configureAuthCookie
import com.github.readingbat.server.ConfigureCookies.configureSessionIdCookie
import com.github.readingbat.server.ConfigureFormAuth.configureFormAuth
import com.github.readingbat.server.ReadingBatServer.serverSessionId
import com.github.readingbat.server.ServerUtils.fetchEmailFromCache
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.HttpHeaders.Location
import io.ktor.http.HttpStatusCode.Companion.Found
import io.ktor.http.cio.websocket.*
import io.ktor.locations.Locations
import io.ktor.request.*
import io.ktor.server.engine.*
import io.ktor.sessions.*
import io.ktor.websocket.*
import mu.KLogging
import org.slf4j.event.Level
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

internal object Installs : KLogging() {

  fun Application.installs(production: Boolean) {

    install(Locations)

    install(Sessions) {
      configureSessionIdCookie()
      configureAuthCookie()
    }

    install(Authentication) {
      //configureSessionAuth()
      configureFormAuth()
    }

    val install = install(WebSockets) {
      pingPeriod = Duration.ofSeconds(15)  // Duration between pings or `0` to disable pings
      timeout = Duration.ofSeconds(15)
    }

    val forwardedHeaderSupportEnabled =
      FORWARDED_ENABLED.getEnv(FORWARDED_ENABLED_PROPERTY.configValue(this, default = "false").toBoolean())
    if (forwardedHeaderSupportEnabled) {
      logger.info { "Enabling ForwardedHeaderSupport" }
      install(ForwardedHeaderSupport)
    }
    else {
      logger.info { "Not enabling ForwardedHeaderSupport" }
    }

    val xforwardedHeaderSupportEnabled =
      XFORWARDED_ENABLED.getEnv(XFORWARDED_ENABLED_PROPERTY.configValue(this, default = "false").toBoolean())
    if (xforwardedHeaderSupportEnabled) {
      logger.info { "Enabling XForwardedHeaderSupport" }
      install(XForwardedHeaderSupport)
    }
    else {
      logger.info { "Not enabling XForwardedHeaderSupport" }
    }

    val redirectHostname = REDIRECT_HOSTNAME.getEnv(REDIRECT_HOSTNAME_PROPERTY.configValue(this, default = ""))
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
        val email = if (isPostgresEnabled()) call.fetchEmailFromCache() else Email.UNKNOWN_EMAIL

        when (val status = response.status() ?: HttpStatusCode(-1, "Unknown")) {
          Found -> "Redirect: $logStr -> ${response.headers[Location]} - $remote - $email"
          else -> "$status: $logStr - $remote - $email"
        }
      }
    }

    install(DefaultHeaders) {
      header("X-Engine", "Ktor")
    }

    val requestCounter = AtomicLong()

    install(CallId) {
      retrieveFromHeader(HttpHeaders.XRequestId)
      generate { "$serverSessionId-${requestCounter.incrementAndGet()}" }
      verify { it.isNotEmpty() }
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
        logger.info { "InvalidRequestException caught: ${cause.message}" }
        respondWith {
          invalidRequestPage(ReadingBatServer.content.get(), call.request.uri, cause.message ?: UNKNOWN)
        }
      }

      exception<IllegalStateException> { cause ->
        logger.info { "IllegalStateException caught: ${cause.message}" }
        respondWith {
          errorPage(ReadingBatServer.content.get())
        }
      }

      exception<RedisUnavailableException> { cause ->
        logger.info(cause) { "RedisUnavailableException caught: ${cause.message}" }
        respondWith {
          dbmsDownPage(ReadingBatServer.content.get())
        }
      }

      // Catch all
      exception<Throwable> { cause ->
        logger.info(cause) { "Throwable caught: ${cause.simpleClassName}" }
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