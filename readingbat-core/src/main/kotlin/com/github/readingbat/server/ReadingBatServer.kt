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
import com.github.pambrose.common.util.FileSource
import com.github.pambrose.common.util.Version
import com.github.pambrose.common.util.Version.Companion.versionDesc
import com.github.pambrose.common.util.getBanner
import com.github.pambrose.common.util.isNotNull
import com.github.pambrose.common.util.isNull
import com.github.pambrose.common.util.maskUrlCredentials
import com.github.pambrose.common.util.randomId
import com.github.readingbat.common.Constants.REDIS_IS_DOWN
import com.github.readingbat.common.Constants.UNASSIGNED
import com.github.readingbat.common.Constants.UNKNOWN_USER_ID
import com.github.readingbat.common.Endpoints.STATIC_ROOT
import com.github.readingbat.common.EnvVar
import com.github.readingbat.common.EnvVar.*
import com.github.readingbat.common.Metrics
import com.github.readingbat.common.Property.*
import com.github.readingbat.common.Property.DBMS_DRIVER_CLASSNAME
import com.github.readingbat.common.User.Companion.createUnknownUser
import com.github.readingbat.common.User.Companion.userExists
import com.github.readingbat.dsl.*
import com.github.readingbat.server.Installs.installs
import com.github.readingbat.server.Locations.locations
import com.github.readingbat.server.ReadingBatServer.adminUsers
import com.github.readingbat.server.ReadingBatServer.content
import com.github.readingbat.server.ReadingBatServer.contentReadCount
import com.github.readingbat.server.ReadingBatServer.logger
import com.github.readingbat.server.ReadingBatServer.metrics
import com.github.readingbat.server.ServerUtils.logToRedis
import com.github.readingbat.server.routes.AdminRoutes.adminRoutes
import com.github.readingbat.server.routes.sysAdminRoutes
import com.github.readingbat.server.routes.userRoutes
import com.github.readingbat.server.ws.LoggingWs
import com.github.readingbat.server.ws.PubSubCommandsWs
import com.github.readingbat.server.ws.WsCommon.wsRoutes
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.http.content.*
import io.ktor.routing.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.prometheus.Agent.Companion.startAsyncAgent
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import mu.KLogging
import org.jetbrains.exposed.sql.Database
import redis.clients.jedis.JedisPool
import redis.clients.jedis.exceptions.JedisConnectionException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.TimeSource
import kotlin.time.measureTime
import kotlin.time.seconds

@Version(version = "1.9.0", date = "12/5/20")
object ReadingBatServer : KLogging() {
  private val startTime = TimeSource.Monotonic.markNow()
  internal val serverSessionId = randomId(10)
  internal val timeStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("M/d/y H:m:ss"))
  internal val content = AtomicReference(ReadingBatContent())
  internal val adminUsers = mutableListOf<String>()
  internal val contentReadCount = AtomicInteger(0)
  internal val metrics by lazy { Metrics() }
  internal var redisPool: JedisPool? = null
  internal val postgres by lazy {
    Database.connect(
      HikariDataSource(
        HikariConfig()
          .apply {
            driverClassName = EnvVar.DBMS_DRIVER_CLASSNAME.getEnv(DBMS_DRIVER_CLASSNAME.getRequiredProperty())
            jdbcUrl = POSTGRES_URL.getEnv(DBMS_URL.getRequiredProperty())
            username = POSTGRES_USERNAME.getEnv(DBMS_USERNAME.getRequiredProperty())
            password = POSTGRES_PASSWORD.getEnv(DBMS_PASSWORD.getRequiredProperty())

            CLOUD_SQL_CONNECTION_NAME.getEnv("")
              .also {
                if (it.isNotBlank()) {
                  addDataSourceProperty("cloudSqlInstance", it)
                  addDataSourceProperty("socketFactory", "com.google.cloud.sql.postgres.SocketFactory")
                }
              }

            maximumPoolSize = DBMS_MAX_POOL_SIZE.getRequiredProperty().toInt()
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
          }))
  }

  internal val upTime get() = startTime.elapsedNow()

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
      if (scriptClasspathEnvVar.isNotNull())
        KOTLIN_SCRIPT_CLASSPATH.setProperty(scriptClasspathEnvVar)
      else
        logger.warn { "Missing ${KOTLIN_SCRIPT_CLASSPATH.propertyValue} and $SCRIPT_CLASSPATH values" }
    }
    else {
      logger.info { "${KOTLIN_SCRIPT_CLASSPATH.propertyValue}: $scriptClasspathProp" }
    }

    logger.info { "$REDIS_URL: ${REDIS_URL.getEnv(UNASSIGNED).maskUrlCredentials()}" }

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

    val newArgs =
      if (args.any { it.startsWith("-config=") })
        args
      else
        args.toMutableList().apply { add("-config=$configFilename") }.toTypedArray()

    val environment = commandLineEnvironment(newArgs)

    // Reference these to load them
    ScriptPools.javaScriptPool
    ScriptPools.pythonScriptPool
    ScriptPools.kotlinScriptPool

    redisPool =
      try {
        RedisUtils.newJedisPool().also { logger.info { "Created Redis pool" } }
      } catch (e: JedisConnectionException) {
        logger.error { "Failed to create Redis pool: $REDIS_IS_DOWN" }
        null  // Return null
      }

    embeddedServer(CIO, environment).start(wait = true)
  }
}

