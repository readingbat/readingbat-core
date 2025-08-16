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

import com.github.pambrose.common.util.isNotNull
import com.github.pambrose.common.util.obfuscate
import com.github.readingbat.common.Constants.UNASSIGNED

enum class EnvVar(val maskFunc: EnvVar.() -> String = { getEnv(UNASSIGNED) }) {
  AGENT_ENABLED,
  AGENT_CONFIG,
  CLOUD_SQL_CONNECTION_NAME,
  FILTER_LOG,
  GITHUB_OAUTH({ getEnvOrNull()?.obfuscate(4) ?: UNASSIGNED }),
  PAPERTRAIL_PORT,
  IPGEOLOCATION_KEY({ getEnvOrNull()?.obfuscate(4) ?: UNASSIGNED }),
  SCRIPT_CLASSPATH,
  EMAIL_PREFIX,
  RESEND_API_KEY({ getEnvOrNull()?.obfuscate(4) ?: UNASSIGNED }),
  RESEND_SENDER_EMAIL,
  REDIRECT_HOSTNAME,
  JAVA_TOOL_OPTIONS,
  DBMS_DRIVER_CLASSNAME,
  DBMS_URL({ getEnvOrNull()?.obfuscate(4) ?: UNASSIGNED }),
  DBMS_USERNAME,
  DBMS_PASSWORD({ getEnvOrNull()?.obfuscate(1) ?: UNASSIGNED }),
  FORWARDED_ENABLED,
  XFORWARDED_ENABLED,
  RATE_LIMIT_COUNT,
  RATE_LIMIT_SECS,
  RECAPTCHA_ENABLED,
  RECAPTCHA_SITE_KEY,
  RECAPTCHA_SECRET_KEY({ getEnvOrNull()?.obfuscate(4) ?: UNASSIGNED }),
  ;

  fun isDefined(): Boolean = getEnvOrNull().isNotNull()

  fun getEnvOrNull(): String? = System.getenv(name)

  fun getEnv(default: String) = System.getenv(name) ?: default

  fun getEnv(default: Boolean) = System.getenv(name)?.toBoolean() ?: default

  @Suppress("unused")
  fun getEnv(default: Int) = System.getenv(name)?.toInt() ?: default

  fun getRequiredEnv() = getEnvOrNull() ?: error("Missing $name value")
}
