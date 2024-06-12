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
import com.github.readingbat.common.Endpoints.PING_ENDPOINT
import com.github.readingbat.common.User.Companion.fetchUserDbmsIdFromCache
import com.github.readingbat.common.browserSession
import com.github.readingbat.dsl.isSaveRequestsEnabled
import com.github.readingbat.server.GeoInfo.Companion.lookupGeoInfo
import com.github.readingbat.server.GeoInfo.Companion.queryGeoInfo
import com.github.readingbat.server.Intercepts.clock
import com.github.readingbat.server.Intercepts.logger
import com.github.readingbat.server.Intercepts.requestTimingMap
import com.github.readingbat.server.ServerUtils.fetchUserDbmsIdFromCache
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpHeaders.UserAgent
import io.ktor.server.application.*
import io.ktor.server.application.ApplicationCallPipeline.ApplicationPhase.Plugins
import io.ktor.server.logging.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.request.*
import io.ktor.server.routing.Routing.Plugin.RoutingCallFinished
import io.ktor.server.routing.Routing.Plugin.RoutingCallStarted
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
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

internal fun Application.intercepts() {
  intercept(ApplicationCallPipeline.Setup) {
    // Phase for preparing call and it's attributes for processing
  }

  intercept(ApplicationCallPipeline.Monitoring) {
    // Phase for tracing calls, useful for logging, metrics, error handling and so on
  }

  intercept(Plugins) {
    // Phase for features. Most features should intercept this phase
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

  if (isSaveRequestsEnabled()) {
    environment.monitor
      .apply {
        subscribe(RoutingCallStarted) { call ->
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

        subscribe(RoutingCallFinished) { call ->
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

fun PipelineCall.isStaticCall() = context.request.path().startsWith("/$STATIC/")
