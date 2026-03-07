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

package com.github.readingbat.common

import com.github.readingbat.dsl.MissingBrowserSessionException
import com.github.readingbat.server.BrowserSessionsTable
import com.pambrose.common.exposed.readonlyTx
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.ApplicationCall
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.select
import java.time.Instant

@Serializable
internal data class UserPrincipal(val userId: String, val created: Long = Instant.now().toEpochMilli())

@Serializable
data class BrowserSession(val id: String, val created: Long = Instant.now().toEpochMilli()) {
  fun queryOrCreateSessionDbmsId() =
    try {
      querySessionDbmsId(id)
    } catch (e: MissingBrowserSessionException) {
      logger.debug { "Creating BrowserSession in sessionDbmsId() - ${e.message}" }
      createBrowserSession(id)
    }

  companion object {
    private val logger = KotlinLogging.logger {}

    fun createBrowserSession(id: String) =
      with(BrowserSessionsTable) {
        insertAndGetId { row -> row[sessionId] = id }.value
      }

    // TODO This is a choke point, but we are seeing rapid-fire requests trying to insert the same session id
    @Synchronized
    fun findOrCreateSessionDbmsId(id: String, createIfMissing: Boolean) =
      try {
        querySessionDbmsId(id)
      } catch (e: MissingBrowserSessionException) {
        if (createIfMissing) {
          logger.debug { "Creating BrowserSession in findSessionDbmsId() - ${e.message}" }
          createBrowserSession(id)
        } else {
          -1
        }
      }

    fun querySessionDbmsId(idVal: String) =
      readonlyTx {
        with(BrowserSessionsTable) {
          select(id)
            .where { sessionId eq idVal }
            .map { it[id].value }
            .firstOrNull() ?: throw MissingBrowserSessionException(idVal)
        }
      }
  }
}

internal val ApplicationCall.browserSession get() = sessions.get<BrowserSession>()

internal val ApplicationCall.userPrincipal get() = sessions.get<UserPrincipal>()
