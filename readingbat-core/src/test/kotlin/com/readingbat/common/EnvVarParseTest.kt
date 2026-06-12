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

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Tests for [parseIntEnv], which backs `EnvVar.getEnv(Int)`. Previously it called `toInt()` and
 * threw `NumberFormatException` on a non-numeric value, crashing application startup. A malformed
 * value must now fall back to the default instead of aborting the server.
 */
class EnvVarParseTest : StringSpec() {
  init {
    "parseIntEnv returns the default when the value is unset" {
      parseIntEnv("RATE_LIMIT_COUNT", null, 10) shouldBe 10
    }

    "parseIntEnv parses a valid integer" {
      parseIntEnv("RATE_LIMIT_COUNT", "42", 10) shouldBe 42
    }

    "parseIntEnv falls back to the default on a non-numeric value instead of crashing" {
      parseIntEnv("RATE_LIMIT_COUNT", "not-a-number", 10) shouldBe 10
    }

    "parseIntEnv falls back to the default on a blank value" {
      parseIntEnv("RATE_LIMIT_COUNT", "   ", 10) shouldBe 10
    }
  }
}
