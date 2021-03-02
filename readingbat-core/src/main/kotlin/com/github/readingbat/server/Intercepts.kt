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

import com.github.pambrose.common.util.isNotNull
import com.github.pambrose.common.util.maxLength
import com.github.readingbat.common.BrowserSession.Companion.findSessionDbmsId
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
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.HttpHeaders.UserAgent
import io.ktor.request.*
import io.ktor.routing.Routing.Feature.RoutingCallFinished
import io.ktor.routing.Routing.Feature.RoutingCallStarted
import mu.KLogging
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.timer
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlin.time.hours
import kotlin.time.minutes

internal object Intercepts : KLogging() {
  val clock = TimeSource.Monotonic
  val requestTimingMap = ConcurrentHashMap<String, TimeMark>()

  @Suppress("unused")
  val timer =
    timer("requestTimingMap admin", false, 1.minutes.toLongMilliseconds(), 1.minutes.toLongMilliseconds()) {
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

  intercept(ApplicationCallPipeline.Features) {
    // Phase for features. Most features should intercept this phase
    if (!isStaticCall())
      try {
        val browserSession = call.browserSession
        if (isSaveRequestsEnabled() && browserSession.isNotNull()) {
          val request = call.request
          val ipAddress = request.origin.remoteHost
          val sessionDbmsId = transaction { findSessionDbmsId(browserSession.id, true) }
          val userDbmsId =
            call.fetchUserDbmsIdFromCache().takeIf { it != -1L } ?: fetchUserDbmsIdFromCache(UNKNOWN_USER_ID)
          val verb = request.httpMethod.value
          val path = request.path()
          val queryString = request.queryString()
          // Use https://ipgeolocation.io/documentation/user-agent-api.html to parse userAgent data
          val userAgent = request.headers[UserAgent] ?: ""

          val geoInfo = lookupGeoInfo(ipAddress)
          val geoDbmsId =
            if (geoInfo.requireDbmsLookUp)
              queryGeoInfo(ipAddress)?.dbmsId ?: error("Missing ip address: $ipAddress")
            else
              geoInfo.dbmsId

          logger.debug { "Saving request: ${call.callId} $ipAddress $userDbmsId $verb $path $queryString $geoDbmsId" }
          transaction {
            ServerRequestsTable
              .insert { row ->
                row[requestId] = call.callId ?: "None"
                row[sessionRef] = sessionDbmsId
                row[userRef] = userDbmsId
                row[geoRef] = geoDbmsId
                row[ServerRequestsTable.verb] = verb
                row[ServerRequestsTable.path] = path.maxLength(256)
                row[ServerRequestsTable.queryString] = queryString.maxLength(256)
                row[ServerRequestsTable.userAgent] = userAgent.maxLength(256)
                row[duration] = 0
              }
          }
        }
      } catch (e: Throwable) {
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
                        logger.debug { "Logged call ${requestTimingMap.size} ${start.elapsedNow()} ${call.callId} ${call.request.toLogString()}" }
                        updateServerRequest(callId, start)
                      }
                    }
                }
                else {
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
    ServerRequestsTable
      .update({ ServerRequestsTable.requestId eq callId }) { row ->
        row[duration] = start.elapsedNow().toLongMilliseconds()
      }
  }
}

fun PipelineCall.isStaticCall() = context.request.path().startsWith("/$STATIC/")
