/*
 * Copyright © 2021 Paul Ambrose (pambrose@mac.com)
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

import com.github.pambrose.common.redis.RedisUtils
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
import io.ktor.server.application.*
import io.ktor.server.config.*
import mu.KLogging
import java.util.concurrent.atomic.AtomicBoolean

enum class Property(
  val propertyValue: String,
  val maskFunc: Property.() -> String = { getProperty(UNASSIGNED, false) }
) {
  KOTLIN_SCRIPT_CLASSPATH("kotlin.script.classpath"),

  CONFIG_FILENAME("$READINGBAT.configFilename"),
  ADMIN_USERS("$READINGBAT.adminUsers"),
  LOGBACK_CONFIG_FILE("logback.configurationFile"),
  AGENT_LAUNCH_ID("$AGENT.launchId"),

  AGENT_CONFIG("agent.config"),

  // These are used in module()
  DSL_FILE_NAME("$READINGBAT.$CONTENT.fileName"),
  DSL_VARIABLE_NAME("$READINGBAT.$CONTENT.variableName"),
  PROXY_HOSTNAME("$AGENT.proxy.hostname"),
  STARTUP_DELAY_SECS("$READINGBAT.$SITE.startupMaxDelaySecs"),

  // These are defaults for env var values
  REDIRECT_HOSTNAME("$READINGBAT.$SITE.redirectHostname"),
  SENDGRID_PREFIX("$READINGBAT.$SITE.sendGridPrefix"),
  FORWARDED_ENABLED("$READINGBAT.$SITE.forwardedHeaderSupportEnabled"),
  XFORWARDED_ENABLED("$READINGBAT.$SITE.xforwardedHeaderSupportEnabled"),

  // These are assigned to ReadingBatContent vals
  ANALYTICS_ID("$READINGBAT.$SITE.googleAnalyticsId", { getPropertyOrNull(false) ?: UNASSIGNED }),
  MAX_HISTORY_LENGTH("$READINGBAT.$CHALLENGES.maxHistoryLength"),
  MAX_CLASS_COUNT("$READINGBAT.$CLASSES.maxCount"),
  KTOR_PORT("ktor.deployment.port"),
  KTOR_WATCH("ktor.deployment.watch"),

  // These are assigned in ReadingBatServer
  IS_PRODUCTION("$READINGBAT.$SITE.production"),
  IS_TESTING("$READINGBAT.$SITE.testing"),
  DBMS_ENABLED("$READINGBAT.$SITE.dbmsEnabled"),
  REDIS_ENABLED("$READINGBAT.$SITE.redisEnabled"),
  SAVE_REQUESTS_ENABLED("$READINGBAT.$SITE.saveRequestsEnabled"),
  MULTI_SERVER_ENABLED("$READINGBAT.$SITE.multiServerEnabled"),
  CONTENT_CACHING_ENABLED("$READINGBAT.$SITE.contentCachingEnabled"),
  AGENT_ENABLED("$AGENT.enabled"),

  PINGDOM_BANNER_ID("$READINGBAT.$SITE.pingdomBannerId", { getPropertyOrNull(false) ?: UNASSIGNED }),
  PINGDOM_URL("$READINGBAT.$SITE.pingdomUrl", { getPropertyOrNull(false) ?: UNASSIGNED }),
  STATUS_PAGE_URL("$READINGBAT.$SITE.statusPageUrl", { getPropertyOrNull(false) ?: UNASSIGNED }),

  PROMETHEUS_URL("$READINGBAT.prometheus.url"),
  GRAFANA_URL("$READINGBAT.grafana.url"),

  JAVA_SCRIPTS_POOL_SIZE("$READINGBAT.scripts.javaPoolSize"),
  KOTLIN_SCRIPTS_POOL_SIZE("$READINGBAT.scripts.kotlinPoolSize"),
  PYTHON_SCRIPTS_POOL_SIZE("$READINGBAT.scripts.pythonPoolSize"),

  KOTLIN_EVALUATORS_POOL_SIZE("$READINGBAT.evaluators.kotlinPoolSize"),
  PYTHON_EVALUATORS_POOL_SIZE("$READINGBAT.evaluators.pythonPoolSize"),

  DBMS_DRIVER_CLASSNAME("$DBMS.driverClassName"),
  DBMS_URL("$DBMS.jdbcUrl"),
  DBMS_USERNAME("$DBMS.username"),
  DBMS_PASSWORD("$DBMS.password", { getPropertyOrNull(false)?.obfuscate(1) ?: UNASSIGNED }),
  DBMS_MAX_POOL_SIZE("$DBMS.maxPoolSize"),
  DBMS_MAX_LIFETIME_MINS("$DBMS.maxLifetimeMins"),

  REDIS_MAX_POOL_SIZE(RedisUtils.REDIS_MAX_POOL_SIZE),
  REDIS_MAX_IDLE_SIZE(RedisUtils.REDIS_MAX_IDLE_SIZE),
  REDIS_MIN_IDLE_SIZE(RedisUtils.REDIS_MIN_IDLE_SIZE),
  ;

  private fun Application.configProperty(name: String, default: String = "", warn: Boolean = false) =
    try {
      environment.config.property(name).getString()
    } catch (e: ApplicationConfigurationException) {
      if (warn)
        logger.warn { "Missing $name value in application.conf" }
      default
    }

  fun configValue(application: Application, default: String = "", warn: Boolean = false) =
    application.configProperty(propertyValue, default, warn)

  fun configValueOrNull(application: Application) =
    application.environment.config.propertyOrNull(propertyValue)

  fun getProperty(default: String, errorOnNonInit: Boolean = true) =
    (System.getProperty(propertyValue)
      ?: default).also { if (errorOnNonInit && !initialized.get()) error(notInitialized(this)) }

  fun getProperty(default: Boolean) = (System.getProperty(propertyValue)?.toBoolean()
    ?: default).also { if (!initialized.get()) error(notInitialized(this)) }

  fun getProperty(default: Int) = (System.getProperty(propertyValue)?.toIntOrNull()
    ?: default).also { if (!initialized.get()) error(notInitialized(this)) }

  fun getPropertyOrNull(errorOnNonInit: Boolean = true): String? =
    System.getProperty(propertyValue).also { if (errorOnNonInit && !initialized.get()) error(notInitialized(this)) }

  fun getRequiredProperty() = (getPropertyOrNull()
    ?: error("Missing $propertyValue value")).also { if (!initialized.get()) error(notInitialized(this)) }

  fun setProperty(value: String) {
    System.setProperty(propertyValue, value)
    logger.info { "$propertyValue: ${maskFunc()}" }
  }

  fun setPropertyFromConfig(application: Application, default: String) {
    if (isNotDefined())
      setProperty(configValue(application, default))
  }

  fun isDefined() = System.getProperty(propertyValue).isNotNull()
  fun isNotDefined() = !isDefined()

  companion object : KLogging() {
    private val initialized = AtomicBoolean(false)

    fun assignInitialized() = initialized.set(true)

    private fun notInitialized(prop: Property) = "Property ${prop.name} not initialized"

    internal fun Application.assignProperties() {

      val agentEnabled =
        EnvVar.AGENT_ENABLED.getEnv(AGENT_ENABLED.configValue(this, default = "false").toBoolean())
      AGENT_ENABLED.setProperty(agentEnabled.toString())
      PROXY_HOSTNAME.setPropertyFromConfig(this, "")

      IS_PRODUCTION.also { it.setProperty(it.configValue(this, "false").toBoolean().toString()) }

      DBMS_ENABLED.also { it.setProperty(it.configValue(this, "false").toBoolean().toString()) }
      REDIS_ENABLED.also { it.setProperty(it.configValue(this, "false").toBoolean().toString()) }

      SAVE_REQUESTS_ENABLED.also { it.setProperty(it.configValue(this, "true").toBoolean().toString()) }

      MULTI_SERVER_ENABLED.also { it.setProperty(it.configValue(this, "false").toBoolean().toString()) }

      CONTENT_CACHING_ENABLED.also { it.setProperty(it.configValue(this, "false").toBoolean().toString()) }

      DSL_FILE_NAME.setPropertyFromConfig(this, "src/Content.kt")
      DSL_VARIABLE_NAME.setPropertyFromConfig(this, "content")

      ANALYTICS_ID.setPropertyFromConfig(this, "")

      PINGDOM_BANNER_ID.setPropertyFromConfig(this, "")
      PINGDOM_URL.setPropertyFromConfig(this, "")
      STATUS_PAGE_URL.setPropertyFromConfig(this, "")

      PROMETHEUS_URL.setPropertyFromConfig(this, "")
      GRAFANA_URL.setPropertyFromConfig(this, "")

      JAVA_SCRIPTS_POOL_SIZE.setPropertyFromConfig(this, "5")
      KOTLIN_SCRIPTS_POOL_SIZE.setPropertyFromConfig(this, "5")
      PYTHON_SCRIPTS_POOL_SIZE.setPropertyFromConfig(this, "5")

      KOTLIN_EVALUATORS_POOL_SIZE.setPropertyFromConfig(this, "5")
      PYTHON_EVALUATORS_POOL_SIZE.setPropertyFromConfig(this, "5")

      DBMS_DRIVER_CLASSNAME.setPropertyFromConfig(this, "com.impossibl.postgres.jdbc.PGDriver")
      DBMS_URL.setPropertyFromConfig(this, "jdbc:pgsql://localhost:5432/readingbat")
      DBMS_USERNAME.setPropertyFromConfig(this, "postgres")
      DBMS_PASSWORD.setPropertyFromConfig(this, "")
      DBMS_MAX_POOL_SIZE.setPropertyFromConfig(this, "10")
      DBMS_MAX_LIFETIME_MINS.setPropertyFromConfig(this, "30")

      REDIS_MAX_POOL_SIZE.setPropertyFromConfig(this, "10")
      REDIS_MAX_IDLE_SIZE.setPropertyFromConfig(this, "5")
      REDIS_MIN_IDLE_SIZE.setPropertyFromConfig(this, "1")

      KTOR_PORT.setPropertyFromConfig(this, "0")
      KTOR_WATCH.also { it.setProperty(it.configValueOrNull(this)?.getList()?.toString() ?: UNASSIGNED) }

      SENDGRID_PREFIX.also {
        it.setProperty(
          EnvVar.SENDGRID_PREFIX.getEnv(
            it.configValue(
              this,
              "https://www.readingbat.com"
            )
          )
        )
      }

      assignInitialized()
    }
  }
}