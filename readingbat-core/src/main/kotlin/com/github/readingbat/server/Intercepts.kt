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

import com.github.pambrose.common.util.isNotNull
import com.github.pambrose.common.util.maxLength
import com.github.readingbat.common.BrowserSession.Companion.findOrCreateSessionDbmsId
import com.github.readingbat.common.Constants.STATIC
import com.github.readingbat.common.Constants.UNKNOWN_USER_ID
import com.github.readingbat.common.Endpoints.ABOUT_ENDPOINT
import com.github.readingbat.common.Endpoints.CHALLENGE_ROOT
import com.github.readingbat.common.Endpoints.HELP_ENDPOINT
import com.github.readingbat.common.Endpoints.OAUTH_CALLBACK_GITHUB_ENDPOINT
import com.github.readingbat.common.Endpoints.OAUTH_CALLBACK_GOOGLE_ENDPOINT
import com.github.readingbat.common.Endpoints.OAUTH_LOGIN_ENDPOINT
import com.github.readingbat.common.Endpoints.OAUTH_LOGIN_GITHUB_ENDPOINT
import com.github.readingbat.common.Endpoints.OAUTH_LOGIN_GOOGLE_ENDPOINT
import com.github.readingbat.common.Endpoints.PING_ENDPOINT
import com.github.readingbat.common.Endpoints.PRIVACY_ENDPOINT
import com.github.readingbat.common.Endpoints.ROOT
import com.github.readingbat.common.OAuthReturnUrl
import com.github.readingbat.common.User.Companion.fetchUserDbmsIdFromCache
import com.github.readingbat.common.browserSession
import com.github.readingbat.common.userPrincipal
import com.github.readingbat.dsl.isDbmsEnabled
import com.github.readingbat.dsl.isSaveRequestsEnabled
import com.github.readingbat.server.GeoInfo.Companion.lookupGeoInfo
import com.github.readingbat.server.GeoInfo.Companion.queryGeoInfo
import com.github.readingbat.server.Intercepts.clock
import com.github.readingbat.server.Intercepts.logger
import com.github.readingbat.server.Intercepts.publicPaths
import com.github.readingbat.server.Intercepts.publicPrefixes
import com.github.readingbat.server.Intercepts.requestTimingMap
import com.github.readingbat.server.ServerUtils.fetchUserDbmsIdFromCache
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpHeaders.UserAgent
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.ApplicationCallPipeline.ApplicationPhase.Plugins
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.PipelineCall
import io.ktor.server.application.ServerReady
import io.ktor.server.application.call
import io.ktor.server.application.host
import io.ktor.server.application.port
import io.ktor.server.logging.toLogString
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.origin
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.queryString
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.RoutingRoot.Plugin.RoutingCallFinished
import io.ktor.server.routing.RoutingRoot.Plugin.RoutingCallStarted
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.util.pipeline.PipelineContext
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.timer
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal object Intercepts {
  internal val logger = KotlinLogging.logger {}
  val clock = TimeSource.Monotonic
  val requestTimingMap = ConcurrentHashMap<String, TimeMark>()

  val publicPaths =
    setOf(
    ROOT,
    OAUTH_LOGIN_ENDPOINT,
    OAUTH_LOGIN_GITHUB_ENDPOINT,
    OAUTH_LOGIN_GOOGLE_ENDPOINT,
    OAUTH_CALLBACK_GITHUB_ENDPOINT,
    OAUTH_CALLBACK_GOOGLE_ENDPOINT,
    HELP_ENDPOINT,
    ABOUT_ENDPOINT,
    PRIVACY_ENDPOINT,
    PING_ENDPOINT,
    "/favicon.ico",
    "/robots.txt",
    "/css.css",
    "/ktor/application/shutdown",
  )

  val publicPrefixes =
    listOf(
    "/oauth/",
    "/$STATIC/",
    "/static/",
  )

  @Suppress("unused")
  val timer =
    timer("requestTimingMap admin", false, 1.minutes.inWholeMilliseconds, 1.minutes.inWholeMilliseconds) {
      requestTimingMap
        .filter { (_, start) -> start.elapsedNow() > 1.hours }
        .forEach { (callId, start) ->
          requestTimingMap.remove(callId)
            ?.also {
              logger.info { "Removing requestTimingMap: $callId after ${start.elapsedNow()}" }
              updateServerRequest(callId, start)
            }
            ?: logger.info { "Unable to remove requestTimingMap item: $callId ${start.elapsedNow()}" }
        }
    }
}

private fun isBrowsableContentPath(path: String): Boolean {
  if (!path.startsWith("$CHALLENGE_ROOT/") && path != CHALLENGE_ROOT) return false
  val suffix = path.removePrefix(CHALLENGE_ROOT).trimStart('/')
  if (suffix.isEmpty()) return true
  return suffix.split('/').size <= 2 // /content/java (1) or /content/java/Warmup-1 (2)
}

