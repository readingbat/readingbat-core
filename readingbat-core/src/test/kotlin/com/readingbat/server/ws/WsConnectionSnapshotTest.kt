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

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.Collections

/**
 * Tests for [snapshotUnderMonitor]. The WebSocket pingers and dispatcher iterate a
 * `Collections.synchronizedSet` of connections while other coroutines concurrently add/remove
 * entries. Iterating the live set throws `ConcurrentModificationException`, which killed the
 * pinger coroutine for the process lifetime. Iterating a snapshot taken under the set's monitor
 * decouples iteration from mutation.
 */
class WsConnectionSnapshotTest : StringSpec() {
  init {
    "directly iterating a synchronized set throws when modified mid-iteration" {
      val set = Collections.synchronizedSet(LinkedHashSet<Int>()).apply { addAll(1..5) }
      shouldThrow<ConcurrentModificationException> {
        for (x in set) set.add(x + 100)
      }
    }

    "iterating a snapshot tolerates mutation of the source set" {
      val set = Collections.synchronizedSet(LinkedHashSet<Int>()).apply { addAll(1..5) }
      shouldNotThrowAny {
        for (x in set.snapshotUnderMonitor()) set.add(x + 100)
      }
    }

    "snapshotUnderMonitor returns a decoupled copy" {
      val set = Collections.synchronizedSet(LinkedHashSet<Int>()).apply { addAll(listOf(1, 2, 3)) }
      val snapshot = set.snapshotUnderMonitor()
      set.add(4)
      set.remove(1)
      snapshot shouldBe listOf(1, 2, 3)
    }
  }
}
