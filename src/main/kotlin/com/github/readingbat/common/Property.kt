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

package com.github.readingbat.common

import com.github.pambrose.common.redis.RedisUtils
import com.github.pambrose.common.util.isNotNull
import com.github.readingbat.common.CommonUtils.obfuscate
import com.github.readingbat.common.Constants.UNASSIGNED
import com.github.readingbat.common.PropertyNames.AGENT
import com.github.readingbat.common.PropertyNames.CHALLENGES
import com.github.readingbat.common.PropertyNames.CLASSES
import com.github.readingbat.common.PropertyNames.CONTENT
import com.github.readingbat.common.PropertyNames.DBMS
import com.github.readingbat.common.PropertyNames.READINGBAT
import com.github.readingbat.common.PropertyNames.SITE
import com.github.readingbat.dsl.InvalidConfigurationException
import io.ktor.application.*
import io.ktor.config.*
import mu.KLogging

enum class Property(val propertyValue: String,
                    val maskFunc: Property.() -> String = { getProperty(UNASSIGNED) }) {

  KOTLIN_SCRIPT_CLASSPATH("kotlin.script.classpath"),

  CONFIG_FILENAME("$READINGBAT.configFilename"),
  ADMIN_USERS("$READINGBAT.adminUsers"),
  LOGBACK_CONFIG_FILE("logback.configurationFile"),
  AGENT_LAUNCH_ID("$AGENT.launchId"),

  AGENT_CONFIG_PROPERTY("agent.config"),

  // These are used in module()
  DSL_FILE_NAME("$READINGBAT.$CONTENT.fileName"),
  DSL_VARIABLE_NAME("$READINGBAT.$CONTENT.variableName"),
  PROXY_HOSTNAME("$AGENT.proxy.hostname"),
  STARTUP_DELAY_SECS("$READINGBAT.$SITE.startupMaxDelaySecs"),

  // These are defaults for enf var values
  REDIRECT_HOSTNAME_PROPERTY("$READINGBAT.$SITE.redirectHostname"),
  SENDGRID_PREFIX_PROPERTY("$READINGBAT.$SITE.sendGridPrefix"),
  FORWARDED_ENABLED_PROPERTY("$READINGBAT.$SITE.forwardedHeaderSupportEnabled"),
  XFORWARDED_ENABLED_PROPERTY("$READINGBAT.$SITE.xforwardedHeaderSupportEnabled"),

  // These are assigned to ReadingBatContent vals
  ANALYTICS_ID("$READINGBAT.$SITE.googleAnalyticsId", { getPropertyOrNull() ?: UNASSIGNED }),
  MAX_HISTORY_LENGTH("$READINGBAT.$CHALLENGES.maxHistoryLength"),
  MAX_CLASS_COUNT("$READINGBAT.$CLASSES.maxCount"),
  KTOR_PORT("ktor.deployment.port"),
  KTOR_WATCH("ktor.deployment.watch"),

  // These are assigned in ReadingBatServer
  IS_PRODUCTION("$READINGBAT.$SITE.production"),
  POSTGRES_ENABLED("$READINGBAT.$SITE.postgresEnabled"),
  SAVE_REQUESTS_ENABLED("$READINGBAT.$SITE.saveRequestsEnabled"),
  MULTI_SERVER_ENABLED("$READINGBAT.$SITE.multiServerEnabled"),
  CONTENT_CACHING_ENABLED("$READINGBAT.$SITE.contentCachingEnabled"),
  AGENT_ENABLED_PROPERTY("$AGENT.enabled"),

  PINGDOM_BANNER_ID("$READINGBAT.$SITE.pingdomBannerId", { getPropertyOrNull() ?: UNASSIGNED }),
  PINGDOM_URL("$READINGBAT.$SITE.pingdomUrl", { getPropertyOrNull() ?: UNASSIGNED }),
  STATUS_PAGE_URL("$READINGBAT.$SITE.statusPageUrl", { getPropertyOrNull() ?: UNASSIGNED }),

  PROMETHEUS_URL("$READINGBAT.prometheus.url"),
  GRAFANA_URL("$READINGBAT.grafana.url"),

  JAVA_SCRIPTS_POOL_SIZE("$READINGBAT.scripts.javaPoolSize"),
  KOTLIN_SCRIPTS_POOL_SIZE("$READINGBAT.scripts.kotlinPoolSize"),
  PYTHON_SCRIPTS_POOL_SIZE("$READINGBAT.scripts.pythonPoolSize"),

  DBMS_DRIVER_CLASSNAME("$DBMS.driverClassName"),
  DBMS_URL("$DBMS.jdbcUrl"),
  DBMS_USERNAME("$DBMS.username"),
  DBMS_PASSWORD("$DBMS.password", { getPropertyOrNull()?.obfuscate(1) ?: UNASSIGNED }),
  DBMS_MAX_POOL_SIZE("$DBMS.maxPoolSize"),

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

  fun getProperty(default: String) = System.getProperty(propertyValue) ?: default

  fun getProperty(default: Boolean) = System.getProperty(propertyValue)?.toBoolean() ?: default

  fun getProperty(default: Int) = System.getProperty(propertyValue)?.toInt() ?: default

  fun getPropertyOrNull(): String? = System.getProperty(propertyValue)

  fun getRequiredProperty() = getPropertyOrNull() ?: throw InvalidConfigurationException("Missing $propertyValue value")

  fun setProperty(value: String) {
    System.setProperty(propertyValue, value)
    logger.info { "$propertyValue: ${maskFunc()}" }
  }

  fun setPropertyFromConfig(application: Application, default: String) {
    setProperty(configValue(application, default))
  }

  fun isDefined() = getPropertyOrNull().isNotNull()

  companion object : KLogging()
}

