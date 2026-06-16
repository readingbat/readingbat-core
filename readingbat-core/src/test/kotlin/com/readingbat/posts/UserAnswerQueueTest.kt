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

package com.readingbat.posts

import com.readingbat.posts.UserAnswerQueue.WORKER_COUNT
import com.readingbat.posts.UserAnswerQueue.submitForUser
import com.readingbat.posts.UserAnswerQueue.workerIndex
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tests the fixed-size worker-pool answer queue that replaced the unbounded per-user channel map.
 * Work is sharded onto a bounded set of worker coroutines by userDbmsId, so the structure no longer
 * grows one channel + coroutine per distinct user, while each user's writes stay serialized.
 */
class UserAnswerQueueTest : StringSpec() {
  init {
    "workerIndex maps a user to a stable worker within the pool range" {
      (0L..100L).forEach { id ->
        workerIndex(id) shouldBeGreaterThanOrEqual 0
        workerIndex(id) shouldBeLessThan WORKER_COUNT
        workerIndex(id) shouldBe workerIndex(id)
      }
    }

    "workerIndex handles negative ids without going out of range" {
      listOf(-1L, -17L, -1234L).forEach { id ->
        workerIndex(id) shouldBeGreaterThanOrEqual 0
        workerIndex(id) shouldBeLessThan WORKER_COUNT
      }
    }

    "submitForUser returns the block result" {
      submitForUser(42L) { 2 + 3 } shouldBe 5
    }

    "submitForUser serializes concurrent jobs for the same user" {
      val active = AtomicInteger(0)
      val maxObserved = AtomicInteger(0)

      coroutineScope {
        (1..40).map {
          async {
            submitForUser(7L) {
              val now = active.incrementAndGet()
              maxObserved.updateAndGet { m -> maxOf(m, now) }
              // Yield-ish window during which an overlapping job would be detected.
              Thread.sleep(2)
              active.decrementAndGet()
            }
          }
        }.awaitAll()
      }

      // A single worker processes one user's jobs one at a time, so they never overlap.
      maxObserved.get() shouldBe 1
    }
  }
}
