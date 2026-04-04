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

package com.readingbat.common

import com.pambrose.common.email.Email
import com.pambrose.common.email.Email.Companion.toResendEmail
import com.pambrose.common.util.obfuscate
import com.readingbat.common.Constants.UNASSIGNED
import com.readingbat.common.KtorProperty.Companion.assignProperties
import com.readingbat.common.KtorProperty.Companion.configStore
import com.readingbat.common.PropertyNames.AGENT
import com.readingbat.common.PropertyNames.CHALLENGES
import com.readingbat.common.PropertyNames.CLASSES
import com.readingbat.common.PropertyNames.CONTENT
import com.readingbat.common.PropertyNames.DBMS
import com.readingbat.common.PropertyNames.READINGBAT
import com.readingbat.common.PropertyNames.SITE
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.Application
import io.ktor.server.config.ApplicationConfigurationException
import kotlin.concurrent.atomics.AtomicBoolean

/**
 * Base class for Ktor application configuration properties backed by HOCON.
 *
 * Properties are read from `application.conf` via Ktor's `ApplicationConfig` and stored in a
 * thread-safe in-memory [configStore]. Each property has an optional initialization function
 * (to set defaults from config) and a mask function (to redact sensitive values in logs).
 *
 * Properties must be initialized via [assignProperties] before access; reading an uninitialized
 * property with `errorOnNonInit=true` throws an error.
 *
 * @property propertyName The HOCON property path (e.g., "readingbat.site.production").
 * @property initFunc Called during application startup to initialize this property's value.
 * @property maskFunc Returns a display-safe representation of the property value (for logging).
 */
open class KtorProperty(
  val propertyName: String,
  val initFunc: KtorProperty.(application: Application) -> Unit = {},
  val maskFunc: KtorProperty.() -> String = { getProperty(UNASSIGNED, false) },
) {
  init {
    require(propertyName.isNotBlank()) { "Property name cannot be blank" }
    instances += this
  }

  private fun Application.configProperty(name: String, default: String = "", warn: Boolean = false) =
    try {
      environment.config.property(name).getString()
    } catch (e: ApplicationConfigurationException) {
      if (warn)
        logger.warn { "Missing $name value in application.conf" }
      default
    }

  fun initProperty(application: Application) = initFunc(application)

  /** Reads this property's value from the Ktor application configuration, with a fallback default. */
  fun configValue(application: Application, default: String = "", warn: Boolean = false) =
    application.configProperty(propertyName, default, warn)

  fun configValueOrNull(application: Application) =
    application.environment.config.propertyOrNull(propertyName)

  /** Returns this property's stored value as a String, or [default] if not set. */
  fun getProperty(default: String, errorOnNonInit: Boolean = true) =
    (configStore[propertyName] ?: default).also {
      if (errorOnNonInit && !initialized.load())
        error(notInitialized(this))
    }

  fun getProperty(default: Boolean, errorOnNonInit: Boolean = true) =
    (configStore[propertyName]?.toBoolean() ?: default).also {
      if (errorOnNonInit && !initialized.load())
        error(notInitialized(this))
    }

  fun getProperty(default: Int, errorOnNonInit: Boolean = true) =
    (configStore[propertyName]?.toIntOrNull() ?: default).also {
      if (errorOnNonInit && !initialized.load())
        error(notInitialized(this))
    }

  fun getPropertyOrNull(errorOnNonInit: Boolean = true): String? =
    configStore[propertyName].also { if (errorOnNonInit && !initialized.load()) error(notInitialized(this)) }

  fun getRequiredProperty() =
    (
      getPropertyOrNull() ?: error("Missing $propertyName value")
      ).also { if (!initialized.load()) error(notInitialized(this)) }

  /** Stores a value for this property and logs the assignment. */
  fun setProperty(value: String) {
    configStore[propertyName] = value
    logger.info { "$this" }
  }

  fun setPropertyFromConfig(application: Application, default: String) {
    if (!isADefinedProperty())
      setProperty(configValue(application, default))
  }

  fun isADefinedProperty() = configStore.containsKey(propertyName)

  val name: String get() = this::class.simpleName!!

  override fun toString() = "$name: $propertyName=${maskFunc(this)}"

  companion object {
    private val logger = KotlinLogging.logger {}
    private val initialized = AtomicBoolean(false)
    private val instances = mutableListOf<KtorProperty>()
    internal val configStore = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun assignInitialized() = initialized.store(true)

    fun resetForTesting() {
      configStore.clear()
      initialized.store(false)
    }

    private fun notInitialized(prop: KtorProperty) = "Property ${prop.name} not initialized"

    internal fun values() = instances

    /** Initializes all given properties from the application config and marks the system as initialized. */
    internal fun Application.assignProperties(props: List<KtorProperty>) {
      props.forEach {
        it.initProperty(this@assignProperties)
      }
      assignInitialized()
    }
  }
}

