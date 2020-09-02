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

import com.github.pambrose.common.dsl.PrometheusDsl.counter
import com.github.pambrose.common.dsl.PrometheusDsl.gauge
import com.github.pambrose.common.dsl.PrometheusDsl.summary
import com.github.pambrose.common.metrics.SamplerGaugeCollector
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.dsl.agentLaunchId
import kotlin.time.hours
import kotlin.time.minutes

internal class Metrics {

  //  /reset call count
  val contentLoadedCount =
    counter {
      name("content_loaded_count")
      help("Content loaded count")
      labelNames(AGENT_ID)
    }

  // Language page count
  val languageGroupRequestCount =
    counter {
      name("language_group_request_count")
      help("Language group request count")
      labelNames(AGENT_ID, HTTP_METHOD, LANG_TYPE, AUTHENTICATED)
    }

  // Challenge group page count
  val challengeGroupRequestCount =
    counter {
      name("challenge_group_request_count")
      help("Challenge group request count")
      labelNames(AGENT_ID, HTTP_METHOD, LANG_TYPE, AUTHENTICATED)
    }

  // Challenge page count
  val challengeRequestCount =
    counter {
      name("challenge_request_count")
      help("Challenge request count")
      labelNames(AGENT_ID, HTTP_METHOD, LANG_TYPE, AUTHENTICATED)
    }

  // Playground page count
  val playgroundRequestCount =
    counter {
      name("playground_request_count")
      help("Playground group request count")
      labelNames(AGENT_ID, HTTP_METHOD, AUTHENTICATED)
    }

  // Page generation time
  private val endpointRequestDuration =
    summary {
      name("endpoint_request_duration")
      help("Endpoint request duration")
      labelNames(AGENT_ID, ENDPOINT_NAME)
    }

  // Time spent reading github directory contents
  val githubDirectoryReadDuration =
    summary {
      name("github_directory_read_duration_seconds")
      help("GitHub directory read duration in seconds")
      labelNames(AGENT_ID)
    }

  // Time to read file from github
  val challengeRemoteReadDuration =
    summary {
      name("challenge_remote_read_duration_seconds")
      help("Challenge remote read duration in seconds")
      labelNames(AGENT_ID)
    }

  // Time to parse a program by language
  val challengeParseDuration =
    summary {
      name("challenge_parse_duration_seconds")
      help("Challenge parse duration in seconds")
      labelNames(AGENT_ID, LANG_TYPE)
    }

  val wsStudentAnswerCount =
    counter {
      name("ws_student_answer_count")
      help("WS Student answer count")
      labelNames(AGENT_ID)
    }

  val wsClassStatisticsCount =
    counter {
      name("ws_class_statistics_count")
      help("WS Class statistics count")
      labelNames(AGENT_ID)
    }

  val wsStudentAnswerGauge =
    gauge {
      name("ws_student_answer_gauge")
      help("WS Student answer gauge")
      labelNames(AGENT_ID)
    }

  val wsClassStatisticsGauge =
    gauge {
      name("ws_class_statistics_gauge")
      help("WS Class statistics gauge")
      labelNames(AGENT_ID)
    }

  val wsStudentAnswerResponseCount =
    counter {
      name("ws_student_answer_response_count")
      help("WS Student answer response count")
      labelNames(AGENT_ID)
    }

  val wsClassStatisticsResponseCount =
    counter {
      name("ws_class_statistics_response_count")
      help("WS Class statistics response count")
      labelNames(AGENT_ID)
    }

  fun init(contentSource: () -> ReadingBatContent) {
    gauge {
      name("server_start_time_seconds")
      labelNames(AGENT_ID)
      help("Server start time in seconds")
    }.labels(agentLaunchId()).setToCurrentTime()

    SamplerGaugeCollector("sources_cache_size",
                          "Sources cache size",
                          labelNames = listOf(AGENT_ID),
                          labelValues = listOf(agentLaunchId()),
                          data = { contentSource().sourcesMap.size.toDouble() })

    SamplerGaugeCollector("active_users_map_size",
                          "Active users map size",
                          labelNames = listOf(AGENT_ID),
                          labelValues = listOf(agentLaunchId()),
                          data = { SessionActivites.sessionsMapSize.toDouble() })

    SamplerGaugeCollector("active_users_1min_count",
                          "Active users in last 1 min count",
                          labelNames = listOf(AGENT_ID),
                          labelValues = listOf(agentLaunchId()),
                          data = { SessionActivites.activeSessions(1.minutes).toDouble() })

    SamplerGaugeCollector("active_users_15mins_count",
                          "Active users in last 15 mins count",
                          labelNames = listOf(AGENT_ID),
                          labelValues = listOf(agentLaunchId()),
                          data = { SessionActivites.activeSessions(15.minutes).toDouble() })

    SamplerGaugeCollector("active_users_60mins_count",
                          "Active users in last 60 mins count",
                          labelNames = listOf(AGENT_ID),
                          labelValues = listOf(agentLaunchId()),
                          data = { SessionActivites.activeSessions(1.hours).toDouble() })
  }

  suspend fun measureEndpointRequest(endpoint: String, body: suspend () -> Unit) {
    val timer = endpointRequestDuration.labels(agentLaunchId(), endpoint).startTimer()
    try {
      body.invoke()
    } finally {
      timer.observeDuration()
    }
  }

  companion object {
    private const val AGENT_ID = "agent_id"
    private const val HTTP_METHOD = "http_method"
    private const val LANG_TYPE = "lang_type"
    private const val ENDPOINT_NAME = "endpoint_name"
    private const val AUTHENTICATED = "authenticated"

    const val GET = "get"
    const val POST = "post"
  }
}