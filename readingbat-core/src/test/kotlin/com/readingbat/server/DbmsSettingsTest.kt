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

import com.readingbat.server.DbmsSettings.PGJDBC_NG_DRIVER
import com.readingbat.server.DbmsSettings.POSTGRESQL_DRIVER
import com.readingbat.server.DbmsSettings.requireSupportedDbmsSettings
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.sql.Driver

/**
 * Tests for [DbmsSettings.requireSupportedDbmsSettings], which stops the server at startup when its
 * database settings are still written for pgjdbc-ng, the driver readingbat-core no longer ships.
 */
class DbmsSettingsTest : StringSpec() {
  private val doUrl = "jdbc:postgresql://db.example.com:25060/readingbat?sslmode=require"

  init {
    "accepts the PostgreSQL driver with the default, Digital Ocean, and Cloud SQL URL forms" {
      listOf(
        "jdbc:postgresql://localhost:5432/readingbat",
        doUrl,
        "jdbc:postgresql:///readingbat",
      ).forEach { url ->
        shouldNotThrowAny { requireSupportedDbmsSettings(POSTGRESQL_DRIVER, url) }
      }
    }

    "rejects the pgjdbc-ng driver class and names its replacement" {
      val e = shouldThrow<IllegalArgumentException> { requireSupportedDbmsSettings(PGJDBC_NG_DRIVER, doUrl) }
      e.message shouldContain PGJDBC_NG_DRIVER
      e.message shouldContain POSTGRESQL_DRIVER
    }

    "rejects a pgjdbc-ng URL in any case and names the replacement form" {
      listOf(
        "jdbc:pgsql://db.example.com:25060/readingbat?ssl.mode=Require",
        "JDBC:PGSQL://localhost:5432/readingbat",
      ).forEach { url ->
        val e = shouldThrow<IllegalArgumentException> { requireSupportedDbmsSettings(POSTGRESQL_DRIVER, url) }
        e.message shouldContain "jdbc:postgresql://"
        e.message shouldContain "sslmode=require"
      }
    }

    "rejects a migrated URL that still carries pgjdbc-ng's ssl.mode parameter" {
      listOf(
        "jdbc:postgresql://db.example.com:25060/readingbat?ssl.mode=Require",
        "jdbc:postgresql://db.example.com:25060/readingbat?user=readingbat&ssl.mode=Require",
      ).forEach { url ->
        val e = shouldThrow<IllegalArgumentException> { requireSupportedDbmsSettings(POSTGRESQL_DRIVER, url) }
        e.message shouldContain "sslmode"
      }
    }

    "does not echo the URL, which can carry a password, in the error" {
      val url = "jdbc:pgsql://db.example.com:25060/readingbat?password=hunter2-secret"
      val e = shouldThrow<IllegalArgumentException> { requireSupportedDbmsSettings(POSTGRESQL_DRIVER, url) }
      e.message shouldNotContain "hunter2-secret"
      e.message shouldNotContain "db.example.com"
    }

    "the default driver is on the runtime classpath and accepts only the URL forms the check allows" {
      val driver = Class.forName(POSTGRESQL_DRIVER).getDeclaredConstructor().newInstance() as Driver
      driver.acceptsURL("jdbc:postgresql://localhost:5432/readingbat").shouldBeTrue()
      driver.acceptsURL(doUrl).shouldBeTrue()
      driver.acceptsURL("jdbc:pgsql://localhost:5432/readingbat").shouldBeFalse()
    }

    "pgjdbc-ng is no longer on the classpath" {
      shouldThrow<ClassNotFoundException> { Class.forName(PGJDBC_NG_DRIVER) }
    }
  }
}
