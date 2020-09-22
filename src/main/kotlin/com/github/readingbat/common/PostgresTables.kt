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

package com.github.readingbat.common

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.jodatime.datetime

object BrowserSessions : LongIdTable("browser_sessions") {
  val created = datetime("created")
  val updated = datetime("updated")
  val session_id = text("session_id")
  val userRef = long("user_ref") // May not have a value
  val activeClassCode = text("active_class_code")
  val previousTeacherClassCode = text("previous_teacher_class_code")

  override fun toString(): String = "$id $activeClassCode $previousTeacherClassCode"
}

object SessionChallengeInfo : LongIdTable("session_challenge_info") {
  val created = datetime("created")
  val updated = datetime("updated")
  val sessionRef = long("session_ref").references(BrowserSessions.id)
  val md5 = text("md5")
  val correct = bool("correct")
  val likeDislike = short("like_dislike")
  val answersJson = text("answers_json")

  override fun toString(): String = "$id $md5 $correct $likeDislike"
}

object SessionAnswerHistory : LongIdTable("session_answer_history") {
  val created = datetime("created")
  val updated = datetime("updated")
  val sessionRef = long("session_ref").references(BrowserSessions.id)
  val md5 = text("md5")
  val invocation = text("invocation")
  val correct = bool("correct")
  val incorrectAttempts = integer("incorrect_attempts")
  val historyJson = text("history_json")

  override fun toString(): String = "$id $md5 $invocation $correct"
}

object Users : LongIdTable() {
  val created = datetime("created")
  val updated = datetime("updated")
  val userId = varchar("user_id", 25)
  val email = text("email")
  val name = text("name")
  val salt = text("salt")
  val digest = text("digest")
  val enrolledClassCode = text("enrolled_class_code")

  override fun toString(): String = userId.toString()
}

object Classes : LongIdTable("classes") {
  val created = datetime("created")
  val updated = datetime("updated")
  val userRef = long("user_ref").references(Users.id)
  val classCode = text("class_code")
  val description = text("description")

  override fun toString(): String = "$id $classCode"
}

object UserChallengeInfo : LongIdTable("user_challenge_info") {
  val created = datetime("created")
  val updated = datetime("updated")
  val userRef = long("user_ref").references(Users.id)
  val md5 = text("md5")
  val correct = bool("correct")
  val likeDislike = short("like_dislike")
  val answersJson = text("answers_json")

  override fun toString(): String = "$id $md5 $correct $likeDislike"
}

object UserAnswerHistory : LongIdTable("user_answer_history") {
  val created = datetime("created")
  val updated = datetime("updated")
  val userRef = long("user_ref").references(Users.id)
  val md5 = text("md5")
  val invocation = text("invocation")
  val correct = bool("correct")
  val incorrectAttempts = integer("incorrect_attempts")
  val historyJson = text("history_json")

  override fun toString(): String = "$id $md5 $invocation $correct"
}
