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
import com.github.pambrose.common.redis.RedisUtils.REDIS_MAX_IDLE_SIZE
import com.github.pambrose.common.redis.RedisUtils.REDIS_MAX_POOL_SIZE
import com.github.pambrose.common.redis.RedisUtils.REDIS_MIN_IDLE_SIZE
import com.github.pambrose.common.util.*
import com.github.pambrose.common.util.Version
import com.github.pambrose.common.util.Version.Companion.versionDesc
import com.github.readingbat.common.Endpoints.STATIC_ROOT
import com.github.readingbat.common.EnvVars.*
import com.github.readingbat.common.Metrics
import com.github.readingbat.common.PropertyNames.AGENT_ENABLED_PROPERTY
import com.github.readingbat.common.PropertyNames.AGENT_LAUNCH_ID
import com.github.readingbat.common.PropertyNames.ANALYTICS_ID
import com.github.readingbat.common.PropertyNames.CONFIG_FILENAME
import com.github.readingbat.common.PropertyNames.FILE_NAME
import com.github.readingbat.common.PropertyNames.FORWARDED_ENABLED_PROPERTY
import com.github.readingbat.common.PropertyNames.IS_PRODUCTION
import com.github.readingbat.common.PropertyNames.JAVA_SCRIPTS_POOL_SIZE
import com.github.readingbat.common.PropertyNames.KOTLIN_SCRIPTS_POOL_SIZE
import com.github.readingbat.common.PropertyNames.MAX_CLASS_COUNT
import com.github.readingbat.common.PropertyNames.MAX_HISTORY_LENGTH
import com.github.readingbat.common.PropertyNames.PROXY_HOSTNAME
import com.github.readingbat.common.PropertyNames.PYTHON_SCRIPTS_POOL_SIZE
import com.github.readingbat.common.PropertyNames.READINGBAT
import com.github.readingbat.common.PropertyNames.REDIRECT_URL_PREFIX_PROPERTY
import com.github.readingbat.common.PropertyNames.SENDGRID_PREFIX_PROPERTY
import com.github.readingbat.common.PropertyNames.SITE
import com.github.readingbat.common.PropertyNames.VARIABLE_NAME
import com.github.readingbat.common.PropertyNames.XFORWARDED_ENABLED_PROPERTY
import com.github.readingbat.common.ScriptPools
import com.github.readingbat.dsl.*
import com.github.readingbat.server.AdminRoutes.adminRoutes
import com.github.readingbat.server.Installs.installs
import com.github.readingbat.server.Locations.locations
import com.github.readingbat.server.ReadingBatServer.adminUsers
import com.github.readingbat.server.ReadingBatServer.content
import com.github.readingbat.server.ReadingBatServer.contentReadCount
import com.github.readingbat.server.ServerUtils.configProperty
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
    val scriptClasspathProp = System.getProperty("kotlin.script.classpath")
    if (scriptClasspathProp.isNull()) {
      val scriptClasspathEnvVar = SCRIPT_CLASSPATH.getEnvOrNull()
      if (scriptClasspathEnvVar.isNotNull()) {
        logger.info { "Assigning kotlin.script.classpath = $scriptClasspathEnvVar" }
        System.setProperty("kotlin.script.classpath", scriptClasspathEnvVar)
      }
      else
        logger.warn { "Missing kotlin.script.classpath and $SCRIPT_CLASSPATH values" }
    }
    else {
      logger.info { "kotlin.script.classpath: $scriptClasspathProp" }
    }

    val redisUrl =
      REDIS_URL.getEnv("unassigned")
        .let {
          if ("://" in it && "@" in it) {
            val scheme = it.split("://")
            val uri = it.split("@")
            "${scheme[0]}://*****:*****@${uri[1]}"
          }
          else {
            it
          }
        }
    logger.info { "$REDIS_URL: $redisUrl" }

    // Grab config filename from CLI args and then try ENV var
    val configFilename =
      args.asSequence()
        .filter { it.startsWith("-config=") }
        .map { it.replaceFirst("-config=", "") }
        .firstOrNull()
        ?: AGENT_CONFIG.getEnvOrNull()
        ?: System.getProperty("agent.config")
        ?: "src/main/resources/application.conf"

    logger.info { "Configuration file: $configFilename" }
    System.setProperty(CONFIG_FILENAME, configFilename)

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

