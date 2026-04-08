/*
 * Copyright © 2026 Paul Ambrose (pambrose@mac.com)
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

package com.readingbat.server

import com.pambrose.common.util.FileSource
import com.pambrose.common.util.Version
import com.pambrose.common.util.Version.Companion.versionDesc
import com.pambrose.common.util.getBanner
import com.pambrose.common.util.randomId
import com.readingbat.BuildConfig
import com.readingbat.common.Constants.UNKNOWN_USER_ID
import com.readingbat.common.Endpoints.STATIC_ROOT
import com.readingbat.common.EnvVar
import com.readingbat.common.EnvVar.CLOUD_SQL_CONNECTION_NAME
import com.readingbat.common.EnvVar.SCRIPT_CLASSPATH
import com.readingbat.common.KtorProperty.Companion.assignProperties
import com.readingbat.common.Metrics
import com.readingbat.common.Property
import com.readingbat.common.Property.ADMIN_USERS
import com.readingbat.common.Property.CONFIG_FILENAME
import com.readingbat.common.Property.Companion.initProperties
import com.readingbat.common.Property.KOTLIN_SCRIPT_CLASSPATH
import com.readingbat.common.User.Companion.createUnknownUser
import com.readingbat.common.User.Companion.userExists
import com.readingbat.dsl.ReadingBatContent
import com.readingbat.dsl.agentLaunchId
import com.readingbat.dsl.evalContentDsl
import com.readingbat.dsl.isAgentEnabled
import com.readingbat.dsl.isDbmsEnabled
import com.readingbat.dsl.isProduction
import com.readingbat.dsl.readContentDsl
import com.readingbat.server.Installs.installs
import com.readingbat.server.Locations.locations
import com.readingbat.server.ReadingBatServer.adminUsersRef
import com.readingbat.server.ReadingBatServer.assignKotlinScriptProperty
import com.readingbat.server.ReadingBatServer.content
import com.readingbat.server.ReadingBatServer.contentReadCount
import com.readingbat.server.ReadingBatServer.logger
import com.readingbat.server.ReadingBatServer.metrics
import com.readingbat.server.ReadingBatServer.start
import com.readingbat.server.ServerUtils.logToShim
import com.readingbat.server.routes.AdminRoutes.adminRoutes
import com.readingbat.server.routes.oauthRoutes
import com.readingbat.server.routes.sysAdminRoutes
import com.readingbat.server.routes.userRoutes
import com.readingbat.server.ws.LoggingWs
import com.readingbat.server.ws.WsCommon.wsRoutes
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
import org.jetbrains.exposed.v1.jdbc.Database
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

/**
 * Main server singleton for the ReadingBat application.
 *
 * Manages the server lifecycle, configuration, database connection pool (HikariCP),
 * DSL content loading, Prometheus metrics, and admin user tracking. The [start] method
 * launches the Ktor CIO engine via [EngineMain].
 */
@Version(
  version = BuildConfig.CORE_VERSION,
  releaseDate = BuildConfig.CORE_RELEASE_DATE,
  buildTime = BuildConfig.BUILD_TIME,
)
object ReadingBatServer {
  private const val CALLER_VERSION = "callerVersion"
  private val startTime = TimeSource.Monotonic.markNow()

  internal val logger = KotlinLogging.logger {}
  internal val serverSessionId = randomId(10)
  internal val timeStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("M/d/y H:m:ss"))
  internal var callerVersion = ""
  internal val content = AtomicReference(ReadingBatContent())
  internal val adminUsersRef = AtomicReference<Set<String>>(emptySet())
  internal val contentReadCount = AtomicInt(0)

  /** Lazily initialized database connection using HikariCP connection pooling. */
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

  internal val adminUsers get() = adminUsersRef.load()

  internal val upTime get() = startTime.elapsedNow()

  val metrics by lazy { Metrics() }

  /**
   * Sets the `kotlin.script.classpath` system property from the [SCRIPT_CLASSPATH] environment
   * variable if it is not already configured. This must be called before reading the DSL content.
   */
  fun assignKotlinScriptProperty() {
    // If kotlin.script.classpath property is missing, set it based on env var SCRIPT_CLASSPATH
    // This has to take place before reading DSL
    val scriptClasspathProp = KOTLIN_SCRIPT_CLASSPATH.getPropertyOrNull()
    if (scriptClasspathProp == null) {
      val scriptClasspathEnvVar = SCRIPT_CLASSPATH.getEnvOrNull()
      if (scriptClasspathEnvVar != null)
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

  /** Starts the server with a caller-supplied version string appended to the CLI arguments. */
  fun start(callerVersion: String, args: Array<String>) {
    start(args + arrayOf("-$CALLER_VERSION=$callerVersion"))
  }

  /** Starts the Ktor CIO engine, printing the banner and version info before launch. */
  fun start(args: Array<String>) {
    logger.apply {
      info { getBanner("banners/readingbat.banner", this) }
      info { ReadingBatServer::class.versionDesc() }
      callerVersion = callerVersion(args)
      info { "Site Version: $callerVersion" }
    }

    EngineMain.main(deriveArgs(args))
  }
}

/**
 * Loads and evaluates the content DSL from [fileName], extracting the [ReadingBatContent]
 * instance bound to [variableName]. Increments [contentReadCount] and publishes metrics
 * on each successful load.
 */
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

/**
 * Ktor application module entry point. Initializes HOCON properties, the database,
 * Prometheus agent, metrics, DSL content, plugin installations, request intercepts,
 * and all route registrations (user, admin, OAuth, WebSocket, static resources).
 */
fun Application.module() {
  assignProperties(initProperties().sortedBy { it.propertyName })

  // Verify all the script engines loaded
  logger.info { "Loaded script engines: ${ScriptEngineManager().engineFactories.map { it.engineName }}" }
  check(ScriptEngineManager().engineFactories.count() == 3) { "Missing script engines" }

  adminUsersRef.store((ADMIN_USERS.configValueOrNull(this)?.getList() ?: emptyList()).toHashSet())

  if (isDbmsEnabled()) {
    ReadingBatServer.dbms

    // Create an unknown user if it does not already exist
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

    if (ConfigureOAuth.isOAuthConfigured) {
      oauthRoutes()
    }

    if (isProduction()) {
      sysAdminRoutes(metrics, resetContentDslFunc)
      wsRoutes(metrics) { content.load() }
    }

    staticResources(STATIC_ROOT, "static")
    staticResources("/", "public")
  }
}
