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

package com.readingbat.server

import com.readingbat.common.BrowserSession
import com.readingbat.common.BrowserSession.Companion.createBrowserSession
import com.readingbat.common.BrowserSession.Companion.findOrCreateSessionDbmsId
import com.readingbat.common.BrowserSession.Companion.querySessionDbmsId
import com.readingbat.dsl.MissingBrowserSessionException
import com.readingbat.kotest.TestDatabase
import com.readingbat.kotest.TestSupport.initTestProperties
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class BrowserSessionPersistenceTest : StringSpec() {
  init {
    "createBrowserSession should persist and be queryable" {
      initTestProperties()
      TestDatabase.connectAndMigrate()

      val sessionId = "test-browser-session-persist-${System.nanoTime()}"

      val dbmsId =
        transaction {
          createBrowserSession(sessionId)
        }

      dbmsId shouldBeGreaterThan 0

      val queriedId = querySessionDbmsId(sessionId)
      queriedId shouldBe dbmsId
    }

    "querySessionDbmsId should throw MissingBrowserSessionException for unknown session" {
      initTestProperties()
      TestDatabase.connectAndMigrate()

      shouldThrow<MissingBrowserSessionException> {
        querySessionDbmsId("nonexistent-session-id")
      }
    }

    "findOrCreateSessionDbmsId should create when missing and createIfMissing is true" {
      initTestProperties()
      TestDatabase.connectAndMigrate()

      val sessionId = "test-find-or-create-${System.nanoTime()}"

      val dbmsId =
        transaction {
          findOrCreateSessionDbmsId(sessionId, createIfMissing = true)
        }
      dbmsId shouldBeGreaterThan 0

      // Second call should return the same ID
      val sameId =
        transaction {
          findOrCreateSessionDbmsId(sessionId, createIfMissing = true)
        }
      sameId shouldBe dbmsId
    }

    "findOrCreateSessionDbmsId should return -1 when missing and createIfMissing is false" {
      initTestProperties()
      TestDatabase.connectAndMigrate()

      val sessionId = "test-no-create-${System.nanoTime()}"

      val result =
        transaction {
          findOrCreateSessionDbmsId(sessionId, createIfMissing = false)
        }
      result shouldBe -1
    }

    "queryOrCreateSessionDbmsId should create session on first call" {
      initTestProperties()
      TestDatabase.connectAndMigrate()

      val sessionId = "test-query-or-create-${System.nanoTime()}"
      val browserSession = BrowserSession(sessionId)

      val dbmsId =
        transaction {
          browserSession.queryOrCreateSessionDbmsId()
        }

      dbmsId shouldBeGreaterThan 0

      // Query again - should return same ID
      val sameId = querySessionDbmsId(sessionId)
      sameId shouldBe dbmsId
    }
  }
}
