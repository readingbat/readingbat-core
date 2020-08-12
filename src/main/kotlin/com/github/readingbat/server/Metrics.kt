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

package com.github.readingbat.server

import com.github.pambrose.common.dsl.PrometheusDsl.counter
import com.github.pambrose.common.dsl.PrometheusDsl.gauge
import com.github.pambrose.common.dsl.PrometheusDsl.summary
import com.github.pambrose.common.metrics.SamplerGaugeCollector
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.dsl.agentLaunchId

internal class Metrics(val contentSource: () -> ReadingBatContent) {

  val contentLoadedCount =
    counter {
      name("content_loaded_count")
      help("Content loaded count")
      labelNames(AGENT_ID)
    }

  val challengeRequestCount =
    counter {
      name("challenge_request_count")
      help("Challenge request count")
      labelNames(AGENT_ID, LANG_TYPE, AUTHENTICATED)
    }

  val challengeGroupRequestCount =
    counter {
      name("challenge_group_request_count")
      help("Challenge group request count")
      labelNames(AGENT_ID, LANG_TYPE, AUTHENTICATED)
    }

  val languageGroupRequestCount =
    counter {
      name("language_group_request_count")
      help("Language group request count")
      labelNames(AGENT_ID, LANG_TYPE, AUTHENTICATED)
    }

  val playgroundRequestCount =
    counter {
      name("playground_request_count")
      help("Playground group request count")
      labelNames(AGENT_ID, AUTHENTICATED)
    }

  val endpointRequestLatency =
    summary {
      name("endpoint_request_latency")
      help("Endpoint request latency")
      labelNames(AGENT_ID, ENDPOINT_NAME)
    }

  val challengeParseLatency =
    summary {
      name("challenge_parse_latency_seconds")
      help("Challenge parse latency in seconds")
      labelNames(AGENT_ID, LANG_TYPE)
    }

  val challengeRemoteReadLatency =
    summary {
      name("challenge_remote_read_latency_seconds")
      help("Challenge remote read latency in seconds")
      labelNames(AGENT_ID)
    }

  val wsStudentAnswerStartCount =
    counter {
      name("ws_student_answer_start_count")
      help("WS Student answer start count")
      labelNames(AGENT_ID)
    }

  val wsClassStatisticsStartCount =
    counter {
      name("ws_class_statistics_start_count")
      help("WS Class statistics start count")
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


  fun init() {
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

    SamplerGaugeCollector("content_cache_size",
                          "Content cache size",
                          labelNames = listOf(AGENT_ID),
                          labelValues = listOf(agentLaunchId()),
                          data = { contentSource().contentMap.size.toDouble() })
  }

  suspend fun measureEndpointRequest(endpoint: String, func: suspend () -> Unit) {
    val timer = endpointRequestLatency.labels(agentLaunchId(), endpoint).startTimer()
    try {
      func.invoke()
    } finally {
      timer.observeDuration()
    }
  }

  companion object {
    private const val AGENT_ID = "agent_id"
    private const val LANG_TYPE = "lang_type"
    private const val ENDPOINT_NAME = "endpoint_name"
    private const val AUTHENTICATED = "authenticated"
  }
}