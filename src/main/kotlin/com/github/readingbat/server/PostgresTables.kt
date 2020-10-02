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
  Index(listOf(UserSessions.sessionRef, UserSessions.userRef), true, "user_sessions_unique")

internal val sessionChallengeInfoIndex =
  Index(listOf(SessionChallengeInfo.sessionRef, SessionChallengeInfo.md5), true, "session_challenge_info_unique")
internal val userChallengeInfoIndex =
  Index(listOf(UserChallengeInfo.userRef, UserChallengeInfo.md5), true, "user_challenge_info_unique")

internal val sessionAnswerHistoryIndex =
  Index(listOf(SessionAnswerHistory.sessionRef, SessionAnswerHistory.md5), true, "session_answer_history_unique")
internal val userAnswerHistoryIndex =
  Index(listOf(UserAnswerHistory.userRef, UserAnswerHistory.md5), true, "user_answer_history_unique")

internal val passwordResetsIndex = Index(listOf(PasswordResets.userRef), true, "password_resets_unique")

internal val geoInfosUnique = Index(listOf(GeoInfos.id), true, "geo_info_unique")

internal object BrowserSessions : LongIdTable("browser_sessions") {
  val created = datetime("created")
  val session_id = text("session_id")
}

// answersJson is a map of invocations to answers
internal object SessionChallengeInfo : LongIdTable("session_challenge_info") {
  val created = datetime("created")
  val updated = datetime("updated")
  val sessionRef = long("session_ref").references(BrowserSessions.id)
  val md5 = text("md5")
  val allCorrect = bool("all_correct")
  val likeDislike = short("like_dislike")
  val answersJson = text("answers_json")
}

internal object SessionAnswerHistory : LongIdTable("session_answer_history") {
  val created = datetime("created")
  val updated = datetime("updated")
  val sessionRef = long("session_ref").references(BrowserSessions.id)
  val md5 = text("md5")
  val invocation = text("invocation")
  val correct = bool("correct")
  val incorrectAttempts = integer("incorrect_attempts")
  val historyJson = text("history_json")
}

internal object Users : LongIdTable() {
  val created = datetime("created")
  val updated = datetime("updated")
  val userId = text("user_id")
  val email = text("email")
  val name = text("name")
  val salt = text("salt")
  val digest = text("digest")
  val enrolledClassCode = text("enrolled_class_code")
  val defaultLanguage = text("default_language")
}

internal object UserSessions : LongIdTable("user_sessions") {
  val created = datetime("created")
  val updated = datetime("updated")
  val sessionRef = long("session_ref").references(BrowserSessions.id)
  val userRef = long("user_ref").references(Users.id)
  val activeClassCode = text("active_class_code")
  val previousTeacherClassCode = text("previous_teacher_class_code")
}

// answersJson is a map of invocations to answers
internal object UserChallengeInfo : LongIdTable("user_challenge_info") {
  val created = datetime("created")
  val updated = datetime("updated")
  val userRef = long("user_ref").references(Users.id)
  val md5 = text("md5")
  val allCorrect = bool("all_correct")
  val likeDislike = short("like_dislike")
  val answersJson = text("answers_json")
}

internal object UserAnswerHistory : LongIdTable("user_answer_history") {
  val created = datetime("created")
  val updated = datetime("updated")
  val userRef = long("user_ref").references(Users.id)
  val md5 = text("md5")
  val invocation = text("invocation")
  val correct = bool("correct")
  val incorrectAttempts = integer("incorrect_attempts")
  val historyJson = text("history_json")
}

internal object Classes : LongIdTable("classes") {
  val created = datetime("created")
  val updated = datetime("updated")
  val userRef = long("user_ref").references(Users.id)
  val classCode = text("class_code")
  val description = text("description")
}

object Enrollees : LongIdTable("enrollees") {
  val created = datetime("created")
  val classesRef = long("classes_ref").references(Classes.id)
  val userRef = long("user_ref").references(Users.id)
}

object PasswordResets : LongIdTable("password_resets") {
  val created = datetime("created")
  val updated = datetime("updated")
  val userRef = long("user_ref").references(Users.id)
  val resetId = text("reset_id")
  val email = text("email")
}

object GeoInfos : LongIdTable("geo_info") {
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

