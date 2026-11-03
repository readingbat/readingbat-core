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

import com.pambrose.common.email.Email
import com.pambrose.common.exposed.readonlyTx
import com.readingbat.common.SessionActivites.querySessions
import com.readingbat.server.BrowserSessionsTable
import com.readingbat.server.FullName
import com.readingbat.server.GeoInfo
import com.readingbat.server.GeoInfosTable
import com.readingbat.server.ServerRequestsTable
import com.readingbat.withTestApp
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class SessionActivitesTest : StringSpec() {
  init {
    "a session's login date is when its browser session was created" {
      withTestApp {
        // Whole seconds, so the TIMESTAMPTZ round-trip cannot truncate it.
        val loginTime = Instant.fromEpochSeconds((nowInstant() - 2.hours).epochSeconds)
        val session = loggedInSession(loginTime)

        session.request("test-agent")

        querySessions(1).single { it.sessionId == session.id }.loginDate shouldBe loginTime
      }
    }

    "a session's requests are one row even when the user agent changes" {
      withTestApp {
        val session = loggedInSession(nowInstant())

        session.request("agent-one")
        session.request("agent-two")

        querySessions(1).single { it.sessionId == session.id }.count shouldBe 2L
      }
    }

    "a session's requests are one row even when the remote host changes within a location" {
      withTestApp {
        val session = loggedInSession(nowInstant())

        session.request("test-agent")
        session.request("test-agent", geoRef = seedGeoInfo(uniqueIp()))

        querySessions(1).single { it.sessionId == session.id }.count shouldBe 2L
      }
    }
  }
}

/** A logged-in browser session, with the user and geo rows its server requests point at. */
private class TestSession(val id: String, val dbmsId: Long, val userRef: Long, val geoRef: Long) {
  fun request(agent: String, geoRef: Long = this.geoRef) =
    transaction {
      with(ServerRequestsTable) {
        insert { row ->
          row[created] = nowInstant()
          row[requestId] = "$id-${System.nanoTime()}"
          row[sessionRef] = dbmsId
          row[userRef] = this@TestSession.userRef
          row[ServerRequestsTable.geoRef] = geoRef
          row[verb] = "GET"
          row[path] = "/"
          row[queryString] = ""
          row[userAgent] = agent
          row[duration] = 0
        }
      }
    }
}

private fun loggedInSession(loginTime: Instant): TestSession {
  val tag = System.nanoTime()
  val user =
    User.createOAuthUser(
      name = FullName("Session Lister"),
      emailVal = Email("session-lister-$tag@test.com"),
      provider = OAuthProvider.GITHUB,
      providerId = "session-lister-$tag",
    )

  val geoRef = seedGeoInfo(uniqueIp())
  val id = "session-lister-$tag"
  val dbmsId =
    transaction {
      BrowserSessionsTable.insertAndGetId { row ->
        row[BrowserSessionsTable.sessionId] = id
        row[BrowserSessionsTable.created] = loginTime
      }.value
    }

  return TestSession(id, dbmsId, user.userDbmsId, geoRef)
}

private fun uniqueIp(): String {
  val n = System.nanoTime()
  return "10.${n / 65_536 % 256}.${n / 256 % 256}.${n % 256}"
}

// Every seeded IP is in the same city, so only the remote host differs between them.
private fun seedGeoInfo(ip: String): Long {
  GeoInfo(true, -1, ip, """{"ip":"$ip","city":"Testville","country_name":"Testland"}""").insert()
  return readonlyTx {
    GeoInfosTable.select(GeoInfosTable.id).where { GeoInfosTable.ip eq ip }.single()[GeoInfosTable.id].value
  }
}
