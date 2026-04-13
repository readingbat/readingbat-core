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

import org.jetbrains.exposed.v1.core.Index
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.datetime.timestamp

// Exposed ORM table definitions for the ReadingBat PostgreSQL schema.
//
// Tables cover user accounts, sessions, challenge answer state and history,
// class/enrollment management, OAuth provider links, request logging, and
// IP geolocation caching. Each table extends LongIdTable with an auto-incrementing primary key.

/** Unique index on (session_ref, user_ref) for the user_sessions table. */
val userSessionIndex =
  Index(listOf(UserSessionsTable.sessionRef, UserSessionsTable.userRef), true, "user_sessions_unique")

val userChallengeInfoIndex =
  Index(listOf(UserChallengeInfoTable.userRef, UserChallengeInfoTable.md5), true, "user_challenge_info_unique")

val userAnswerHistoryIndex =
  Index(listOf(UserAnswerHistoryTable.userRef, UserAnswerHistoryTable.md5), true, "user_answer_history_unique")

val oauthLinksProviderIndex =
  Index(listOf(OAuthLinksTable.provider, OAuthLinksTable.providerId), true, "oauth_links_provider_unique")

val geoInfosUnique = Index(listOf(GeoInfosTable.id), true, "geo_info_unique")

/** Tracks anonymous browser sessions identified by a random session ID cookie. */
object BrowserSessionsTable : LongIdTable("browser_sessions") {
  val created = timestamp("created")
  val sessionId = text("session_id")
}

/** Registered user accounts with enrolled class code and default language preference. */
object UsersTable : LongIdTable("users") {
  val created = timestamp("created")
  val updated = timestamp("updated")
  val userId = text("user_id")
  val email = text("email")
  val fullName = text("name")
  val enrolledClassCode = text("enrolled_class_code")
  val defaultLanguage = text("default_language")
  val authProvider = text("auth_provider").nullable()
  val avatarUrl = text("avatar_url").nullable()
}

/** Links OAuth provider identities (GitHub, Google) to local user accounts. */
object OAuthLinksTable : LongIdTable("oauth_links") {
  val created = timestamp("created")
  val updated = timestamp("updated")
  val userRef = long("user_ref").references(UsersTable.id)
  val provider = text("provider")
  val providerId = text("provider_id")
  val providerEmail = text("provider_email")
  val accessToken = text("access_token")
}

/** Maps browser sessions to authenticated users, tracking active class code and previous teacher class code. */
object UserSessionsTable : LongIdTable("user_sessions") {
  val created = timestamp("created")
  val updated = timestamp("updated")
  val sessionRef = long("session_ref").references(BrowserSessionsTable.id)
  val userRef = long("user_ref").references(UsersTable.id)
  val activeClassCode = text("active_class_code")
  val previousTeacherClassCode = text("previous_teacher_class_code")
}

/**
 * Current answer state for each user-challenge pair. The [answersJson] column stores a
 * JSON map of invocation strings to the user's most recent answer for that invocation.
 */
object UserChallengeInfoTable : LongIdTable("user_challenge_info") {
  val created = timestamp("created")
  val updated = timestamp("updated")
  val userRef = long("user_ref").references(UsersTable.id)
  val md5 = text("md5")
  val allCorrect = bool("all_correct")
  val likeDislike = short("like_dislike")
  val answersJson = text("answers_json")
}

/** Per-invocation answer history for a user, tracking correctness and the number of incorrect attempts. */
object UserAnswerHistoryTable : LongIdTable("user_answer_history") {
  val created = timestamp("created")
  val updated = timestamp("updated")
  val userRef = long("user_ref").references(UsersTable.id)
  val md5 = text("md5")
  val invocation = text("invocation")
  val correct = bool("correct")
  val incorrectAttempts = integer("incorrect_attempts")
  val historyJson = text("history_json")
}

/** Teacher-created classes, each identified by a unique [classCode]. */
@Suppress("unused")
object ClassesTable : LongIdTable("classes") {
  val created = timestamp("created")
  val updated = timestamp("updated")
  val userRef = long("user_ref").references(UsersTable.id)
  val classCode = text("class_code")
  val description = text("description")
}

/** Junction table linking students (users) to the classes they are enrolled in. */
object EnrolleesTable : LongIdTable("enrollees") {
  val created = timestamp("created")
  val classesRef = long("classes_ref").references(ClassesTable.id)
  val userRef = long("user_ref").references(UsersTable.id)
}

/** Cached IP geolocation data fetched from the ipgeolocation.io API. */
object GeoInfosTable : LongIdTable("geo_info") {
  val created = timestamp("created")
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

/** Logs each HTTP request with session, user, geolocation, verb, path, and response duration. */
object ServerRequestsTable : LongIdTable("server_requests") {
  val created = timestamp("created")
  val requestId = text("request_id")
  val sessionRef = long("session_ref").references(BrowserSessionsTable.id)
  val userRef = long("user_ref").references(UsersTable.id)
  val geoRef = long("geo_ref").references(GeoInfosTable.id)
  val verb = text("verb")
  val path = text("path")
  val queryString = text("query_string")
  val userAgent = text("user_agent")
  val duration = long("duration")
}
