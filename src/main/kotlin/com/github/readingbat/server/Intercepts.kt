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
import com.github.readingbat.common.BrowserSession.Companion.querySessionDbmsId
import com.github.readingbat.common.Constants.STATIC
import com.github.readingbat.common.Constants.UNKNOWN_USER_ID
import com.github.readingbat.common.SessionActivites.markActivity
import com.github.readingbat.common.SessionActivites.queryGeoDbmsId
import com.github.readingbat.common.browserSession
import com.github.readingbat.common.queryUserDbmsId
import com.github.readingbat.dsl.isPostgresEnabled
import com.github.readingbat.server.Intercepts.logger
import com.github.readingbat.server.ServerUtils.fetchUserDbmsId
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.request.*
import io.ktor.routing.*
import io.ktor.routing.Routing.Feature.RoutingCallFinished
import io.ktor.routing.Routing.Feature.RoutingCallStarted
import mu.KLogging
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal fun Application.intercepts() {

  val clock = TimeSource.Monotonic
  val timingMap = ConcurrentHashMap<String, TimeMark>()

  intercept(ApplicationCallPipeline.Setup) {
    // Phase for preparing call and it's attributes for processing
  }

  intercept(ApplicationCallPipeline.Monitoring) {
    // Phase for tracing calls, useful for logging, metrics, error handling and so on
  }

  intercept(ApplicationCallPipeline.Features) {
    // Phase for features. Most features should intercept this phase

    if (!isStaticCall()) {
      val browserSession = call.browserSession
      //logger.info { "${context.request.origin.remoteHost} $browserSession ${context.request.path()}" }
      browserSession?.markActivity("intercept()", call)
        ?: logger.info { "Null browser sessions for ${call.request.origin.remoteHost}" }

      if (isPostgresEnabled() && browserSession.isNotNull()) {
        val request = call.request
        val ipAddress = request.origin.remoteHost
        val sessionDbmsId = transaction { querySessionDbmsId(browserSession.id) }
        val userDbmsId = call.fetchUserDbmsId().takeIf { it != -1L } ?: transaction { queryUserDbmsId(UNKNOWN_USER_ID) }
        val geoDbmsId = transaction { queryGeoDbmsId(ipAddress) }
        val verb = request.httpMethod.value
        val path = request.path()
        val queryString = request.queryString()

        logger.info { "Saving request: ${call.callId} $ipAddress $userDbmsId $verb $path $queryString $geoDbmsId" }
        transaction {
          ServerRequests
            .insertAndGetId { row ->
              row[requestId] = call.callId ?: "None"
              row[sessionRef] = sessionDbmsId
              row[userRef] = userDbmsId
              row[geoRef] = geoDbmsId
              row[ServerRequests.verb] = verb
              row[ServerRequests.path] = path
              row[ServerRequests.queryString] = queryString
            }
        }
      }
    }
  }

  intercept(ApplicationCallPipeline.Call) {
    // Phase for processing a call and sending a response
  }

  intercept(ApplicationCallPipeline.Fallback) {
    // Phase for handling unprocessed calls
  }

  environment.monitor.subscribe(RoutingCallStarted) { call: RoutingApplicationCall ->
    if (!call.request.path().startsWith("/$STATIC/")) {
      call.callId
        .also { callId ->
          if (callId.isNotNull()) {
            timingMap.put(callId, clock.markNow())
            // logger.info { "Monitor start ${call.callId}  ${call.request.toLogString()}" }
          }
        }
    }
  }

  environment.monitor.subscribe(RoutingCallFinished) { call: RoutingApplicationCall ->
    call.callId
      .also { callId ->
        if (callId.isNotNull()) {
          timingMap.remove(callId)
            .also { start ->
              val requestId = call.callId
              if (start.isNotNull() && requestId.isNotNull()) {
                logger.info { "Monitor stop ${timingMap.size} ${call.callId}  ${start.elapsedNow()} ${call.request.toLogString()}" }
                transaction {
                  ServerRequests
                    .update({ ServerRequests.requestId eq requestId }) { row ->
                      row[duration] = start.elapsedNow().toLongMilliseconds()
                    }
                }
              }
            }
        }
      }
  }
}

fun PipelineCall.isStaticCall() = context.request.path().startsWith("/$STATIC/")

object Intercepts : KLogging()
