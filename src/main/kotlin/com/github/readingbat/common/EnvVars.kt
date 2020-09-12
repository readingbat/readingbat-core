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
import com.github.readingbat.common.CommonUtils.maskUrl
import com.github.readingbat.common.CommonUtils.obfuscate
import com.github.readingbat.common.Constants.UNASSIGNED
import com.github.readingbat.dsl.InvalidConfigurationException

enum class EnvVars(val maskFunc: EnvVars.() -> String = { getEnv(UNASSIGNED) }) {

  AGENT_ENABLED,
  AGENT_CONFIG,
  REDIS_URL({ getEnv(UNASSIGNED).maskUrl() }),
  GITHUB_OAUTH({ getEnvOrNull()?.obfuscate(4) ?: UNASSIGNED }),
  PAPERTRAIL_PORT,
  IPGEOLOCATION_KEY({ getEnvOrNull()?.obfuscate(4) ?: UNASSIGNED }),
  SCRIPT_CLASSPATH,
  SENDGRID_API_KEY({ getEnvOrNull()?.obfuscate(4) ?: UNASSIGNED }),
  SENDGRID_PREFIX,
  FILTER_LOG,
  REDIRECT_HOSTNAME,
  FORWARDED_ENABLED,
  XFORWARDED_ENABLED,
  JAVA_TOOL_OPTIONS;

  fun isDefined(): Boolean = getEnvOrNull().isNotNull()

  fun getEnvOrNull(): String? = System.getenv(name)

  fun getEnv(default: String) = System.getenv(name) ?: default

  fun getEnv(default: Boolean) = System.getenv(name)?.toBoolean() ?: default

  fun getEnv(default: Int) = System.getenv(name)?.toInt() ?: default

  fun getRequiredEnv() = getEnvOrNull() ?: throw InvalidConfigurationException("Missing $name value")
}

