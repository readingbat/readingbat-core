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

package com.readingbat.server.routes

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Tests that Google OAuth only accepts a verified email. Google's userinfo response includes a
 * `verified_email` flag; accepting an unverified email would let an attacker register a Google
 * account with someone else's email address and take over the matching ReadingBat account.
 */
class OAuthGoogleEmailTest : StringSpec() {
  init {
    "verifiedEmailOrNull returns the email when it is present and verified" {
      GoogleUser(email = "alice@example.com", verifiedEmail = true).verifiedEmailOrNull() shouldBe
        "alice@example.com"
    }

    "verifiedEmailOrNull returns null when the email is not verified" {
      GoogleUser(email = "alice@example.com", verifiedEmail = false).verifiedEmailOrNull() shouldBe null
    }

    "verifiedEmailOrNull returns null when the email is missing" {
      GoogleUser(email = null, verifiedEmail = true).verifiedEmailOrNull() shouldBe null
    }

    "verifiedEmailOrNull returns null when the email is blank" {
      GoogleUser(email = "   ", verifiedEmail = true).verifiedEmailOrNull() shouldBe null
    }
  }
}
