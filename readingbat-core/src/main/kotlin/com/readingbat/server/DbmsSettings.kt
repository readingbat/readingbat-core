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

/**
 * Rejects connection settings written for pgjdbc-ng, the JDBC driver readingbat-core used to ship.
 *
 * pgjdbc-ng's last release (0.8.9, May 2021) bundles its own copy of Netty 4.1.63, which carries
 * critical and high CVEs that pgjdbc-ng will never pick up a fix for, and the server's TLS connection
 * to the database ran through it. The server now connects with the PostgreSQL JDBC driver
 * (`org.postgresql:postgresql`), which rejects pgjdbc-ng's `jdbc:pgsql:` URLs and does not recognize
 * its URL parameters. Old settings are not translated, since a pgjdbc-ng-only parameter would be
 * dropped without a word; startup fails instead, with a message naming the replacement.
 */
internal object DbmsSettings {
  internal const val POSTGRESQL_DRIVER = "org.postgresql.Driver"
  internal const val PGJDBC_NG_DRIVER = "com.impossibl.postgres.jdbc.PGDriver"
  internal const val PGJDBC_NG_URL_PREFIX = "jdbc:pgsql:"

  // pgjdbc-ng's TLS parameter. The PostgreSQL driver ignores parameters it does not know, so a URL
  // whose prefix was migrated but which still says ssl.mode=Require would quietly lose that setting.
  private val pgjdbcNgSslMode = Regex("""[?&]ssl\.mode=""", RegexOption.IGNORE_CASE)

  /**
   * Fails with an [IllegalArgumentException] if [driverClassName] or [jdbcUrl] is still in
   * pgjdbc-ng's form. The messages never echo the URL, which can carry a password.
   */
  internal fun requireSupportedDbmsSettings(
    driverClassName: String,
    jdbcUrl: String,
  ) {
    require(driverClassName != PGJDBC_NG_DRIVER) {
      "DBMS_DRIVER_CLASSNAME (dbms.driverClassName) is $PGJDBC_NG_DRIVER, the pgjdbc-ng driver, which " +
        "readingbat-core no longer ships. Set it to $POSTGRESQL_DRIVER, or leave it unset to get that default"
    }
    require(!jdbcUrl.startsWith(PGJDBC_NG_URL_PREFIX, ignoreCase = true)) {
      "DBMS_URL (dbms.jdbcUrl) is a pgjdbc-ng URL ($PGJDBC_NG_URL_PREFIX...), which the PostgreSQL JDBC " +
        "driver that readingbat-core now uses rejects. Change it to jdbc:postgresql://<host>:<port>/<database>, " +
        "and replace ssl.mode=Require with sslmode=require"
    }
    require(!pgjdbcNgSslMode.containsMatchIn(jdbcUrl)) {
      "DBMS_URL (dbms.jdbcUrl) sets ssl.mode, pgjdbc-ng's TLS parameter, which the PostgreSQL JDBC driver " +
        "ignores. Use sslmode instead, e.g. sslmode=require in place of ssl.mode=Require"
    }
  }
}
