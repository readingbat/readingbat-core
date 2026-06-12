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

import com.pambrose.common.email.Email
import com.readingbat.common.OAuthProvider
import com.readingbat.common.User
import com.readingbat.dsl.InvalidRequestException
import com.readingbat.server.FullName
import com.readingbat.server.ReadingBatServer
import com.readingbat.withTestApp
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec

/**
 * The logging WebSocket streams admin operational logs, so [WsCommon.validateLogContext] must
 * require the subscriber to be an admin — not merely any authenticated user.
 */
class LoggingWsAuthTest : StringSpec() {
  init {
    "validateLogContext rejects a valid non-admin user" {
      withTestApp {
        ReadingBatServer.adminUsersRef.store(emptySet())
        val user =
          User.createOAuthUser(
            name = FullName("Regular Student"),
            emailVal = Email("regular-student@test.com"),
            provider = OAuthProvider.GITHUB,
            providerId = "logging-ws-regular-001",
          )

        shouldThrow<InvalidRequestException> {
          WsCommon.validateLogContext(user)
        }
      }
    }

    "validateLogContext allows an admin user" {
      withTestApp {
        val user =
          User.createOAuthUser(
            name = FullName("Admin User"),
            emailVal = Email("logging-ws-admin@test.com"),
            provider = OAuthProvider.GITHUB,
            providerId = "logging-ws-admin-001",
          )
        ReadingBatServer.adminUsersRef.store(setOf(user.email.value))
        try {
          shouldNotThrowAny {
            WsCommon.validateLogContext(user)
          }
        } finally {
          ReadingBatServer.adminUsersRef.store(emptySet())
        }
      }
    }
  }
}
