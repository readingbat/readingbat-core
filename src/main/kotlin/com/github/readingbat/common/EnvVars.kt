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

import com.github.readingbat.dsl.InvalidConfigurationException

enum class EnvVars {

  AGENT_CONFIG,
  SCRIPT_CLASSPATH,
  GITHUB_OAUTH,
  SENDGRID_API_KEY,
  SENDGRID_PREFIX,
  REDIS_URL,
  REDIRECT_HOSTNAME,
  AGENT_ENABLED,
  FORWARDED_ENABLED,
  XFORWARDED_ENABLED,
  FILTER_LOG,
  JAVA_TOOL_OPTIONS;

  fun getEnvOrNull(): String? = System.getenv(name)

  fun getEnv() = System.getenv(name) ?: throw InvalidConfigurationException("$this unassigned")

  fun getEnv(default: String) = System.getenv(name) ?: default

  fun getEnv(default: Boolean) = System.getenv(name)?.toBoolean() ?: default

  fun getEnv(default: Int) = System.getenv(name)?.toInt() ?: default

  fun getRequiredEnv() = getEnvOrNull() ?: throw InvalidConfigurationException("Missing $name value")
}