internal fun Application.intercepts() {
  intercept(ApplicationCallPipeline.Setup) {
    // Phase for preparing call and it's attributes for processing
  }

  intercept(ApplicationCallPipeline.Monitoring) {
    // Phase for tracing calls, useful for logging, metrics, error handling and so on
  }

  // Mandatory auth: redirect unauthenticated users to OAuth login page
  // Language pages (/content/java) and group pages (/content/java/Warmup-1) are browsable without auth;
  // only individual challenges require authentication.
  if (isDbmsEnabled()) {
    intercept(Plugins) {
      val path = call.request.path()
      val isPublic = path in publicPaths || publicPrefixes.any { path.startsWith(it) } || isBrowsableContentPath(path)
      if (!isPublic && call.userPrincipal == null) {
        val returnUrl = path + call.request.queryString().let { if (it.isNotEmpty()) "?$it" else "" }
        call.sessions.set(OAuthReturnUrl(returnUrl))
        call.respondRedirect(OAUTH_LOGIN_ENDPOINT)
        finish()
      }

      // Capture return URL from OAuth login links (e.g., /oauth/login/github?return=/content/java/Warmup-1/hello)
      if (path == OAUTH_LOGIN_GITHUB_ENDPOINT || path == OAUTH_LOGIN_GOOGLE_ENDPOINT) {
        call.request.queryParameters["return"]
          ?.takeIf { it.startsWith("/") }
          ?.let { call.sessions.set(OAuthReturnUrl(it)) }
      }
    }
  }

  // Phase for features. Most features should intercept this phase
  intercept(Plugins) {
    if (!isStaticCall())
      runCatching {
        val browserSession = call.browserSession
        if (isSaveRequestsEnabled() && browserSession.isNotNull()) {
          val request = call.request
          val ipAddress = request.origin.remoteHost
          val sessionDbmsId = transaction { findOrCreateSessionDbmsId(browserSession.id, true) }
          val userDbmsId =
            call.fetchUserDbmsIdFromCache().takeIf { it != -1L } ?: fetchUserDbmsIdFromCache(UNKNOWN_USER_ID)
          val verbVal = request.httpMethod.value
          val pathVal = request.path()
          val queryStringVal = request.queryString()
          // Use https://ipgeolocation.io/documentation/user-agent-api.html to parse userAgent data
          val userAgentVal = request.headers[UserAgent] ?: ""

          val geoInfo = lookupGeoInfo(ipAddress)
          val geoDbmsId =
            if (geoInfo.requireDbmsLookUp)
              queryGeoInfo(ipAddress)?.dbmsId ?: error("Missing ip address: $ipAddress")
            else
              geoInfo.dbmsId

          val s = call.callId
          logger.debug { "Saving request: $s $ipAddress $userDbmsId $verbVal $pathVal $queryStringVal $geoDbmsId" }
          transaction {
            with(ServerRequestsTable) {
              insert { row ->
                row[requestId] = call.callId ?: "None"
                row[sessionRef] = sessionDbmsId
                row[userRef] = userDbmsId
                row[geoRef] = geoDbmsId
                row[verb] = verbVal
                row[path] = pathVal.maxLength(256)
                row[queryString] = queryStringVal.maxLength(256)
                row[userAgent] = userAgentVal.maxLength(256)
                row[duration] = 0
              }
            }
          }
        }
      }.onFailure { e ->
//        logger.warn(e) {}
        logger.info { "Failure saving request: ${e.message}" }
      }
  }

  intercept(ApplicationCallPipeline.Call) {
    // Phase for processing a call and sending a response
  }

  intercept(ApplicationCallPipeline.Fallback) {
    // Phase for handling unprocessed calls
  }

  monitor.subscribe(ApplicationStarted) { call ->
    logger.info { "Application started" }
  }
  monitor.subscribe(ServerReady) { call ->
    logger.info { "Server ready: ${call.config.host}:${call.config.port}" }
  }

  if (isSaveRequestsEnabled()) {
    monitor.subscribe(RoutingCallStarted) { call ->
      val path = call.request.path()
      if (!path.startsWith("/$STATIC/") && path != PING_ENDPOINT) {
        call.callId
          .also { callId ->
            if (callId.isNotNull()) {
              requestTimingMap[callId] = clock.markNow()
            }
          }
      }
    }

    monitor.subscribe(RoutingCallFinished) { call ->
      val path = call.request.path()
      if (!path.startsWith("/$STATIC/") && path != PING_ENDPOINT) {
        call.callId
          .also { callId ->
            if (callId.isNotNull()) {
              requestTimingMap.remove(callId)
                .also { start ->
                  if (start.isNotNull()) {
                    logger.debug {
                      val str = call.request.toLogString()
                      "Logged call ${requestTimingMap.size} ${start.elapsedNow()} ${call.callId} $str"
                    }
                    updateServerRequest(callId, start)
                  }
                }
            } else {
              logger.error { "Null requestId for $path" }
            }
          }
      }
    }
  }
}

fun updateServerRequest(callId: String, start: TimeMark) {
  transaction {
    with(ServerRequestsTable) {
      update({ requestId eq callId }) { row ->
        row[duration] = start.elapsedNow().inWholeMilliseconds
      }
    }
  }
}

fun PipelineContext<Unit, PipelineCall>.isStaticCall() = context.request.path().startsWith("/$STATIC/")
