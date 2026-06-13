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

import com.pambrose.common.email.Email
import com.pambrose.common.exposed.readonlyTx
import com.readingbat.common.OAuthProvider
import com.readingbat.common.User
import com.readingbat.server.FullName
import com.readingbat.server.OAuthLinksTable
import com.readingbat.withTestApp
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll

/**
 * Tests that concurrent OAuth callbacks for the same provider account don't race into a duplicate
 * link. The find-or-create flow checks "does a link exist?" and inserts in separate transactions,
 * so two simultaneous first-login callbacks (e.g. a double-click) could both fall through to the
 * insert. The auto-link upsert against the (provider, providerId) unique index makes that converge
 * to a single link and user instead of throwing a duplicate-key exception.
 */
class OAuthFindOrCreateRaceTest : StringSpec() {
  init {
    "concurrent OAuth callbacks for the same provider id converge to one link and user" {
      withTestApp {
        val email = Email("oauth-race@test.com")
        val existing = User.createOAuthUser(FullName("Race User"), email, OAuthProvider.GOOGLE, "google-race-001")

        val users =
          coroutineScope {
            (1..20).map {
              async(Dispatchers.IO) {
                findOrCreateOAuthUser(OAuthProvider.GITHUB, "github-race-001", email, FullName("Race User"))
              }
            }.awaitAll()
          }

        // All concurrent callbacks resolve to the same (existing) user.
        users.map { it.userId }.toSet() shouldHaveSize 1
        users.first().userId shouldBe existing.userId

        // Exactly one GitHub link exists despite the concurrent inserts.
        val linkCount =
          readonlyTx {
            OAuthLinksTable
              .selectAll()
              .where {
                (OAuthLinksTable.provider eq OAuthProvider.GITHUB.providerName) and
                  (OAuthLinksTable.providerId eq "github-race-001")
              }
              .count()
          }
        linkCount shouldBe 1L
      }
    }
  }
}
