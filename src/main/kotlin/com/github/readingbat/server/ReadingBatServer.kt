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

import com.github.pambrose.common.util.FileSource
import com.github.pambrose.common.util.Version
import com.github.pambrose.common.util.Version.Companion.versionDesc
import com.github.pambrose.common.util.getBanner
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.dsl.agentLaunchId
import com.github.readingbat.dsl.isProduction
import com.github.readingbat.dsl.readContentDsl
import com.github.readingbat.misc.Constants.AGENT_ENABLED
import com.github.readingbat.misc.Constants.AGENT_LAUNCH_ID
import com.github.readingbat.misc.Constants.ANALYTICS_ID
import com.github.readingbat.misc.Constants.CONFIG_FILENAME
import com.github.readingbat.misc.Constants.FILE_NAME
import com.github.readingbat.misc.Constants.IS_PRODUCTION
import com.github.readingbat.misc.Constants.MAX_CLASS_COUNT
import com.github.readingbat.misc.Constants.MAX_HISTORY_LENGTH
import com.github.readingbat.misc.Constants.PROXY_HOSTNAME
import com.github.readingbat.misc.Constants.STATIC_ROOT
import com.github.readingbat.misc.Constants.URL_PREFIX
import com.github.readingbat.misc.Constants.VARIABLE_NAME
import com.github.readingbat.server.AdminRoutes.adminRoutes
import com.github.readingbat.server.Installs.installs
import com.github.readingbat.server.Locations.locations
import com.github.readingbat.server.ReadingBatServer.content
import com.github.readingbat.server.ServerUtils.property
import com.github.readingbat.server.WsEndoints.wsEndpoints
import io.ktor.application.*
import io.ktor.http.content.*
import io.ktor.routing.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.prometheus.Agent
import io.prometheus.Agent.Companion.startAsyncAgent
import mu.KLogging
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.milliseconds

@Version(version = "1.2.0", date = "8/11/20")
object ReadingBatServer : KLogging() {
  internal val timeStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("M/d/y H:m:ss"))
  internal val startTimeMillis = System.currentTimeMillis().milliseconds
  internal val content = AtomicReference(ReadingBatContent())
  internal val metrics by lazy { Metrics() }

  fun start(args: Array<String>) {
    // grab config filename from CLI args and then try ENV var
    val configFilename =
      args.asSequence()
        .filter { it.startsWith("-config=") }
        .map { it.replaceFirst("-config=", "") }
        .firstOrNull()
        ?: System.getenv("AGENT_CONFIG")
        ?: System.getProperty("agent.config")
        ?: "src/main/resources/application.conf"

    logger.info { "Using configuration file: $configFilename" }

    System.setProperty(CONFIG_FILENAME, configFilename)

    val newargs =
      if (args.any { it.startsWith("-config=") })
        args
      else
        args.toMutableList().apply { add("-config=$configFilename") }.toTypedArray()

    val environment = commandLineEnvironment(newargs)
    embeddedServer(CIO, environment).start(wait = true)
  }
}

internal fun Application.assignContentDsl(fileName: String, variableName: String) {
  content.set(
    readContentDsl(FileSource(fileName = fileName), variableName = variableName)
      .apply {
        dslFileName = fileName
        dslVariableName = variableName
        urlPrefix = property(URL_PREFIX, default = "https://www.readingbat.com")
        googleAnalyticsId = property(ANALYTICS_ID)
        maxHistoryLength = property(MAX_HISTORY_LENGTH, default = "10").toInt()
        maxClassCount = property(MAX_CLASS_COUNT, default = "25").toInt()
        ktorPort = property("ktor.deployment.port", "0").toInt()
        val watchVal = environment.config.propertyOrNull("ktor.deployment.watch")?.getList() ?: emptyList()
        ktorWatch = if (watchVal.isNotEmpty()) watchVal.toString() else "unassigned"
      })
  ReadingBatServer.metrics.contentLoadedCount.labels(agentLaunchId()).inc()
}

internal fun Application.module() {
  val fileName = property(FILE_NAME, "src/Content.kt")
  val variableName = property(VARIABLE_NAME, "content")
  val isProduction = property(IS_PRODUCTION, default = "false").toBoolean()
  val agentEnabled = property(AGENT_ENABLED, default = "true").toBoolean()
  val proxyHostname = property(PROXY_HOSTNAME, default = "")
  val metrics = ReadingBatServer.metrics

  System.setProperty(IS_PRODUCTION, isProduction.toString())

  Agent.logger.apply {
    info { getBanner("banners/readingbat.txt", this) }
    info { ReadingBatServer::class.versionDesc() }
  }

  if (agentEnabled && proxyHostname.isNotEmpty()) {
    val configFilename = System.getProperty(CONFIG_FILENAME) ?: ""
    val agentInfo = startAsyncAgent(configFilename, true)
    System.setProperty(AGENT_LAUNCH_ID, agentInfo.launchId)
  }

  // This is done after AGENT_LAUNCH_ID is assigned
  metrics.init({ content.get() })

  assignContentDsl(fileName, variableName)

  installs(isProduction(), content.get().urlPrefix)
  intercepts()

  routing {
    adminRoutes(metrics)
    locations(metrics) { content.get() }
    userRoutes(metrics, { content.get() }, { assignContentDsl(fileName, variableName) })
    wsEndpoints(metrics) { content.get() }
    static(STATIC_ROOT) { resources("static") }
  }
}