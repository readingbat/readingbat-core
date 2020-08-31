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

import com.github.pambrose.common.util.isNotNull
import com.github.readingbat.common.CommonUtils.obfuscate
import com.github.readingbat.common.PropertyNames.AGENT
import com.github.readingbat.common.PropertyNames.CHALLENGES
import com.github.readingbat.common.PropertyNames.CLASSES
import com.github.readingbat.common.PropertyNames.CONTENT
import com.github.readingbat.common.PropertyNames.READINGBAT
import com.github.readingbat.common.PropertyNames.SITE
import com.github.readingbat.dsl.InvalidConfigurationException
import io.ktor.application.*
import io.ktor.config.*
import mu.KLogging

enum class Properties(val propertyValue: String,
                      val maskFunc: Properties.() -> String = { getProperty("unassigned") }) {

  IS_PRODUCTION("$READINGBAT.$SITE.production"),
  STARTUP_DELAY_SECS("$READINGBAT.$SITE.startupMaxDelaySecs"),
  REDIRECT_HOSTNAME_PROPERTY("$READINGBAT.$SITE.redirectHostname"),
  SENDGRID_PREFIX_PROPERTY("$READINGBAT.$SITE.sendGridPrefix"),
  FORWARDED_ENABLED_PROPERTY("$READINGBAT.$SITE.forwardedHeaderSupportEnabled"),
  XFORWARDED_ENABLED_PROPERTY("$READINGBAT.$SITE.xforwardedHeaderSupportEnabled"),
  ANALYTICS_ID("$READINGBAT.$SITE.googleAnalyticsId", { getPropertyOrNull()?.obfuscate(2) ?: "unassigned" }),

  JAVA_SCRIPTS_POOL_SIZE("$READINGBAT.scripts.javaPoolSize"),
  KOTLIN_SCRIPTS_POOL_SIZE("$READINGBAT.scripts.kotlinPoolSize"),
  PYTHON_SCRIPTS_POOL_SIZE("$READINGBAT.scripts.pythonPoolSize"),

  FILE_NAME("$READINGBAT.$CONTENT.fileName"),
  VARIABLE_NAME("$READINGBAT.$CONTENT.variableName"),

  PROMETHEUS_URL("$READINGBAT.prometheus.url"),
  GRAFANA_URL("$READINGBAT.grafana.url"),
  CONFIG_FILENAME("$READINGBAT.configFilename"),

  MAX_HISTORY_LENGTH("$READINGBAT.$CHALLENGES.maxHistoryLength"),
  MAX_CLASS_COUNT("$READINGBAT.$CLASSES.maxCount"),

  ADMIN_USERS("$READINGBAT.adminUsers"),

  AGENT_CONFIG_PROPERTY("agent.config"),
  AGENT_ENABLED_PROPERTY("$AGENT.enabled"),
  PROXY_HOSTNAME("$AGENT.proxy.hostname"),
  AGENT_LAUNCH_ID("$AGENT.launchId"),

  REDIS_MAX_POOL_SIZE("redis.maxPoolSize"),
  REDIS_MAX_IDLE_SIZE("redis.maxIdleSize"),
  REDIS_MIN_IDLE_SIZE("redis.minIdleSize"),

  KOTLIN_SCRIPT_CLASSPATH("kotlin.script.classpath"),
  KTOR_PORT("ktor.deployment.port"),
  ;

  private fun Application.configProperty(name: String, default: String = "", warn: Boolean = false) =
    try {
      environment.config.property(name).getString()
    } catch (e: ApplicationConfigurationException) {
      if (warn)
        logger.warn { "Missing $name value in application.conf" }
      default
    }

  fun configProperty(application: Application, default: String = "", warn: Boolean = false) =
    application.configProperty(propertyValue, default, warn)

  fun configPropertyOrNull(application: Application) =
    application.environment.config.propertyOrNull(propertyValue)

  fun getProperty(default: String) = System.getProperty(propertyValue) ?: default

  fun getProperty(default: Boolean) = System.getProperty(propertyValue)?.toBoolean() ?: default

  fun getProperty(default: Int) = System.getProperty(propertyValue)?.toInt() ?: default

  fun getPropertyOrNull(): String? = System.getProperty(propertyValue)

  fun getRequiredProperty() = getPropertyOrNull() ?: throw InvalidConfigurationException("Missing $propertyValue value")

  fun setProperty(value: String) {
    logger.info { "$propertyValue: $value" }
    System.setProperty(propertyValue, value)
  }

  fun isDefined() = getPropertyOrNull().isNotNull()

  companion object : KLogging()
}