private fun Application.redirectUrlPrefix() =
  REDIRECT_URL_PREFIX.getEnv(configProperty(REDIRECT_URL_PREFIX_PROPERTY, default = "https://www.readingbat.com"))

private fun Application.sendGridPrefix() =
  SENDGRID_PREFIX.getEnv(configProperty(SENDGRID_PREFIX_PROPERTY, default = "https://www.readingbat.com"))

private fun Application.agentEnabled() =
  AGENT_ENABLED.getEnv(configProperty(AGENT_ENABLED_PROPERTY, default = "false").toBoolean())

private fun Application.forwardedHeaderSupportEnabled() =
  FORWARDED_ENABLED.getEnv(configProperty(FORWARDED_ENABLED_PROPERTY, default = "false").toBoolean())

private fun Application.xforwardedHeaderSupportEnabled() =
  XFORWARDED_ENABLED.getEnv(configProperty(XFORWARDED_ENABLED_PROPERTY, default = "false").toBoolean())

internal fun Application.assignContentDsl(fileName: String, variableName: String) {
  ReadingBatServer.logger.info { "Loading content content using $variableName in $fileName" }
  measureTime {
    content.set(
      readContentDsl(FileSource(fileName = fileName), variableName = variableName)
        .apply {
          dslFileName = fileName
          dslVariableName = variableName
          sendGridPrefix = sendGridPrefix()
          googleAnalyticsId = configProperty(ANALYTICS_ID)
          maxHistoryLength = configProperty(MAX_HISTORY_LENGTH, default = "10").toInt()
          maxClassCount = configProperty(MAX_CLASS_COUNT, default = "25").toInt()
          ktorPort = configProperty("ktor.deployment.port", "0").toInt()
          val watchVal = environment.config.propertyOrNull("ktor.deployment.watch")?.getList() ?: emptyList()
          ktorWatch = if (watchVal.isNotEmpty()) watchVal.toString() else "unassigned"
          grafanaUrl = configProperty("$READINGBAT.grafana.url")
          prometheusUrl = configProperty("$READINGBAT.prometheus.url")
        }.apply { clearContentMap() })
    ReadingBatServer.metrics.contentLoadedCount.labels(agentLaunchId()).inc()
  }.also {
    ReadingBatServer.logger.info { "Loaded content in $it using $variableName in $fileName" }
  }
  contentReadCount.incrementAndGet()
}

internal fun Application.module() {
  val logger = ReadingBatServer.logger
  val fileName = configProperty(FILE_NAME, "src/Content.kt")
  val variableName = configProperty(VARIABLE_NAME, "content")
  val proxyHostname = configProperty(PROXY_HOSTNAME, default = "")
  val maxDelay = configProperty("$READINGBAT.$SITE.startupMaxDelaySecs", default = "30").toInt()
  val metrics = ReadingBatServer.metrics

  val admins = environment.config.propertyOrNull("$READINGBAT.adminUsers")?.getList() ?: emptyList()
  adminUsers.addAll(admins)

  System.setProperty(IS_PRODUCTION, configProperty(IS_PRODUCTION, default = "false").toBoolean().toString())
  System.setProperty(AGENT_ENABLED_PROPERTY, agentEnabled().toString())
  System.setProperty(JAVA_SCRIPTS_POOL_SIZE, configProperty(JAVA_SCRIPTS_POOL_SIZE, default = "5"))
  System.setProperty(KOTLIN_SCRIPTS_POOL_SIZE, configProperty(KOTLIN_SCRIPTS_POOL_SIZE, default = "5"))
  System.setProperty(PYTHON_SCRIPTS_POOL_SIZE, configProperty(PYTHON_SCRIPTS_POOL_SIZE, default = "5"))
  System.setProperty(REDIS_MAX_POOL_SIZE, configProperty(REDIS_MAX_POOL_SIZE, default = "10"))
  System.setProperty(REDIS_MAX_IDLE_SIZE, configProperty(REDIS_MAX_IDLE_SIZE, default = "5"))
  System.setProperty(REDIS_MIN_IDLE_SIZE, configProperty(REDIS_MIN_IDLE_SIZE, default = "1"))

  if (isAgentEnabled()) {
    if (proxyHostname.isNotEmpty()) {
      val configFilename = System.getProperty(CONFIG_FILENAME) ?: ""
      val agentInfo = startAsyncAgent(configFilename, true)
      System.setProperty(AGENT_LAUNCH_ID, agentInfo.launchId)
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
           redirectUrlPrefix(),
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