/**
 * Sealed class hierarchy of all ReadingBat HOCON configuration properties.
 *
 * Each singleton object represents a specific configuration setting. Properties fall into
 * several categories:
 * - **Content DSL**: [DSL_FILE_NAME], [DSL_VARIABLE_NAME] -- control content loading
 * - **Site behavior**: [IS_PRODUCTION], [DBMS_ENABLED], [MULTI_SERVER_ENABLED], etc.
 * - **Database**: [DBMS_URL], [DBMS_USERNAME], [DBMS_PASSWORD], [DBMS_MAX_POOL_SIZE], etc.
 * - **OAuth**: [GITHUB_OAUTH_CLIENT_ID], [GOOGLE_OAUTH_CLIENT_ID], and their secrets
 * - **Email**: [RESEND_API_KEY], [RESEND_SENDER_EMAIL]
 * - **Script pools**: [JAVA_SCRIPTS_POOL_SIZE], [KOTLIN_SCRIPTS_POOL_SIZE], [PYTHON_SCRIPTS_POOL_SIZE]
 * - **Monitoring**: [ANALYTICS_ID], [PROMETHEUS_URL], [GRAFANA_URL], [PINGDOM_BANNER_ID]
 *
 * Values can be overridden by [EnvVar] environment variables using the pattern:
 * `EnvVar.X.getEnv(Property.X.configValue(...))`.
 */
