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

import com.github.pambrose.common.redis.RedisUtils
import com.github.pambrose.common.util.*
import com.github.pambrose.common.util.Version
import com.github.pambrose.common.util.Version.Companion.versionDesc
import com.github.readingbat.common.CommonUtils.maskUrl
import com.github.readingbat.common.Endpoints.STATIC_ROOT
import com.github.readingbat.common.EnvVars.*
import com.github.readingbat.common.Metrics
import com.github.readingbat.common.Properties.*
import com.github.readingbat.common.ScriptPools
import com.github.readingbat.dsl.*
import com.github.readingbat.server.AdminRoutes.adminRoutes
import com.github.readingbat.server.Installs.installs
import com.github.readingbat.server.Locations.locations
import com.github.readingbat.server.ReadingBatServer.adminUsers
import com.github.readingbat.server.ReadingBatServer.content
import com.github.readingbat.server.ReadingBatServer.contentReadCount
import com.github.readingbat.server.WsEndoints.wsEndpoints
import io.ktor.application.*
import io.ktor.http.content.*
import io.ktor.routing.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.prometheus.Agent.Companion.startAsyncAgent
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import mu.KLogging
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.TimeSource
import kotlin.time.measureTime
import kotlin.time.seconds

@Version(version = "1.3.0", date = "8/22/20")
object ReadingBatServer : KLogging() {
  internal val timeStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("M/d/y H:m:ss"))
  private val startTime = TimeSource.Monotonic.markNow()
  internal val upTime get() = startTime.elapsedNow()
  internal val content = AtomicReference(ReadingBatContent())
  internal val adminUsers = mutableListOf<String>()
  internal val contentReadCount = AtomicInteger(0)
  internal val metrics by lazy { Metrics() }
  internal val pool by lazy { RedisUtils.newJedisPool() }

  fun start(args: Array<String>) {

    logger.apply {
      info { getBanner("banners/readingbat.txt", this) }
      info { ReadingBatServer::class.versionDesc() }
    }

    // If kotlin.script.classpath property is missing, set it based on env var SCRIPT_CLASSPATH
    // This has to take place before reading DSL
    val scriptClasspathProp = KOTLIN_SCRIPT_CLASSPATH.getPropertyOrNull()
    if (scriptClasspathProp.isNull()) {
      val scriptClasspathEnvVar = SCRIPT_CLASSPATH.getEnvOrNull()
      if (scriptClasspathEnvVar.isNotNull()) {
        logger.info { "Assigning ${KOTLIN_SCRIPT_CLASSPATH.propertyValue} = $scriptClasspathEnvVar" }
        KOTLIN_SCRIPT_CLASSPATH.setProperty(scriptClasspathEnvVar)
      }
      else
        logger.warn { "Missing ${KOTLIN_SCRIPT_CLASSPATH.propertyValue} and $SCRIPT_CLASSPATH values" }
    }
    else {
      logger.info { "${KOTLIN_SCRIPT_CLASSPATH.propertyValue}: $scriptClasspathProp" }
    }

    logger.info { "$REDIS_URL: ${REDIS_URL.getEnv("unassigned").maskUrl()}" }

    // Grab config filename from CLI args and then try ENV var
    val configFilename =
      args.asSequence()
        .filter { it.startsWith("-config=") }
        .map { it.replaceFirst("-config=", "") }
        .firstOrNull()
        ?: AGENT_CONFIG.getEnvOrNull()
        ?: AGENT_CONFIG_PROPERTY.getPropertyOrNull()
        ?: "src/main/resources/application.conf"

    CONFIG_FILENAME.setProperty(configFilename)

    val newargs =
      if (args.any { it.startsWith("-config=") })
        args
      else
        args.toMutableList().apply { add("-config=$configFilename") }.toTypedArray()

    // Reference these to load them
    ScriptPools.javaScriptPool
    ScriptPools.pythonScriptPool
    ScriptPools.kotlinScriptPool

    val environment = commandLineEnvironment(newargs)
    embeddedServer(CIO, environment).start(wait = true)
  }
}

private fun Application.redirectHostname() =
  REDIRECT_HOSTNAME.getEnv(REDIRECT_HOSTNAME_PROPERTY.configProperty(this, default = ""))

private fun Application.sendGridPrefix() =
  SENDGRID_PREFIX.getEnv(SENDGRID_PREFIX_PROPERTY.configProperty(this, default = "https://www.readingbat.com"))

private fun Application.agentEnabled() =
  AGENT_ENABLED.getEnv(AGENT_ENABLED_PROPERTY.configProperty(this, default = "false").toBoolean())

private fun Application.forwardedHeaderSupportEnabled() =
  FORWARDED_ENABLED.getEnv(FORWARDED_ENABLED_PROPERTY.configProperty(this, default = "false").toBoolean())

