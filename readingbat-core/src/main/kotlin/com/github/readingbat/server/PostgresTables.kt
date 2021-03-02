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

package com.github.readingbat.server

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.Index
import org.jetbrains.exposed.sql.jodatime.datetime

internal val userSessionIndex =
  Index(listOf(UserSessionsTable.sessionRef, UserSessionsTable.userRef), true, "user_sessions_unique")

internal val sessionChallengeInfoIndex =
  Index(listOf(SessionChallengeInfoTable.sessionRef, SessionChallengeInfoTable.md5),
        true,
        "session_challenge_info_unique")
internal val userChallengeInfoIndex =
  Index(listOf(UserChallengeInfoTable.userRef, UserChallengeInfoTable.md5), true, "user_challenge_info_unique")

internal val sessionAnswerHistoryIndex =
  Index(listOf(SessionAnswerHistoryTable.sessionRef, SessionAnswerHistoryTable.md5),
        true,
        "session_answer_history_unique")
internal val userAnswerHistoryIndex =
  Index(listOf(UserAnswerHistoryTable.userRef, UserAnswerHistoryTable.md5), true, "user_answer_history_unique")

internal val passwordResetsIndex = Index(listOf(PasswordResetsTable.userRef), true, "password_resets_unique")

internal val geoInfosUnique = Index(listOf(GeoInfosTable.id), true, "geo_info_unique")

internal object BrowserSessions : LongIdTable("browser_sessions") {
  val created = datetime("created")
  val sessionId = text("session_id")
}

// answersJson is a map of invocations to answers
internal object SessionChallengeInfoTable : LongIdTable("session_challenge_info") {
  val created = datetime("created")
  val updated = datetime("updated")
  val sessionRef = long("session_ref").references(BrowserSessions.id)
  val md5 = text("md5")
  val allCorrect = bool("all_correct")
  val likeDislike = short("like_dislike")
  val answersJson = text("answers_json")
}

internal object SessionAnswerHistoryTable : LongIdTable("session_answer_history") {
  val created = datetime("created")
  val updated = datetime("updated")
  val sessionRef = long("session_ref").references(BrowserSessions.id)
  val md5 = text("md5")
  val invocation = text("invocation")
  val correct = bool("correct")
  val incorrectAttempts = integer("incorrect_attempts")
  val historyJson = text("history_json")
}

internal object UsersTable : LongIdTable("users") {
  val created = datetime("created")
  val updated = datetime("updated")
  val userId = text("user_id")
  val email = text("email")
  val fullName = text("name")
  val salt = text("salt")
  val digest = text("digest")
  val enrolledClassCode = text("enrolled_class_code")
  val defaultLanguage = text("default_language")
}

internal object UserSessionsTable : LongIdTable("user_sessions") {
  val created = datetime("created")
  val updated = datetime("updated")
  val sessionRef = long("session_ref").references(BrowserSessions.id)
  val userRef = long("user_ref").references(UsersTable.id)
  val activeClassCode = text("active_class_code")
  val previousTeacherClassCode = text("previous_teacher_class_code")
}

// answersJson is a map of invocations to answers
internal object UserChallengeInfoTable : LongIdTable("user_challenge_info") {
  val created = datetime("created")
  val updated = datetime("updated")
  val userRef = long("user_ref").references(UsersTable.id)
  val md5 = text("md5")
  val allCorrect = bool("all_correct")
  val likeDislike = short("like_dislike")
  val answersJson = text("answers_json")
}

internal object UserAnswerHistoryTable : LongIdTable("user_answer_history") {
  val created = datetime("created")
  val updated = datetime("updated")
  val userRef = long("user_ref").references(UsersTable.id)
  val md5 = text("md5")
  val invocation = text("invocation")
  val correct = bool("correct")
  val incorrectAttempts = integer("incorrect_attempts")
  val historyJson = text("history_json")
}

@Suppress("unused")
internal object ClassesTable : LongIdTable("classes") {
  val created = datetime("created")
  val updated = datetime("updated")
  val userRef = long("user_ref").references(UsersTable.id)
  val classCode = text("class_code")
  val description = text("description")
}

object EnrolleesTable : LongIdTable("enrollees") {
  val created = datetime("created")
  val classesRef = long("classes_ref").references(ClassesTable.id)
  val userRef = long("user_ref").references(UsersTable.id)
}

object PasswordResetsTable : LongIdTable("password_resets") {
  val created = datetime("created")
  val updated = datetime("updated")
  val userRef = long("user_ref").references(UsersTable.id)
  val resetId = text("reset_id")
  val email = text("email")
}

object GeoInfosTable : LongIdTable("geo_info") {
  val created = datetime("created")
  val ip = text("ip")
  val json = text("json")
  val continentCode = text("continent_code")
  val continentName = text("continent_name")
  val countryCode2 = text("country_code2")
  val countryCode3 = text("country_code3")
  val countryName = text("country_name")
  val countryCapital = text("country_capital")
  val district = text("district")
  val city = text("city")
  val stateProv = text("state_prov")
  val zipcode = text("zipcode")
  val latitude = text("latitude")
  val longitude = text("longitude")
  val isEu = text("is_eu")
  val callingCode = text("calling_code")
  val countryTld = text("country_tld")
  val countryFlag = text("country_flag")
  val isp = text("isp")
  val connectionType = text("connection_type")
  val organization = text("organization")
  val timeZone = text("time_zone")
}

object ServerRequestsTable : LongIdTable("server_requests") {
  val created = datetime("created")
  val requestId = text("request_id")
  val sessionRef = long("session_ref").references(BrowserSessions.id)
  val userRef = long("user_ref").references(UsersTable.id)
  val geoRef = long("geo_ref").references(GeoInfosTable.id)
  val verb = text("verb")
  val path = text("path")
  val queryString = text("query_string")
  val userAgent = text("user_agent")
  val duration = long("duration")
}