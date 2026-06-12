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
 * Tests for GitHub OAuth email resolution. Previously, when no usable email was found the callback
 * fell back to an empty string and created a user with a blank email; this verifies that case now
 * resolves to null (so the login is rejected) and that only verified emails are accepted.
 */
class OAuthGitHubEmailTest : StringSpec() {
  init {
    "resolveGitHubEmail uses a present profile email" {
      resolveGitHubEmail("alice@example.com", emptyList()) shouldBe "alice@example.com"
    }

    "resolveGitHubEmail prefers a primary verified email when the profile email is blank" {
      val emails =
        listOf(
          GitHubEmail("secondary@example.com", primary = false, verified = true),
          GitHubEmail("primary@example.com", primary = true, verified = true),
        )
      resolveGitHubEmail(null, emails) shouldBe "primary@example.com"
    }

    "resolveGitHubEmail falls back to any verified email" {
      val emails =
        listOf(
          GitHubEmail("unverified@example.com", primary = true, verified = false),
          GitHubEmail("verified@example.com", primary = false, verified = true),
        )
      resolveGitHubEmail(null, emails) shouldBe "verified@example.com"
    }

    "resolveGitHubEmail returns null when only unverified emails exist" {
      resolveGitHubEmail(null, listOf(GitHubEmail("x@example.com", primary = true, verified = false))) shouldBe null
    }

    "resolveGitHubEmail returns null when there is no email at all" {
      resolveGitHubEmail(null, emptyList()) shouldBe null
      resolveGitHubEmail("   ", emptyList()) shouldBe null
    }
  }
}
