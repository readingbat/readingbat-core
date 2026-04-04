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

package com.readingbat.kotest

import com.readingbat.common.Property
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.testcontainers.containers.PostgreSQLContainer

object TestDatabase {
  private val container: PostgreSQLContainer<*> by lazy {
    PostgreSQLContainer("postgres:16-alpine")
      .withDatabaseName("readingbat_test")
      .withUsername("test")
      .withPassword("test")
      .also { it.start() }
  }

  fun connectAndMigrate(): Database {
    val db =
      Database.connect(
        url = container.jdbcUrl,
        user = container.username,
        password = container.password,
        driver = "org.postgresql.Driver",
      )

    Flyway.configure()
      .dataSource(container.jdbcUrl, container.username, container.password)
      .locations("classpath:db/migration")
      .load()
      .migrate()

    Property.DBMS_ENABLED.setProperty("true")

    return db
  }
}
