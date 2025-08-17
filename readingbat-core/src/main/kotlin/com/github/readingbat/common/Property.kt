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

package com.github.readingbat.common

import com.github.pambrose.common.email.Email
import com.github.pambrose.common.email.Email.Companion.toResendEmail
import com.github.pambrose.common.recaptcha.RecaptchaConfig
import com.github.pambrose.common.util.isNotNull
import com.github.pambrose.common.util.obfuscate
import com.github.readingbat.common.Constants.UNASSIGNED
import com.github.readingbat.common.PropertyNames.AGENT
import com.github.readingbat.common.PropertyNames.CHALLENGES
import com.github.readingbat.common.PropertyNames.CLASSES
import com.github.readingbat.common.PropertyNames.CONTENT
import com.github.readingbat.common.PropertyNames.DBMS
import com.github.readingbat.common.PropertyNames.READINGBAT
import com.github.readingbat.common.PropertyNames.SITE
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.Application
import io.ktor.server.config.ApplicationConfigurationException
import kotlin.concurrent.atomics.AtomicBoolean

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

  fun configValue(application: Application, default: String = "", warn: Boolean = false) =
    application.configProperty(propertyName, default, warn)

  fun configValueOrNull(application: Application) =
    application.environment.config.propertyOrNull(propertyName)

  fun getProperty(default: String, errorOnNonInit: Boolean = true) =
    (System.getProperty(propertyName) ?: default).also {
      if (errorOnNonInit && !initialized.load())
        error(notInitialized(this))
    }

  fun getProperty(default: Boolean, errorOnNonInit: Boolean = true) =
    (System.getProperty(propertyName)?.toBoolean() ?: default).also {
      if (errorOnNonInit && !initialized.load())
        error(notInitialized(this))
    }

  fun getProperty(default: Int, errorOnNonInit: Boolean = true) =
    (System.getProperty(propertyName)?.toIntOrNull() ?: default).also {
      if (errorOnNonInit && !initialized.load())
        error(notInitialized(this))
    }

  fun getPropertyOrNull(errorOnNonInit: Boolean = true): String? =
    System.getProperty(propertyName).also { if (errorOnNonInit && !initialized.load()) error(notInitialized(this)) }

  fun getRequiredProperty() =
    (
      getPropertyOrNull() ?: error("Missing $propertyName value")
      ).also { if (!initialized.load()) error(notInitialized(this)) }

  fun setProperty(value: String) {
    System.setProperty(propertyName, value)
    logger.info { "$this" }
  }

  fun setPropertyFromConfig(application: Application, default: String) {
    if (!isADefinedProperty())
      setProperty(configValue(application, default))
  }

  fun isADefinedProperty() = System.getProperty(propertyName).isNotNull()

  val name: String get() = this::class.simpleName!!

  override fun toString() = "$name: $propertyName=${maskFunc(this)}"

  companion object {
    private val logger = KotlinLogging.logger {}
    private val initialized = AtomicBoolean(false)
    private val instances = mutableListOf<KtorProperty>()

    fun assignInitialized() = initialized.store(true)

    private fun notInitialized(prop: KtorProperty) = "Property ${prop.name} not initialized"

    internal fun values() = instances

    internal fun Application.assignProperties(props: List<KtorProperty>) {
      props.forEach {
        it.initProperty(this@assignProperties)
      }
      assignInitialized()
    }
  }
}

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

  object EMAIL_PREFIX :
    Property(
      propertyValue = "$READINGBAT.$SITE.sendGridPrefix",
      initFunc = { setProperty(EnvVar.EMAIL_PREFIX.getEnv(configValue(it, "https://www.readingbat.com"))) },
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

  object RECAPTCHA_ENABLED :
    Property(
      propertyValue = "$READINGBAT.$SITE.recaptchaEnabled",
      initFunc = {
        val agentEnabled = EnvVar.RECAPTCHA_ENABLED.getEnv(configValue(it, default = "false").toBoolean())
        setProperty(agentEnabled.toString())
      },
    )

  object RECAPTCHA_SITE_KEY :
    Property(
      propertyValue = "$READINGBAT.$SITE.recaptchaSiteKey",
      initFunc = { setProperty(EnvVar.RECAPTCHA_SITE_KEY.getEnv(configValue(it, ""))) },
    )

  object RECAPTCHA_SECRET_KEY :
    Property(
      propertyValue = "$READINGBAT.$SITE.recaptchaSecretKey",
      initFunc = { setProperty(EnvVar.RECAPTCHA_SECRET_KEY.getEnv(configValue(it, ""))) },
      maskFunc = { getPropertyOrNull(false)?.obfuscate(4) ?: UNASSIGNED },
    )

  companion object {
    val envResendApiKey: String by lazy { Property.RESEND_API_KEY.getRequiredProperty() }
    val envResendSender: Email by lazy { Property.RESEND_SENDER_EMAIL.getRequiredProperty().toResendEmail() }

    val recaptchaConfig by lazy {
      object : RecaptchaConfig {
        override val isRecaptchaEnabled = RECAPTCHA_ENABLED.getProperty(default = false, errorOnNonInit = false)
        override val recaptchaSiteKey = RECAPTCHA_SITE_KEY.getPropertyOrNull(errorOnNonInit = false)
        override val recaptchaSecretKey = RECAPTCHA_SECRET_KEY.getPropertyOrNull(errorOnNonInit = false)
      }
    }

    fun initProperties() =
      listOf(
        DSL_FILE_NAME,
        DSL_VARIABLE_NAME,
        PROXY_HOSTNAME,
        EMAIL_PREFIX,
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
        RECAPTCHA_ENABLED,
        RECAPTCHA_SITE_KEY,
        RECAPTCHA_SECRET_KEY,
      )
  }
}
