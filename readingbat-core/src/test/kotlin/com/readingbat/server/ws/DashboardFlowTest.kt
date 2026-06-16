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

package com.readingbat.server.ws

import com.readingbat.common.Constants.FLOW_BUFFER_CAPACITY
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.StringSpec
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

/**
 * Tests that the dashboard flows never apply backpressure to the producer. The dashboard publish
 * runs inline in the student answer-submit request coroutine; with the default SUSPEND overflow a
 * full buffer (no/slow teacher collector) would suspend `emit` and slow student submissions. A
 * DROP_OLDEST flow self-evicts so the producer never blocks.
 */
class DashboardFlowTest : StringSpec() {
  init {
    "dashboardFlow does not suspend the producer when nothing drains it" {
      val flow = dashboardFlow<Int>()
      shouldNotThrowAny {
        // With SUSPEND this blocks once the buffer fills (no collector) and times out; with
        // DROP_OLDEST every emit returns immediately.
        withTimeout(5.seconds) {
          repeat(FLOW_BUFFER_CAPACITY + 500) { flow.emit(it) }
        }
      }
    }
  }
}
