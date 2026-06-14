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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * Tests the per-property initialization guard. Previously the "not initialized" check used a single
 * global flag, so it could never reflect a specific property. Now a property that has individually
 * had a value assigned is recognized as initialized, while one that hasn't still errors — making the
 * guard's property-named error meaningful.
 */
class PropertyInitGuardTest : StringSpec() {
  init {
    "an uninitialized property read with errorOnNonInit throws a property-named error" {
      KtorProperty.resetForTesting()
      try {
        val prop = KtorProperty("test.initguard.unset")
        val ex = shouldThrow<IllegalStateException> { prop.getProperty("d") }
        ex.message shouldContain "not initialized"
      } finally {
        KtorProperty.assignInitialized()
      }
    }

    "a property individually set is readable while a different unset one still errors" {
      KtorProperty.resetForTesting()
      try {
        val set = KtorProperty("test.initguard.set")
        val unset = KtorProperty("test.initguard.other")

        set.setProperty("v")

        set.isInitialized() shouldBe true
        // Per-property awareness: the set property reads without error even before global init...
        set.getProperty("d") shouldBe "v"

        // ...while a different, unset property still triggers the guard.
        unset.isInitialized() shouldBe false
        shouldThrow<IllegalStateException> { unset.getProperty("d") }
      } finally {
        KtorProperty.configStore.remove("test.initguard.set")
        KtorProperty.assignInitialized()
      }
    }
  }
}
