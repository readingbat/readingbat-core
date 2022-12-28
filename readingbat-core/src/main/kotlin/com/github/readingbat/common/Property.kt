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
      if (errorOnNonInit && !initialized.get())
        error(notInitialized(this))
    }

  fun getProperty(default: Boolean) =
    (System.getProperty(propertyName)?.toBoolean() ?: default).also {
      if (!initialized.get())
        error(notInitialized(this))
    }

  fun getProperty(default: Int) =
    (System.getProperty(propertyName)?.toIntOrNull() ?: default).also {
      if (!initialized.get())
        error(notInitialized(this))
    }

  fun getPropertyOrNull(errorOnNonInit: Boolean = true): String? =
    System.getProperty(propertyName).also { if (errorOnNonInit && !initialized.get()) error(notInitialized(this)) }

  fun getRequiredProperty() = (getPropertyOrNull()
    ?: error("Missing $propertyName value")).also { if (!initialized.get()) error(notInitialized(this)) }

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

  companion object : KLogging() {
    private val initialized = AtomicBoolean(false)
    private val instances = mutableListOf<KtorProperty>()

    fun assignInitialized() = initialized.set(true)

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
  object KOTLIN_SCRIPT_CLASSPATH : Property("kotlin.script.classpath")

  object CONFIG_FILENAME : Property("$READINGBAT.configFilename")
  object ADMIN_USERS : Property("$READINGBAT.adminUsers")
  object LOGBACK_CONFIG_FILE : Property("logback.configurationFile")
  object AGENT_LAUNCH_ID : Property("$AGENT.launchId")

  object AGENT_CONFIG : Property("agent.config")

  // These are used in module()
  object DSL_FILE_NAME : Property("$READINGBAT.$CONTENT.fileName",
                                  initFunc = { setPropertyFromConfig(it, "src/Content.kt") })

  object DSL_VARIABLE_NAME : Property("$READINGBAT.$CONTENT.variableName",
                                      initFunc = { setPropertyFromConfig(it, "content") })

  object PROXY_HOSTNAME : Property("$AGENT.proxy.hostname",
                                   initFunc = { setPropertyFromConfig(it, "") })

  object STARTUP_DELAY_SECS : Property("$READINGBAT.$SITE.startupMaxDelaySecs")

  // These are defaults for env var values
  object REDIRECT_HOSTNAME : Property("$READINGBAT.$SITE.redirectHostname")
  object SENDGRID_PREFIX : Property("$READINGBAT.$SITE.sendGridPrefix",
                                    initFunc = {
                                      setProperty(
                                        EnvVar.SENDGRID_PREFIX.getEnv(configValue(it, "https://www.readingbat.com"))
                                      )
                                    })

  object FORWARDED_ENABLED : Property("$READINGBAT.$SITE.forwardedHeaderSupportEnabled")
  object XFORWARDED_ENABLED : Property("$READINGBAT.$SITE.xforwardedHeaderSupportEnabled")

  // These are assigned to ReadingBatContent vals
  object ANALYTICS_ID : Property("$READINGBAT.$SITE.googleAnalyticsId",
                                 initFunc = { setPropertyFromConfig(it, "") },
                                 maskFunc = { getPropertyOrNull(false) ?: UNASSIGNED })

  object MAX_HISTORY_LENGTH : Property("$READINGBAT.$CHALLENGES.maxHistoryLength")
  object MAX_CLASS_COUNT : Property("$READINGBAT.$CLASSES.maxCount")

  object KTOR_PORT : Property("ktor.deployment.port",
                              initFunc = { setPropertyFromConfig(it, "0") })

  object KTOR_WATCH : Property("ktor.deployment.watch",
                               initFunc = { setProperty(configValueOrNull(it)?.getList()?.toString() ?: UNASSIGNED) })

  // These are assigned in ReadingBatServer
  object IS_PRODUCTION : Property("$READINGBAT.$SITE.production",
                                  initFunc = { setProperty(configValue(it, "false").toBoolean().toString()) })

  object IS_TESTING : Property("$READINGBAT.$SITE.testing")

  object DBMS_ENABLED : Property("$READINGBAT.$SITE.dbmsEnabled",
                                 initFunc = { setProperty(configValue(it, "false").toBoolean().toString()) })

  object REDIS_ENABLED : Property("$READINGBAT.$SITE.redisEnabled",
                                  initFunc = { setProperty(configValue(it, "false").toBoolean().toString()) })

  object SAVE_REQUESTS_ENABLED : Property("$READINGBAT.$SITE.saveRequestsEnabled",
                                          initFunc = { setProperty(configValue(it, "true").toBoolean().toString()) })

  object MULTI_SERVER_ENABLED : Property("$READINGBAT.$SITE.multiServerEnabled",
                                         initFunc = { setProperty(configValue(it, "false").toBoolean().toString()) })

  object CONTENT_CACHING_ENABLED : Property("$READINGBAT.$SITE.contentCachingEnabled",
                                            initFunc = { setProperty(configValue(it, "false").toBoolean().toString()) })

  object AGENT_ENABLED :
    Property("$AGENT.enabled",
             initFunc = {
               val agentEnabled = EnvVar.AGENT_ENABLED.getEnv(configValue(it, default = "false").toBoolean())
               setProperty(agentEnabled.toString())
             })

  object PINGDOM_BANNER_ID : Property("$READINGBAT.$SITE.pingdomBannerId",
                                      initFunc = { setPropertyFromConfig(it, "") },
                                      maskFunc = { getPropertyOrNull(false) ?: UNASSIGNED })

  object PINGDOM_URL : Property("$READINGBAT.$SITE.pingdomUrl",
                                initFunc = { setPropertyFromConfig(it, "") },
                                maskFunc = { getPropertyOrNull(false) ?: UNASSIGNED })

  object STATUS_PAGE_URL : Property("$READINGBAT.$SITE.statusPageUrl",
                                    initFunc = { setPropertyFromConfig(it, "") },
                                    maskFunc = { getPropertyOrNull(false) ?: UNASSIGNED })

  object PROMETHEUS_URL : Property("$READINGBAT.prometheus.url",
                                   initFunc = { setPropertyFromConfig(it, "") })

  object GRAFANA_URL : Property("$READINGBAT.grafana.url",
                                initFunc = { setPropertyFromConfig(it, "") })

  object JAVA_SCRIPTS_POOL_SIZE : Property("$READINGBAT.scripts.javaPoolSize",
                                           initFunc = { setPropertyFromConfig(it, "5") })

  object KOTLIN_SCRIPTS_POOL_SIZE : Property("$READINGBAT.scripts.kotlinPoolSize",
                                             initFunc = { setPropertyFromConfig(it, "5") })

  object PYTHON_SCRIPTS_POOL_SIZE : Property("$READINGBAT.scripts.pythonPoolSize",
                                             initFunc = { setPropertyFromConfig(it, "5") })

  object KOTLIN_EVALUATORS_POOL_SIZE : Property("$READINGBAT.evaluators.kotlinPoolSize",
                                                initFunc = { setPropertyFromConfig(it, "5") })

  object PYTHON_EVALUATORS_POOL_SIZE : Property("$READINGBAT.evaluators.pythonPoolSize",
                                                initFunc = { setPropertyFromConfig(it, "5") })

  object DBMS_DRIVER_CLASSNAME :
    Property("$DBMS.driverClassName",
             initFunc = { setPropertyFromConfig(it, "com.impossibl.postgres.jdbc.PGDriver") })

  object DBMS_URL : Property("$DBMS.jdbcUrl",
                             initFunc = { setPropertyFromConfig(it, "jdbc:pgsql://localhost:5432/readingbat") })

  object DBMS_USERNAME : Property("$DBMS.username",
                                  initFunc = { setPropertyFromConfig(it, "postgres") })

  object DBMS_PASSWORD : Property("$DBMS.password",
                                  initFunc = { setPropertyFromConfig(it, "") },
                                  maskFunc = { getPropertyOrNull(false)?.obfuscate(1) ?: UNASSIGNED })

  object DBMS_MAX_POOL_SIZE : Property("$DBMS.maxPoolSize",
                                       initFunc = { setPropertyFromConfig(it, "10") })

  object DBMS_MAX_LIFETIME_MINS : Property("$DBMS.maxLifetimeMins",
                                           initFunc = { setPropertyFromConfig(it, "30") })

  object REDIS_MAX_POOL_SIZE : Property(RedisUtils.REDIS_MAX_POOL_SIZE, initFunc = { setPropertyFromConfig(it, "10") })
  object REDIS_MAX_IDLE_SIZE : Property(RedisUtils.REDIS_MAX_IDLE_SIZE, initFunc = { setPropertyFromConfig(it, "5") })
  object REDIS_MIN_IDLE_SIZE : Property(RedisUtils.REDIS_MIN_IDLE_SIZE, initFunc = { setPropertyFromConfig(it, "1") })

  companion object : KLogging() {
    fun initProperties() =
      listOf(
        DSL_FILE_NAME,
        DSL_VARIABLE_NAME,
        PROXY_HOSTNAME,
        SENDGRID_PREFIX,
        ANALYTICS_ID,
        KTOR_PORT,
        KTOR_WATCH,
        IS_PRODUCTION,
        DBMS_ENABLED,
        REDIS_ENABLED,
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
        REDIS_MAX_POOL_SIZE,
        REDIS_MAX_IDLE_SIZE,
        REDIS_MIN_IDLE_SIZE,
      )
  }
}