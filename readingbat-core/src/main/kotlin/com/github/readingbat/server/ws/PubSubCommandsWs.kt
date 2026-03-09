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

package com.github.readingbat.server.ws

import com.github.readingbat.common.Endpoints.LOAD_ALL_ENDPOINT
import com.github.readingbat.common.Endpoints.LOAD_JAVA_ENDPOINT
import com.github.readingbat.common.Endpoints.LOAD_KOTLIN_ENDPOINT
import com.github.readingbat.common.Endpoints.LOAD_PYTHON_ENDPOINT
import com.github.readingbat.dsl.LanguageType
import com.github.readingbat.dsl.LanguageType.Java
import com.github.readingbat.dsl.LanguageType.Kotlin
import com.github.readingbat.dsl.LanguageType.Python
import com.github.readingbat.server.ReadingBatServer.serverSessionId
import com.github.readingbat.server.ws.ChallengeWs.multiServerWsReadFlow
import com.github.readingbat.server.ws.LoggingWs.adminCommandFlow
import com.github.readingbat.server.ws.LoggingWs.logWsReadFlow
import com.github.readingbat.server.ws.PubSubCommandsWs.PubSubTopic.ADMIN_COMMAND
import com.github.readingbat.server.ws.PubSubCommandsWs.PubSubTopic.LIKE_DISLIKE
import com.github.readingbat.server.ws.PubSubCommandsWs.PubSubTopic.LOG_MESSAGE
import com.github.readingbat.server.ws.PubSubCommandsWs.PubSubTopic.USER_ANSWERS
import com.github.readingbat.utils.toJson
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal object PubSubCommandsWs {
  private val logger = KotlinLogging.logger {}

  enum class PubSubTopic { ADMIN_COMMAND, USER_ANSWERS, LIKE_DISLIKE, LOG_MESSAGE }

  enum class AdminCommand { RESET_CONTENT_DSL, RESET_CACHE, LOAD_CHALLENGE, RUN_GC }

  @Serializable
  enum class LoadChallengeType(
    @Transient val endPoint: String,
    val languageTypes: List<LanguageType>,
  ) {
    LOAD_JAVA(LOAD_JAVA_ENDPOINT, listOf(Java)),
    LOAD_PYTHON(LOAD_PYTHON_ENDPOINT, listOf(Python)),
    LOAD_KOTLIN(LOAD_KOTLIN_ENDPOINT, listOf(Kotlin)),
    LOAD_ALL(LOAD_ALL_ENDPOINT, listOf(Java, Python, Kotlin)),
    ;

    fun toJson() = Json.encodeToString(serializer(), this)
  }

  @Serializable
  data class AdminCommandData(val command: AdminCommand, val jsonArgs: String, val logId: String)

  @Serializable
  data class ChallengeAnswerData(val pubSubTopic: PubSubTopic, val target: String, val jsonArgs: String)

  @Serializable
  data class LogData(val text: String, val logId: String)

  private val timeFormat = DateTimeFormatter.ofPattern("H:m:ss.SSS")

  fun publishAdminCommand(command: AdminCommand, logId: String, jsonArgs: String = "") {
    publishLog("Dispatching ${command.name} $jsonArgs", logId)
    val adminCommandData = AdminCommandData(command, jsonArgs, logId)
    publishShim(ADMIN_COMMAND.name, adminCommandData.toJson())
  }

  fun publishLog(msg: String, logId: String) {
    val logData = LogData("${LocalDateTime.now().format(timeFormat)} [$serverSessionId] - $msg", logId)
    publishShim(LOG_MESSAGE.name, logData.toJson())
  }

  fun publishShim(channel: String, message: String) {
    if (channel != null && message != null)
      when (enumValueOf<PubSubTopic>(channel)) {
        ADMIN_COMMAND -> {
          val data = Json.decodeFromString<AdminCommandData>(message)
          if (!adminCommandFlow.tryEmit(data))
            logger.warn { "adminCommandFlow buffer full, dropping AdminCommand: ${data.command}" }
        }

        USER_ANSWERS,
        LIKE_DISLIKE,
          -> {
          val data = Json.decodeFromString<ChallengeAnswerData>(message)
          if (!multiServerWsReadFlow.tryEmit(data))
            logger.warn { "multiServerWsReadFlow buffer full, dropping ${data.pubSubTopic}" }
        }

        LOG_MESSAGE -> {
          val data = Json.decodeFromString<LogData>(message)
          if (!logWsReadFlow.tryEmit(data))
            logger.warn { "logWsReadFlow buffer full, dropping log message" }
        }
      }
  }
}
