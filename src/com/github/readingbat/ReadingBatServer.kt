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

package com.github.readingbat

import com.github.pambrose.common.util.FileSource
import com.github.readingbat.config.*
import com.github.readingbat.dsl.readDsl
import com.github.readingbat.misc.UserPrincipal
import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.auth.principal
import io.ktor.config.ApplicationConfigurationException
import io.ktor.routing.routing
import io.ktor.server.cio.CIO
import io.ktor.server.engine.commandLineEnvironment
import io.ktor.server.engine.embeddedServer
import io.ktor.sessions.get
import io.ktor.sessions.sessions
import io.ktor.sessions.set
import io.ktor.util.pipeline.PipelineContext
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

object ReadingBatServer {
  fun start(args: Array<String>) {
    val environment = commandLineEnvironment(args)
    embeddedServer(CIO, environment).start(wait = true)
  }
}

internal fun Application.module() {
  val fileName = property("readingbat.content.fileName", "src/Content.kt")
  val variableName = property("readingbat.content.variableName", "content")
  val readingBatContent =
    readDsl(FileSource(fileName = fileName), variableName = variableName)
      .apply {
        googleAnalyticsId = property("readingbat.site.googleAnalyticsId")
      }

  installs()
  intercepts()

  routing {
    locations(readingBatContent)
    userRoutes(readingBatContent)
    wsEndpoints(readingBatContent)
    adminRoutes(readingBatContent)
  }
}

private fun Application.property(name: String, default: String = "", warn: Boolean = false) =
  try {
    environment.config.property(name).getString()
  } catch (e: ApplicationConfigurationException) {
    if (warn)
      logger.warn { "Missing $name value in application.conf" }
    default
  }

typealias PipelineCall = PipelineContext<Unit, ApplicationCall>

internal fun PipelineCall.retrievePrincipal() =
  call.sessions.get<UserPrincipal>()

internal fun PipelineCall.assignPrincipal() =
  call.principal<UserPrincipal>().apply { if (this != null) call.sessions.set(this) }  // Set the cookie

internal fun PipelineCall.queryParam(key: String): String? = call.request.queryParameters[key]

internal class InvalidPathException(msg: String) : RuntimeException(msg)

internal class InvalidConfigurationException(msg: String) : Exception(msg)