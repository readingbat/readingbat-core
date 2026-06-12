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
import io.kotest.matchers.string.shouldNotContain

/**
 * Tests for [maskSecret], the replacement for `obfuscate(4)`, which previously left ~75% of every
 * secret visible in logs and the admin config page. A mask must reveal at most the last few
 * characters of a secret.
 */
class MaskSecretTest : StringSpec() {
  init {
    "maskSecret leaves a blank value unchanged" {
      "".maskSecret() shouldBe ""
    }

    "maskSecret fully masks short values, revealing nothing" {
      "shortsecret".maskSecret() shouldBe "****"
    }

    "maskSecret reveals only the last four characters of a long secret" {
      "0123456789abcdefABCD".maskSecret() shouldBe "****ABCD"
    }

    "maskSecret does not reveal the bulk of a secret" {
      val secret = "super-sensitive-client-secret-value-1234"
      val masked = secret.maskSecret()
      masked shouldNotContain "super-sensitive"
      masked shouldNotContain secret.dropLast(4)
    }
  }
}