sealed class Property(
  propertyValue: String,
  initFunc: KtorProperty.(application: Application) -> Unit = {},
  maskFunc: KtorProperty.() -> String = { getProperty(UNASSIGNED, false) },
) : KtorProperty(propertyValue, initFunc, maskFunc) {
  object KOTLIN_SCRIPT_CLASSPATH :
    Property(propertyValue = "kotlin.script.classpath")

  object CONFIG_FILENAME :
    Property(propertyValue = "$READINGBAT.configFilename")

  object ADMIN_USERS :
    Property(propertyValue = "$READINGBAT.adminUsers")

  object LOGBACK_CONFIG_FILE :
    Property(propertyValue = "logback.configurationFile")

  object AGENT_LAUNCH_ID :
    Property(propertyValue = "$AGENT.launchId")

  object AGENT_CONFIG : Property(propertyValue = "agent.config")

  // These are used in module()
  object DSL_FILE_NAME :
    Property(
      propertyValue = "$READINGBAT.$CONTENT.fileName",
      initFunc = { setPropertyFromConfig(it, "src/Content.kt") },
    )

  object DSL_VARIABLE_NAME :
    Property(
      propertyValue = "$READINGBAT.$CONTENT.variableName",
      initFunc = { setPropertyFromConfig(it, "content") },
    )

  object PROXY_HOSTNAME :
    Property(
      propertyValue = "$AGENT.proxy.hostname",
      initFunc = { setPropertyFromConfig(it, "") },
    )

  object STARTUP_DELAY_SECS :
    Property(propertyValue = "$READINGBAT.$SITE.startupMaxDelaySecs")

  // These are defaults for env var values
  object REDIRECT_HOSTNAME :
    Property(propertyValue = "$READINGBAT.$SITE.redirectHostname")

  object OAUTH_CALLBACK_URL_PREFIX :
    Property(
      propertyValue = "$READINGBAT.$SITE.oauthCallbackUrlPrefix",
      initFunc = {
        setProperty(EnvVar.OAUTH_CALLBACK_URL_PREFIX.getEnv(configValue(it, "https://www.readingbat.com")))
      },
    )

  object RESEND_API_KEY :
    Property(
      propertyValue = "$READINGBAT.$SITE.resendApiKey",
      initFunc = { setProperty(EnvVar.RESEND_API_KEY.getEnv(configValue(it, ""))) },
      maskFunc = { getPropertyOrNull(false)?.obfuscate(4) ?: UNASSIGNED },
    )

  object RESEND_SENDER_EMAIL :
    Property(
      propertyValue = "$READINGBAT.$SITE.resendSenderEmail",
      initFunc = { setProperty(EnvVar.RESEND_SENDER_EMAIL.getEnv(configValue(it, ""))) },
    )

  object FORWARDED_ENABLED :
    Property(propertyValue = "$READINGBAT.$SITE.forwardedHeaderSupportEnabled")

  object XFORWARDED_ENABLED :
    Property(propertyValue = "$READINGBAT.$SITE.xforwardedHeaderSupportEnabled")

  // These are assigned to ReadingBatContent vals
  object ANALYTICS_ID :
    Property(
      propertyValue = "$READINGBAT.$SITE.googleAnalyticsId",
      initFunc = { setPropertyFromConfig(it, "") },
      maskFunc = { getPropertyOrNull(false) ?: UNASSIGNED },
    )

  object MAX_HISTORY_LENGTH :
    Property("$READINGBAT.$CHALLENGES.maxHistoryLength")

  object MAX_CLASS_COUNT :
    Property("$READINGBAT.$CLASSES.maxCount")

  object KTOR_PORT :
    Property(
      propertyValue = "ktor.deployment.port",
      initFunc = { setPropertyFromConfig(it, "0") },
    )

  object KTOR_WATCH :
    Property(
      propertyValue = "ktor.deployment.watch",
      initFunc = { setProperty(configValueOrNull(it)?.getList()?.toString() ?: UNASSIGNED) },
    )

  // These are assigned in ReadingBatServer
  object IS_PRODUCTION :
    Property(
      propertyValue = "$READINGBAT.$SITE.production",
      initFunc = { setProperty(configValue(it, "false").toBoolean().toString()) },
    )

  object IS_TESTING :
    Property("$READINGBAT.$SITE.testing")

  object DBMS_ENABLED :
    Property(
      propertyValue = "$READINGBAT.$SITE.dbmsEnabled",
      initFunc = { setProperty(configValue(it, "false").toBoolean().toString()) },
    )

  object SAVE_REQUESTS_ENABLED :
    Property(
      propertyValue = "$READINGBAT.$SITE.saveRequestsEnabled",
      initFunc = { setProperty(configValue(it, "true").toBoolean().toString()) },
    )

  object MULTI_SERVER_ENABLED :
    Property(
      propertyValue = "$READINGBAT.$SITE.multiServerEnabled",
      initFunc = { setProperty(configValue(it, "false").toBoolean().toString()) },
    )

  object CONTENT_CACHING_ENABLED :
    Property(
      propertyValue = "$READINGBAT.$SITE.contentCachingEnabled",
      initFunc = { setProperty(configValue(it, "false").toBoolean().toString()) },
    )

  object AGENT_ENABLED :
    Property(
      propertyValue = "$AGENT.enabled",
      initFunc = {
        val agentEnabled = EnvVar.AGENT_ENABLED.getEnv(configValue(it, default = "false").toBoolean())
        setProperty(agentEnabled.toString())
      },
    )

  object PINGDOM_BANNER_ID :
    Property(
      propertyValue = "$READINGBAT.$SITE.pingdomBannerId",
      initFunc = { setPropertyFromConfig(it, "") },
      maskFunc = { getPropertyOrNull(false) ?: UNASSIGNED },
    )

  object PINGDOM_URL :
    Property(
      propertyValue = "$READINGBAT.$SITE.pingdomUrl",
      initFunc = { setPropertyFromConfig(it, "") },
      maskFunc = { getPropertyOrNull(false) ?: UNASSIGNED },
    )

  object STATUS_PAGE_URL :
    Property(
      propertyValue = "$READINGBAT.$SITE.statusPageUrl",
      initFunc = { setPropertyFromConfig(it, "") },
      maskFunc = { getPropertyOrNull(false) ?: UNASSIGNED },
    )

  object PROMETHEUS_URL :
    Property(
      propertyValue = "$READINGBAT.prometheus.url",
      initFunc = { setPropertyFromConfig(it, "") },
    )

  object GRAFANA_URL :
    Property(
      propertyValue = "$READINGBAT.grafana.url",
      initFunc = { setPropertyFromConfig(it, "") },
    )

  object JAVA_SCRIPTS_POOL_SIZE :
    Property(
      propertyValue = "$READINGBAT.scripts.javaPoolSize",
      initFunc = { setPropertyFromConfig(it, "5") },
    )

  object KOTLIN_SCRIPTS_POOL_SIZE :
    Property(
      propertyValue = "$READINGBAT.scripts.kotlinPoolSize",
      initFunc = { setPropertyFromConfig(it, "5") },
    )

  object PYTHON_SCRIPTS_POOL_SIZE :
    Property(
      propertyValue = "$READINGBAT.scripts.pythonPoolSize",
      initFunc = { setPropertyFromConfig(it, "5") },
    )

  object KOTLIN_EVALUATORS_POOL_SIZE :
    Property(
      propertyValue = "$READINGBAT.evaluators.kotlinPoolSize",
      initFunc = { setPropertyFromConfig(it, "5") },
    )

  object PYTHON_EVALUATORS_POOL_SIZE :
    Property(
      propertyValue = "$READINGBAT.evaluators.pythonPoolSize",
      initFunc = { setPropertyFromConfig(it, "5") },
    )

  object DBMS_DRIVER_CLASSNAME :
    Property(
      propertyValue = "$DBMS.driverClassName",
      initFunc = { setPropertyFromConfig(it, "com.impossibl.postgres.jdbc.PGDriver") },
    )

  object DBMS_URL :
    Property(
      propertyValue = "$DBMS.jdbcUrl",
      initFunc = { setPropertyFromConfig(it, "jdbc:pgsql://localhost:5432/readingbat") },
    )

  object DBMS_USERNAME :
    Property(
      propertyValue = "$DBMS.username",
      initFunc = { setPropertyFromConfig(it, "postgres") },
    )

  object DBMS_PASSWORD : Property(
    propertyValue = "$DBMS.password",
    initFunc = { setPropertyFromConfig(it, "") },
    maskFunc = { getPropertyOrNull(false)?.obfuscate(1) ?: UNASSIGNED },
  )

  object DBMS_MAX_POOL_SIZE :
    Property(
      propertyValue = "$DBMS.maxPoolSize",
      initFunc = { setPropertyFromConfig(it, "10") },
    )

  object DBMS_MAX_LIFETIME_MINS :
    Property(
      propertyValue = "$DBMS.maxLifetimeMins",
      initFunc = { setPropertyFromConfig(it, "30") },
    )

  object GITHUB_OAUTH_CLIENT_ID :
    Property(
      propertyValue = "$READINGBAT.$SITE.githubOAuthClientId",
      initFunc = { setProperty(EnvVar.GITHUB_OAUTH_CLIENT_ID.getEnv(configValue(it, ""))) },
    )

  object GITHUB_OAUTH_CLIENT_SECRET :
    Property(
      propertyValue = "$READINGBAT.$SITE.githubOAuthClientSecret",
      initFunc = { setProperty(EnvVar.GITHUB_OAUTH_CLIENT_SECRET.getEnv(configValue(it, ""))) },
      maskFunc = { getPropertyOrNull(false)?.obfuscate(4) ?: UNASSIGNED },
    )

  object GOOGLE_OAUTH_CLIENT_ID :
    Property(
      propertyValue = "$READINGBAT.$SITE.googleOAuthClientId",
      initFunc = { setProperty(EnvVar.GOOGLE_OAUTH_CLIENT_ID.getEnv(configValue(it, ""))) },
    )

  object GOOGLE_OAUTH_CLIENT_SECRET :
    Property(
      propertyValue = "$READINGBAT.$SITE.googleOAuthClientSecret",
      initFunc = { setProperty(EnvVar.GOOGLE_OAUTH_CLIENT_SECRET.getEnv(configValue(it, ""))) },
      maskFunc = { getPropertyOrNull(false)?.obfuscate(4) ?: UNASSIGNED },
    )

  companion object {
    val envResendApiKey: String by lazy { RESEND_API_KEY.getRequiredProperty() }
    val envResendSender: Email by lazy { RESEND_SENDER_EMAIL.getRequiredProperty().toResendEmail() }

    /** Returns the ordered list of properties that should be initialized at application startup. */
    fun initProperties() =
      listOf(
        DSL_FILE_NAME,
        DSL_VARIABLE_NAME,
        PROXY_HOSTNAME,
        OAUTH_CALLBACK_URL_PREFIX,
        RESEND_API_KEY,
        RESEND_SENDER_EMAIL,
        ANALYTICS_ID,
        KTOR_PORT,
        KTOR_WATCH,
        IS_PRODUCTION,
        DBMS_ENABLED,
        SAVE_REQUESTS_ENABLED,
        MULTI_SERVER_ENABLED,
        CONTENT_CACHING_ENABLED,
        AGENT_ENABLED,
        PINGDOM_BANNER_ID,
        PINGDOM_URL,
        STATUS_PAGE_URL,
        PROMETHEUS_URL,
        GRAFANA_URL,
        JAVA_SCRIPTS_POOL_SIZE,
        KOTLIN_SCRIPTS_POOL_SIZE,
        PYTHON_SCRIPTS_POOL_SIZE,
        KOTLIN_EVALUATORS_POOL_SIZE,
        PYTHON_EVALUATORS_POOL_SIZE,
        DBMS_DRIVER_CLASSNAME,
        DBMS_URL,
        DBMS_USERNAME,
        DBMS_PASSWORD,
        DBMS_MAX_POOL_SIZE,
        DBMS_MAX_LIFETIME_MINS,
        GITHUB_OAUTH_CLIENT_ID,
        GITHUB_OAUTH_CLIENT_SECRET,
        GOOGLE_OAUTH_CLIENT_ID,
        GOOGLE_OAUTH_CLIENT_SECRET,
      )
  }
}
