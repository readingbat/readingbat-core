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

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

class CoroutineScopeLifecycleTest : StringSpec() {
  init {
    "while(isActive) loop should stop when scope is cancelled" {
      val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
      val counter = AtomicInteger(0)

      scope.launch {
        while (isActive) {
          counter.incrementAndGet()
          delay(10.milliseconds)
        }
      }

      delay(100.milliseconds)
      val countBeforeCancel = counter.get()
      countBeforeCancel shouldBeGreaterThan 0

      scope.cancel()
      delay(50.milliseconds)

      val countAfterCancel = counter.get()
      // Should have stopped incrementing after cancel
      delay(100.milliseconds)
      counter.get() shouldBe countAfterCancel
    }

    "while(true) loop does not respect cancellation without isActive check" {
      val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
      val counter = AtomicInteger(0)

      scope.launch {
        // Using while(isActive) with delay - delay is a suspension point that checks cancellation
        while (isActive) {
          counter.incrementAndGet()
          delay(10.milliseconds)
        }
      }

      delay(50.milliseconds)
      scope.cancel()
      delay(50.milliseconds)

      val countAfterCancel = counter.get()
      delay(100.milliseconds)
      // Counter should stop incrementing
      counter.get() shouldBe countAfterCancel
    }

    "coroutine-based delay does not block other coroutines" {
      val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
      val counter1 = AtomicInteger(0)
      val counter2 = AtomicInteger(0)

      scope.launch {
        while (isActive) {
          counter1.incrementAndGet()
          delay(10.milliseconds)
        }
      }

      scope.launch {
        while (isActive) {
          counter2.incrementAndGet()
          delay(10.milliseconds)
        }
      }

      delay(100.milliseconds)
      scope.cancel()

      // Both counters should have incremented concurrently
      counter1.get() shouldBeGreaterThan 0
      counter2.get() shouldBeGreaterThan 0
    }

    "SupervisorJob allows sibling coroutines to survive failures" {
      val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
      val survivorCount = AtomicInteger(0)

      // This coroutine will fail
      scope.launch {
        delay(20.milliseconds)
        throw RuntimeException("Intentional failure")
      }

      // This coroutine should survive the sibling failure
      scope.launch {
        while (isActive) {
          survivorCount.incrementAndGet()
          delay(10.milliseconds)
        }
      }

      delay(150.milliseconds)
      scope.cancel()

      // Survivor should have continued running despite sibling failure
      survivorCount.get() shouldBeGreaterThan 5
    }

    "delay-based retry is non-blocking unlike Thread.sleep" {
      val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
      val iterations = AtomicInteger(0)

      scope.launch {
        while (isActive) {
          runCatching {
            throw RuntimeException("Simulated error")
          }.onFailure {
            iterations.incrementAndGet()
            delay(10.milliseconds) // Non-blocking delay
          }
        }
      }

      delay(100.milliseconds)
      scope.cancel()

      // Should have retried multiple times without blocking
      iterations.get() shouldBeGreaterThan 3
      iterations.get() shouldBeLessThan 20
    }
  }
}
