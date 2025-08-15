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

import com.github.pambrose.common.util.FileSource
import com.github.pambrose.common.util.Version
import com.github.pambrose.common.util.Version.Companion.versionDesc
import com.github.pambrose.common.util.getBanner
import com.github.pambrose.common.util.isNotNull
import com.github.pambrose.common.util.isNull
import com.github.pambrose.common.util.randomId
import com.github.readingbat.BuildConfig
import com.github.readingbat.common.Constants.UNKNOWN_USER_ID
import com.github.readingbat.common.Endpoints.STATIC_ROOT
import com.github.readingbat.common.EnvVar
import com.github.readingbat.common.EnvVar.CLOUD_SQL_CONNECTION_NAME
import com.github.readingbat.common.EnvVar.SCRIPT_CLASSPATH
import com.github.readingbat.common.KtorProperty.Companion.assignProperties
import com.github.readingbat.common.Metrics
import com.github.readingbat.common.Property
import com.github.readingbat.common.Property.CONFIG_FILENAME
import com.github.readingbat.common.Property.Companion.initProperties
import com.github.readingbat.common.Property.KOTLIN_SCRIPT_CLASSPATH
import com.github.readingbat.common.User.Companion.createUnknownUser
import com.github.readingbat.common.User.Companion.userExists
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.dsl.agentLaunchId
import com.github.readingbat.dsl.evalContentDsl
import com.github.readingbat.dsl.isAgentEnabled
import com.github.readingbat.dsl.isDbmsEnabled
import com.github.readingbat.dsl.isProduction
import com.github.readingbat.dsl.readContentDsl
import com.github.readingbat.server.Installs.installs
import com.github.readingbat.server.Locations.locations
import com.github.readingbat.server.ReadingBatServer.adminUsers
import com.github.readingbat.server.ReadingBatServer.assignKotlinScriptProperty
import com.github.readingbat.server.ReadingBatServer.content
import com.github.readingbat.server.ReadingBatServer.contentReadCount
import com.github.readingbat.server.ReadingBatServer.logger
import com.github.readingbat.server.ReadingBatServer.metrics
import com.github.readingbat.server.ServerUtils.logToShim
import com.github.readingbat.server.routes.AdminRoutes.adminRoutes
import com.github.readingbat.server.routes.sysAdminRoutes
import com.github.readingbat.server.routes.userRoutes
import com.github.readingbat.server.ws.LoggingWs
import com.github.readingbat.server.ws.WsCommon.wsRoutes
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.Application
import io.ktor.server.cio.EngineMain
import io.ktor.server.engine.CommandLineConfig
import io.ktor.server.http.content.staticResources
import io.ktor.server.routing.routing
import io.prometheus.Agent.Companion.startAsyncAgent
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.exposed.sql.Database
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.script.ScriptEngineManager
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.plusAssign
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlin.time.measureTime

@Version(
  version = BuildConfig.CORE_VERSION,
  releaseDate = BuildConfig.CORE_RELEASE_DATE,
  buildTime = BuildConfig.BUILD_TIME,
)
object ReadingBatServer {
  internal val logger = KotlinLogging.logger {}
  val metrics by lazy { Metrics() }
  private const val CALLER_VERSION = "callerVersion"
  private val startTime = TimeSource.Monotonic.markNow()
  internal val serverSessionId = randomId(10)
  internal val timeStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("M/d/y H:m:ss"))
  internal var callerVersion = ""
  internal val content = AtomicReference(ReadingBatContent())
  internal val adminUsers = mutableListOf<String>()
  internal val contentReadCount = AtomicInt(0)
  internal val dbms by lazy {
    Database.connect(
      HikariDataSource(
        HikariConfig()
          .apply {
            driverClassName = EnvVar.DBMS_DRIVER_CLASSNAME.getEnv(Property.DBMS_DRIVER_CLASSNAME.getRequiredProperty())
            jdbcUrl = EnvVar.DBMS_URL.getEnv(Property.DBMS_URL.getRequiredProperty())
            username = EnvVar.DBMS_USERNAME.getEnv(Property.DBMS_USERNAME.getRequiredProperty())
            password = EnvVar.DBMS_PASSWORD.getEnv(Property.DBMS_PASSWORD.getRequiredProperty())

            CLOUD_SQL_CONNECTION_NAME.getEnv("")
              .also {
                if (it.isNotBlank()) {
                  addDataSourceProperty("cloudSqlInstance", it)
                  addDataSourceProperty("socketFactory", "com.google.cloud.sql.postgres.SocketFactory")
                }
              }

            maximumPoolSize = Property.DBMS_MAX_POOL_SIZE.getRequiredProperty().toInt()
            // This causes problems. Postgres defaults to false, but setting it here starts a transaction and then
            // subsequent readOnly sets throw exceptions
            // isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            maxLifetime = Property.DBMS_MAX_LIFETIME_MINS.getRequiredProperty().toInt().minutes.inWholeMilliseconds
            validate()
          },
      ),
    )
  }

  internal val upTime get() = startTime.elapsedNow()

  fun assignKotlinScriptProperty() {
    // If kotlin.script.classpath property is missing, set it based on env var SCRIPT_CLASSPATH
    // This has to take place before reading DSL
    val scriptClasspathProp = KOTLIN_SCRIPT_CLASSPATH.getPropertyOrNull()
    if (scriptClasspathProp.isNull()) {
      val scriptClasspathEnvVar = SCRIPT_CLASSPATH.getEnvOrNull()
      if (scriptClasspathEnvVar.isNotNull())
        KOTLIN_SCRIPT_CLASSPATH.setProperty(scriptClasspathEnvVar)
      else
        logger.warn { "Missing ${KOTLIN_SCRIPT_CLASSPATH.propertyName} and $SCRIPT_CLASSPATH values" }
    } else {
      logger.info { "${KOTLIN_SCRIPT_CLASSPATH.propertyName}: $scriptClasspathProp" }
    }
  }

  @Suppress("unused")
  fun configEnvironment(arg: String) = CommandLineConfig(withConfigArg(arg))

  private fun withConfigArg(arg: String) = deriveArgs(arrayOf("-config=$arg"))

  private fun callerVersion(args: Array<String>) =
    args.asSequence()
      .filter { it.startsWith("-$CALLER_VERSION=") }
      .map { it.replaceFirst("-$CALLER_VERSION=", "") }
      .firstOrNull() ?: "None specified"

  private fun deriveArgs(args: Array<String>): Array<String> {
    // Grab the config filename from CLI args and then try ENV var
    val configFilename =
      args.asSequence()
        .filter { it.startsWith("-config=") }
        .map { it.replaceFirst("-config=", "") }
        .firstOrNull()
        ?: EnvVar.AGENT_CONFIG.getEnvOrNull()
        ?: Property.AGENT_CONFIG.getPropertyOrNull(false)
        ?: "src/main/resources/application.conf"

    CONFIG_FILENAME.setProperty(configFilename)

    return if (args.any { it.startsWith("-config=") })
      args
    else
      args.toMutableList().apply { add("-config=$configFilename") }.toTypedArray()
  }

  fun start(callerVersion: String, args: Array<String>) {
    start(args + arrayOf("-$CALLER_VERSION=$callerVersion"))
  }

  fun start(args: Array<String>) {
    logger.apply {
      info { getBanner("banners/readingbat.banner", this) }
      info { ReadingBatServer::class.versionDesc() }
      callerVersion = callerVersion(args)
      info { "Caller Version: $callerVersion" }
    }

    EngineMain.main(deriveArgs(args))
  }
}