private fun Application.xforwardedHeaderSupportEnabled() =
  XFORWARDED_ENABLED.getEnv(XFORWARDED_ENABLED_PROPERTY.configProperty(this, default = "false").toBoolean())

internal fun Application.assignContentDsl(fileName: String, variableName: String) {
  ReadingBatServer.logger.info { "Loading content content using $variableName in $fileName" }
  measureTime {
    content.set(
      readContentDsl(FileSource(fileName = fileName), variableName = variableName)
        .apply {
          dslFileName = fileName
          dslVariableName = variableName
          sendGridPrefix = sendGridPrefix()
          googleAnalyticsId = ANALYTICS_ID.configProperty(this@assignContentDsl)
          maxHistoryLength = MAX_HISTORY_LENGTH.configProperty(this@assignContentDsl, default = "10").toInt()
          maxClassCount = MAX_CLASS_COUNT.configProperty(this@assignContentDsl, default = "25").toInt()
          ktorPort = KTOR_PORT.configProperty(this@assignContentDsl, "0").toInt()
          val watchVal = environment.config.propertyOrNull("ktor.deployment.watch")?.getList() ?: emptyList()
          ktorWatch = if (watchVal.isNotEmpty()) watchVal.toString() else "unassigned"
          grafanaUrl = GRAFANA_URL.configProperty(this@assignContentDsl)
          prometheusUrl = PROMETHEUS_URL.configProperty(this@assignContentDsl)
        }.apply { clearContentMap() })
    ReadingBatServer.metrics.contentLoadedCount.labels(agentLaunchId()).inc()
  }.also {
    ReadingBatServer.logger.info { "Loaded content in $it using $variableName in $fileName" }
  }
  contentReadCount.incrementAndGet()
}

internal fun Application.module() {
  val logger = ReadingBatServer.logger
  val fileName = FILE_NAME.configProperty(this, "src/Content.kt")
  val variableName = VARIABLE_NAME.configProperty(this, "content")
  val proxyHostname = PROXY_HOSTNAME.configProperty(this, default = "")
  val maxDelay = STARTUP_DELAY_SECS.configProperty(this, default = "30").toInt()
  val metrics = ReadingBatServer.metrics

  adminUsers.addAll(ADMIN_USERS.configPropertyOrNull(this)?.getList() ?: emptyList())

  IS_PRODUCTION.setProperty(IS_PRODUCTION.configProperty(this, default = "false").toBoolean().toString())
  AGENT_ENABLED_PROPERTY.setProperty(agentEnabled().toString())
  JAVA_SCRIPTS_POOL_SIZE.setProperty(JAVA_SCRIPTS_POOL_SIZE.configProperty(this, default = "5"))
  KOTLIN_SCRIPTS_POOL_SIZE.setProperty(KOTLIN_SCRIPTS_POOL_SIZE.configProperty(this, default = "5"))
  PYTHON_SCRIPTS_POOL_SIZE.setProperty(PYTHON_SCRIPTS_POOL_SIZE.configProperty(this, default = "5"))
  REDIS_MAX_POOL_SIZE.setProperty(REDIS_MAX_POOL_SIZE.configProperty(this, default = "10"))
  REDIS_MAX_IDLE_SIZE.setProperty(REDIS_MAX_IDLE_SIZE.configProperty(this, default = "5"))
  REDIS_MIN_IDLE_SIZE.setProperty(REDIS_MIN_IDLE_SIZE.configProperty(this, default = "1"))

  if (isAgentEnabled()) {
    if (proxyHostname.isNotEmpty()) {
      val configFilename = CONFIG_FILENAME.getRequiredProperty()
      val agentInfo = startAsyncAgent(configFilename, true)
      AGENT_LAUNCH_ID.setProperty(agentInfo.launchId)
    }
    else {
      logger.error { "Prometheus agent is enabled but the proxy hostname is not assigned" }
    }
  }

  // This is done *after* AGENT_LAUNCH_ID is assigned because metrics depend on it
  metrics.init { content.get() }

  val job =
    launch {
      assignContentDsl(fileName, variableName)
    }

  runBlocking {
    logger.info { "Delaying start-up by $maxDelay seconds" }
    val dur =
      measureTime {
        withTimeoutOrNull(maxDelay.seconds) {
          job.join()
        }
      }
    logger.info { "Continued start-up after delaying $dur" }
  }

  installs(isProduction(),
           redirectHostname(),
           forwardedHeaderSupportEnabled(),
           xforwardedHeaderSupportEnabled())
  intercepts()

  routing {
    adminRoutes(metrics)
    locations(metrics) { content.get() }
    userRoutes(metrics) { content.get() }
    sysAdminRoutes(metrics, { content.get() }, { assignContentDsl(fileName, variableName) })
    wsEndpoints(metrics) { content.get() }
    static(STATIC_ROOT) { resources("static") }
  }
}