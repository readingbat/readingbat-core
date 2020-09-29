/*
 * Copyright © 2020 Paul Ambrose (pambrose@mac.com)
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

package com.github.readingbat.utils

import com.github.pambrose.common.util.simpleClassName
import com.github.readingbat.common.PasswordResets
import com.github.readingbat.server.CustomDateTimeConstant
import com.github.readingbat.server.get
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.jodatime.CurrentDateTime
import org.jetbrains.exposed.sql.jodatime.datetime
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

object Requests : LongIdTable() {
  val created = datetime("created").defaultExpression(CurrentDateTime())
  val sessionId = varchar("sessionId", 15)
  val userId = varchar("userId", 25).index(customIndexName = "RequestsUserIdIndex")
  val remote = text("remote")
  val path = text("path")

  override fun toString(): String = userId.toString()
}

object PUsers : UUIDTable() {
  val created = varchar("created", 50)
  val email = varchar("email", 50)
  val first = varchar("first", 30)
  val last = varchar("last", 30)
  val salt = varchar("salt", 50)
  val digest = varchar("digest", 50)
  val enrolledClassCode = varchar("enrolledClassCode", 50)
}

class PUser(id: EntityID<UUID>) : UUIDEntity(id) {
  var email by PUsers.email
  var first by PUsers.first
  var last by PUsers.last
  var salt by PUsers.salt
  var digest by PUsers.digest
  var enrolledClassCode by PUsers.enrolledClassCode
  override fun toString() = email

  companion object : UUIDEntityClass<PUser>(PUsers)
}

object Cities : IntIdTable() {
  val name = varchar("name", 50)
}

class City(id: EntityID<Int>) : IntEntity(id) {
  var name by Cities.name
  override fun toString() = name

  companion object : IntEntityClass<City>(Cities)
}

/*
abstract class Function<T>(override val columnType: IColumnType)
   : ExpressionWithColumnType<T>()


class FromUnixTime(private val unixTimeParam: Long)
  : Function<DateTime>(DateColumnType(true)) {
     override fun toQueryBuilder(queryBuilder: QueryBuilder): String
         = "FROM_UNIXTIME($unixTimeParam)"

     override val columnType: IColumnType = DateColumnType(true)
   }
*/

fun main() {
  /*
  Database.connect("jdbc:pgsql://localhost:5432/postgres",
                   driver = "com.impossibl.postgres.jdbc.PGDriver",
                   user = "postgres", password = "docker")
   */

  //Database.connect(hikari())

  transaction {
    // print sql to std-out
    addLogger(StdOutSqlLogger)

    /*
    repeat(10) {
      PasswordResets
        .insert { row ->
          row[userRef] = it.toLong()+2
          row[updated] = DateTime(2020, 10, 31, 0, 0, 0)
          row[resetId] = "resest id for user $it"
          row[email] = "email value"
        }
      Thread.sleep(10000)
    }

     */
  }

  transaction {
    addLogger(StdOutSqlLogger)

    PasswordResets
      .slice(PasswordResets.created, CurrentDateTime(), CustomDateTimeConstant("NOW() - INTERVAL '1 hour'"))
      .select { PasswordResets.created greater CustomDateTimeConstant("NOW() - INTERVAL '1 hour'") }
      .forEach { row ->
        val p = row[2]
        println("${row[0]} ${row[1]} ${p} ${p?.simpleClassName}")

      }
  }
  /*
  //SchemaUtils.drop(Cities)
  //SchemaUtils.create(Cities)
  SchemaUtils.drop(Requests)
  SchemaUtils.create(Requests)

  val dur1 = measureTime {
    repeat(10) { i ->
      val stPete =
        Requests.insertAndGetId { row ->
          row[path] = "St. Petersburg $i"
        }
    }
  }

  val dur2 = measureTime {
    println("Films: ${
      Requests
        .select { Requests.path neq "dd" }
        .map {
          "${it[Requests.id]} ${it[Requests.created]} ${it[Requests.path]}"
        }.toList()
    }")
  }

  println("$dur1 and $dur2")
   */
}