internal fun Application.readContentDsl(fileName: String, variableName: String, logId: String = "") {
  "Loading getContent using $variableName in $fileName"
    .also {
      logger.info { it }
      logToRedis(it, logId)
    }
  measureTime {
    val contentSource = FileSource(fileName = fileName)
    val dslCode = readContentDsl(contentSource)
    content.set(
      evalContentDsl(contentSource.source, variableName, dslCode)
        .apply {
          maxHistoryLength = MAX_HISTORY_LENGTH.configValue(this@readContentDsl, "10").toInt()
          maxClassCount = MAX_CLASS_COUNT.configValue(this@readContentDsl, "25").toInt()
        }
        .apply { clearContentMap() })
    metrics.contentLoadedCount.labels(agentLaunchId()).inc()
  }.also { dur ->
    "Loaded getContent using $variableName in $fileName in $dur"
      .also {
        logger.info { it }
        logToRedis(it, logId)
      }
  }
  contentReadCount.incrementAndGet()
}

internal fun Application.module(testing: Boolean = false) {

  assignProperties()

  adminUsers.addAll(ADMIN_USERS.configValueOrNull(this)?.getList() ?: emptyList())

  // Only run this
  if (isProduction())
    PubSubCommandsWs.initThreads()

  if (isPostgresEnabled()) {
    ReadingBatServer.postgres

    // Create unknown user if it does not already exist
    if (!userExists(UNKNOWN_USER_ID))
      createUnknownUser(UNKNOWN_USER_ID)
  }

  if (isAgentEnabled()) {
    if (PROXY_HOSTNAME.getRequiredProperty().isNotEmpty()) {
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

  val dslFileName = DSL_FILE_NAME.getRequiredProperty()
  val dslVariableName = DSL_VARIABLE_NAME.getRequiredProperty()

  val job = launch { readContentDsl(dslFileName, dslVariableName) }

  runBlocking {
    val maxDelay = STARTUP_DELAY_SECS.configValue(this@module, "30").toInt().seconds
    logger.info { "Delaying start-up by max of $maxDelay" }
    measureTime {
      withTimeoutOrNull(maxDelay) {
        job.join()
      } ?: logger.info { "Timed-out after waiting $maxDelay" }
    }.also {
      logger.info { "Continued start-up after delaying $it" }
    }
  }

  // readContentDsl() is passed as a lambda because it is Application.readContentDsl()
  val resetContentDslFunc = { logId: String -> readContentDsl(dslFileName, dslVariableName, logId) }

  LoggingWs.initThreads({ content.get() }, resetContentDslFunc)

  installs(isProduction())

  intercepts()

  routing {
    adminRoutes(metrics)
    locations(metrics) { content.get() }
    userRoutes(metrics) { content.get() }
    sysAdminRoutes(metrics, resetContentDslFunc)
    wsRoutes(metrics) { content.get() }
    static(STATIC_ROOT) { resources("static") }
  }
}

private fun Application.assignProperties() {

  val agentEnabled = AGENT_ENABLED.getEnv(AGENT_ENABLED_PROPERTY.configValue(this, default = "false").toBoolean())
  AGENT_ENABLED_PROPERTY.setProperty(agentEnabled.toString())
  PROXY_HOSTNAME.setPropertyFromConfig(this, "")

  IS_PRODUCTION.setProperty(IS_PRODUCTION.configValue(this, "false").toBoolean().toString())
  POSTGRES_ENABLED.setProperty(POSTGRES_ENABLED.configValue(this, "false").toBoolean().toString())
  SAVE_REQUESTS_ENABLED.setProperty(SAVE_REQUESTS_ENABLED.configValue(this, "true").toBoolean().toString())
  MULTI_SERVER_ENABLED.setProperty(MULTI_SERVER_ENABLED.configValue(this, "false").toBoolean().toString())
  CONTENT_CACHING_ENABLED.setProperty(CONTENT_CACHING_ENABLED.configValue(this, "false").toBoolean().toString())

  DSL_FILE_NAME.setPropertyFromConfig(this, "src/Content.kt")
  DSL_VARIABLE_NAME.setPropertyFromConfig(this, "getContent")

  ANALYTICS_ID.setPropertyFromConfig(this, "")

  PINGDOM_BANNER_ID.setPropertyFromConfig(this, "")
  PINGDOM_URL.setPropertyFromConfig(this, "")
  STATUS_PAGE_URL.setPropertyFromConfig(this, "")

  PROMETHEUS_URL.setPropertyFromConfig(this, "")
  GRAFANA_URL.setPropertyFromConfig(this, "")

  JAVA_SCRIPTS_POOL_SIZE.setPropertyFromConfig(this, "5")
  KOTLIN_SCRIPTS_POOL_SIZE.setPropertyFromConfig(this, "5")
  PYTHON_SCRIPTS_POOL_SIZE.setPropertyFromConfig(this, "5")

  DBMS_DRIVER_CLASSNAME.setPropertyFromConfig(this, "com.impossibl.postgres.jdbc.PGDriver")
  DBMS_URL.setPropertyFromConfig(this, "jdbc:pgsql://localhost:5432/postgres")
  DBMS_USERNAME.setPropertyFromConfig(this, "postgres")
  DBMS_PASSWORD.setPropertyFromConfig(this, "")
  DBMS_MAX_POOL_SIZE.setPropertyFromConfig(this, "10")

  REDIS_MAX_POOL_SIZE.setPropertyFromConfig(this, "10")
  REDIS_MAX_IDLE_SIZE.setPropertyFromConfig(this, "5")
  REDIS_MIN_IDLE_SIZE.setPropertyFromConfig(this, "1")

  KTOR_PORT.setPropertyFromConfig(this, "0")
  KTOR_WATCH.setProperty(KTOR_WATCH.configValueOrNull(this)?.getList()?.toString() ?: UNASSIGNED)

  SENDGRID_PREFIX_PROPERTY.setProperty(
    SENDGRID_PREFIX.getEnv(SENDGRID_PREFIX_PROPERTY.configValue(this, "https://www.readingbat.com")))
}
