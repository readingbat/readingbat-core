/*
 * Copyright © 2023 Paul Ambrose (pambrose@mac.com)
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
import com.github.readingbat.common.Endpoints.PING_ENDPOINT
import com.github.readingbat.common.Endpoints.STATIC_ROOT
import com.github.readingbat.common.Endpoints.WS_ROOT
import com.github.readingbat.common.EnvVar
import com.github.readingbat.common.Property
import com.github.readingbat.dsl.InvalidRequestException
import com.github.readingbat.dsl.isDbmsEnabled
import com.github.readingbat.pages.ErrorPage.errorPage
import com.github.readingbat.pages.InvalidRequestPage.invalidRequestPage
import com.github.readingbat.pages.NotFoundPage.notFoundPage
import com.github.readingbat.server.ConfigureCookies.configureAuthCookie
import com.github.readingbat.server.ConfigureCookies.configureSessionIdCookie
import com.github.readingbat.server.ConfigureFormAuth.configureFormAuth
import com.github.readingbat.server.Email.Companion.UNKNOWN_EMAIL
import com.github.readingbat.server.ReadingBatServer.serverSessionId
import com.github.readingbat.server.ServerUtils.fetchEmailFromCache
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpHeaders.Location
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.Found
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.engine.ShutDownUrl
import io.ktor.server.logging.toLogString
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.deflate
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.compression.minimumSize
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.plugins.forwardedheaders.ForwardedHeaders
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.request.uri
import io.ktor.server.resources.Resources
import io.ktor.server.sessions.Sessions
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import org.slf4j.event.Level
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.time.Duration.Companion.seconds

object Installs {
  private val logger = KotlinLogging.logger {}
  private val excludedEndpoints = listOf("/", "/$STATIC/", "$WS_ROOT/")

  fun Application.installs(production: Boolean) {
    install(Resources)

    install(Sessions) {
      configureSessionIdCookie()
      configureAuthCookie()
    }

    install(Authentication) {
      // configureSessionAuth()
      configureFormAuth()
    }

    install(WebSockets) {
      pingPeriod = 15.seconds  // Duration between pings or `0` to disable pings
      timeout = 15.seconds
    }

    val forwardedHeaderSupportEnabled =
      EnvVar.FORWARDED_ENABLED.getEnv(Property.FORWARDED_ENABLED.configValue(this, default = "false").toBoolean())
    if (forwardedHeaderSupportEnabled) {
      logger.info { "Enabling ForwardedHeaderSupport" }
      install(ForwardedHeaders)
    } else {
      logger.info { "Not enabling ForwardedHeaderSupport" }
    }

    val xforwardedHeaderSupportEnabled =
      EnvVar.XFORWARDED_ENABLED.getEnv(Property.XFORWARDED_ENABLED.configValue(this, default = "false").toBoolean())
    if (xforwardedHeaderSupportEnabled) {
      logger.info { "Enabling XForwardedHeaderSupport" }
      install(XForwardedHeaders)
    } else {
      logger.info { "Not enabling XForwardedHeaderSupport" }
    }

    val redirectHostname = EnvVar.REDIRECT_HOSTNAME.getEnv(Property.REDIRECT_HOSTNAME.configValue(this, default = ""))
    if (production && redirectHostname.isNotBlank()) {
      logger.info { "Installing HerokuHttpsRedirect using: $redirectHostname" }
      install(HerokuHttpsRedirect) {
        host = redirectHostname
        permanentRedirect = false

        excludePrefix("$STATIC_ROOT/")
        excludeSuffix(CSS_ENDPOINT)
        excludeSuffix(FAV_ICON_ENDPOINT)
      }
    } else {
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

    install(RateLimit) {
      register {
        rateLimiter(
          limit = EnvVar.RATE_LIMIT_COUNT.getEnv(10),
          refillPeriod = EnvVar.RATE_LIMIT_SECS.getEnv(1).seconds,
        )
      }
    }

    fun String.startsWithList(prefixes: Iterable<String>) = prefixes.any { startsWith(it) }

    install(CallLogging) {
      level = Level.INFO

      if (EnvVar.FILTER_LOG.getEnv(true))
        filter { call ->
          call.request.path()
            .run {
              this != PING_ENDPOINT &&
                !startsWithList(excludedEndpoints) &&
                !endsWith(".php") &&
                !endsWith("error.log") &&
                !endsWith("error.txt")
            }
        }

      format { call ->
        val request = call.request
        val response = call.response
        val logStr = request.toLogString()
        val remote = request.origin.remoteHost
        val email = if (isDbmsEnabled()) call.fetchEmailFromCache() else UNKNOWN_EMAIL

        when (val status = response.status() ?: HttpStatusCode(-1, "Unknown")) {
          Found -> "Redirect: $logStr -> ${response.headers[Location]} - $remote - $email"
          else -> "$status: $logStr - $remote - $email"
        }
      }
    }

    install(DefaultHeaders) {
      header("X-Engine", "Ktor")
    }

    val requestCounter = AtomicLong(0)

    install(CallId) {
      retrieveFromHeader(HttpHeaders.XRequestId)
      generate { "$serverSessionId-${requestCounter.incrementAndFetch()}" }
      verify { it.isNotEmpty() }
    }

//    install(KHealth) {
//      readyChecks {
//        check("check my database is up") { true }
//      }
//
//      healthChecks {
//        check("another check") { true }
//      }
//    }

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
      exception<Throwable> { call, cause ->
        when (cause) {
          is InvalidRequestException -> {
            logger.info { "InvalidRequestException caught: ${cause.message}" }
            call.respondWith { invalidRequestPage(call.request.uri, cause.message ?: UNKNOWN) }
          }

          is IllegalStateException -> {
            logger.info(cause) { "IllegalStateException caught: ${cause.message}" }
            call.respondWith { errorPage() }
          }

          else -> {
            logger.warn(cause) { "Throwable caught: ${cause.simpleClassName}" }
            call.respondWith { errorPage() }
          }
        }
      }

      status(HttpStatusCode.NotFound) { call, _ ->
        // call.respond(TextContent("${it.value} ${it.description}", Plain.withCharset(UTF_8), it))
        call.respondWith { notFoundPage(call.request.uri.replaceAfter("?", "").replace("?", "")) }
      }
    }

    if (!production) {
      install(ShutDownUrl.ApplicationCallPlugin) {
        // The URL that will be intercepted (you can also use the application.conf's ktor.deployment.shutdown.url key)
        shutDownUrl = "/ktor/application/shutdown"
        // A function that will be executed to get the exit code of the process
        exitCodeSupplier = { 0 } // ApplicationCall.() -> Int
      }
    }
  }
}