internal fun Application.readContentDsl(fileName: String, variableName: String, logId: String = "") {
  "Loading content using $variableName in $fileName".also {
    logger.info { it }
    logToShim(it, logId)
  }

  measureTime {
    val contentSource = FileSource(fileName = fileName)
    val dslCode = readContentDsl(contentSource)
    content.store(
      evalContentDsl(contentSource.source, variableName, dslCode)
        .apply {
          maxHistoryLength = Property.MAX_HISTORY_LENGTH.configValue(this@readContentDsl, "10").toInt()
          maxClassCount = Property.MAX_CLASS_COUNT.configValue(this@readContentDsl, "25").toInt()
        }
        .apply { clearContentMap() },
    )
    metrics.contentLoadedCount.labels(agentLaunchId()).inc()
  }.also { dur ->
    "Loaded content using $variableName in $fileName in $dur"
      .also {
        logger.info { it }
        logToShim(it, logId)
      }
  }

  contentReadCount += 1
}

fun Application.module() {
  assignProperties(initProperties().sortedBy { it.propertyName })

  // Verifu all the script engines loaded
  logger.info { "Loaded script engines: ${ScriptEngineManager().engineFactories.map { it.engineName }}" }
  check(ScriptEngineManager().engineFactories.count() == 3) { "Missing script engines" }

  adminUsers.addAll(Property.ADMIN_USERS.configValueOrNull(this)?.getList() ?: emptyList())

  if (isDbmsEnabled()) {
    ReadingBatServer.dbms

    // Create unknown user if it does not already exist
    if (!userExists(UNKNOWN_USER_ID))
      createUnknownUser(UNKNOWN_USER_ID)
  }

  if (isAgentEnabled()) {
    if (Property.PROXY_HOSTNAME.getRequiredProperty().isNotEmpty()) {
      val configFilename = CONFIG_FILENAME.getRequiredProperty()
      val agentInfo = startAsyncAgent(configFilename, true)
      Property.AGENT_LAUNCH_ID.setProperty(agentInfo.launchId)
    } else {
      logger.error { "Prometheus agent is enabled but the proxy hostname is not assigned" }
    }
  }

  // This is done *after* AGENT_LAUNCH_ID is assigned because metrics depend on it
  metrics.init { content.load() }

  assignKotlinScriptProperty()

  val dslFileName = Property.DSL_FILE_NAME.getRequiredProperty()
  val dslVariableName = Property.DSL_VARIABLE_NAME.getRequiredProperty()

  val job = launch { readContentDsl(dslFileName, dslVariableName) }

  runBlocking {
    val maxDelay = Property.STARTUP_DELAY_SECS.configValue(this@module, "60").toInt().seconds
    logger.info { "Delaying start-up by max of $maxDelay" }
    measureTime {
      withTimeoutOrNull(maxDelay) {
        job.join()
      } ?: logger.error { "Timed-out after waiting $maxDelay" }
    }.also {
      logger.info { "Continued start-up after delaying $it" }
    }
  }

  // readContentDsl() is passed as a lambda because it is Application.readContentDsl()
  val resetContentDslFunc = { logId: String -> readContentDsl(dslFileName, dslVariableName, logId) }

  LoggingWs.initThreads({ content.load() }, resetContentDslFunc)

  installs(isProduction())

  intercepts()

  routing {
    adminRoutes(metrics)
    locations(metrics) { content.load() }
    userRoutes(metrics) { content.load() }

    if (isProduction()) {
      sysAdminRoutes(metrics, resetContentDslFunc)
      wsRoutes(metrics) { content.load() }
    }

    staticResources(STATIC_ROOT, "static")
    staticResources("/", "public")
  }
}
