/*
 * Copyright © 2026 Paul Ambrose (pambrose@mac.com)
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

package com.readingbat.common

import com.readingbat.TestData
import com.readingbat.kotest.TestSupport.initTestProperties
import com.readingbat.server.ReadingBatServer
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainAll
import io.prometheus.metrics.model.registry.PrometheusRegistry

/**
 * `Metrics.init` registers the JVM collectors, so heap, GC and thread state are scrapeable.
 *
 * Worth pinning: the server previously exported its own cache-size gauges but nothing about the
 * JVM, which meant memory problems were invisible until the process died. That is the difference
 * between watching heap fail to fall back after a GC and finding out from an OOM.
 */
class JvmMetricsTest : StringSpec() {
  init {
    "Metrics.init registers the JVM collectors" {
      initTestProperties()
      ReadingBatServer.metrics.init { TestData.readTestContent() }

      // The registry common-utils' MetricsService serves on :8083/metrics. A collector registered
      // anywhere else -- such as the old 0.x CollectorRegistry -- is never scraped.
      val exported =
        PrometheusRegistry.defaultRegistry
          .scrape()
          .map { it.metadata.prometheusName }
          .toSet()

      exported shouldContainAll
        [
          "jvm_memory_used_bytes", // the heap gauge to alert on; was jvm_memory_bytes_used in 0.x
          "jvm_gc_collection_seconds",
          "jvm_threads_current",
          "jvm_classes_currently_loaded", // was jvm_classes_loaded in 0.x
        ]
    }
  }
}
