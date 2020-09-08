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

import com.github.readingbat.common.Constants.CLASS_CODE_QP
import com.github.readingbat.common.Constants.GROUP_NAME_QP
import com.github.readingbat.common.Constants.LANG_TYPE_QP
import com.github.readingbat.common.Constants.STATIC
import com.github.readingbat.common.Constants.USER_ID_QP
import com.github.readingbat.server.GroupName
import com.github.readingbat.server.LanguageName

internal object Constants {
  const val SESSION_ID = "sessionid"
  const val ICONS = "icons"
  const val MSG = "msg"
  const val BACK_PATH = "backPath"
  const val STATIC = "static"
  const val PRISM = "prism"
  const val RESP = "response"
  const val LIKE_DESC = "likeDesc"
  const val NO_ANSWER_COLOR = "white"
  const val CORRECT_COLOR = "#4EAA3A"
  const val WRONG_COLOR = "#FF0000" //red
  const val INCOMPLETE_COLOR = "#F1F1F1"
  const val LABEL_WIDTH = "width:250"
  const val INVALID_RESET_ID = "Invalid reset_id"
  const val PING_CODE = "P"
  const val COLUMN_CNT = 3
  const val LANG_SRC = "lang"
  const val GROUP_SRC = "groupName"
  const val CHALLENGE_SRC = "challengeName"
  const val PROCESS_USER_ANSWERS_JS_FUNC = "processUserAnswers"
  const val LIKE_DISLIKE_JS_FUNC = "likeDislike"
  const val NO_TRACK_HEADER = "NO_TRACK"

  const val CLASS_CODE_QP = "class-code"
  const val USER_ID_QP = "user-id"
  const val LANG_TYPE_QP = "lang-type"
  const val GROUP_NAME_QP = "group-name"

  const val YES = "Y"
  const val NO = "N"
  const val UNANSWERED = "U"

  val DBMS_DOWN = Message("Database is down", true)
}

internal object PropertyNames {
  internal const val READINGBAT = "readingbat"
  internal const val SITE = "site"
  internal const val AGENT = "agent"
  internal const val CLASSES = "classes"
  internal const val CONTENT = "content"
  internal const val CHALLENGES = "challenges"
}

internal object Endpoints {
  const val ROOT = "/"

  //const val STATIC_ROOT = "/$STATIC"
  const val STATIC_ROOT = "https://static.readingbat.com"
  const val CHALLENGE_ROOT = "/content"
  const val PLAYGROUND_ROOT = "/playground"
  const val USER_PREFS_ENDPOINT = "/user-prefs"
  const val USER_PREFS_POST_ENDPOINT = "/user-prefs-post"
  const val TEACHER_PREFS_ENDPOINT = "/teacher-prefs"
  const val CLASS_SUMMARY_ENDPOINT = "/class-summary"
  const val STUDENT_SUMMARY_ENDPOINT = "/student-summary"
  const val SYSTEM_ADMIN_ENDPOINT = "/system-admin"
  const val TEACHER_PREFS_POST_ENDPOINT = "/teacher-prefs-post"
  const val ENABLE_STUDENT_MODE_ENDPOINT = "/enable-student-mode"
  const val ENABLE_TEACHER_MODE_ENDPOINT = "/enable-teacher-mode"
  const val ADMIN_ENDPOINT = "/admin"
  const val ADMIN_POST_ENDPOINT = "/admin-post"
  const val USER_INFO_ENDPOINT = "/userinfo"
  const val ABOUT_ENDPOINT = "/about.html"
  const val CONFIG_ENDPOINT = "/config"
  const val SESSIONS_ENDPOINT = "/sessions"
  const val CREATE_ACCOUNT_ENDPOINT = "/create-account"
  const val CREATE_ACCOUNT_POST_ENDPOINT = "/create-account-post"
  const val PRIVACY_ENDPOINT = "/privacy.html"
  const val PASSWORD_CHANGE_POST_ENDPOINT = "/password-change-post"
  const val PASSWORD_RESET_ENDPOINT = "/password-reset"
  const val PASSWORD_RESET_POST_ENDPOINT = "/password-reset-post"
  const val FAV_ICON_ENDPOINT = "/favicon.ico"
  const val ROBOTS_ENDPOINT = "/robots.txt"
  const val CHALLENGE_ENDPOINT = "/challenge"
  const val CHALLENGE_GROUP_ENDPOINT = "/challenge-group"
  const val CHECK_ANSWERS_ENDPOINT = "/check-answers"
  const val LIKE_DISLIKE_ENDPOINT = "/like-dislike"
  const val CLEAR_GROUP_ANSWERS_ENDPOINT = "/clear-group-answers"
  const val CLEAR_CHALLENGE_ANSWERS_ENDPOINT = "/clear-challenge-answers"
  const val MESSAGE_ENDPOINT = "/message"
  const val RESET_CONTENT_DSL_ENDPOINT = "/reset-content"
  const val RESET_CACHE_ENDPOINT = "/reset-cache"
  const val GARBAGE_COLLECTOR_ENDPOINT = "/garbage-collector"
  const val LOAD_JAVA_ENDPOINT = "/load-java"
  const val LOAD_PYTHON_ENDPOINT = "/load-python"
  const val LOAD_KOTLIN_ENDPOINT = "/load-kotlin"

  const val DELETE_CONTENT_IN_REDIS_ENDPOINT = "/clear-caches"

  const val PING = "/ping"
  const val THREAD_DUMP = "/threaddump"
  const val LOGOUT_ENDPOINT = "/logout"

  // This is a dynamic page
  const val CSS_ENDPOINT = "/$STATIC/styles.css"

  fun classSummaryEndpoint(classCode: ClassCode) = "$CLASS_SUMMARY_ENDPOINT?$CLASS_CODE_QP=$classCode"

  fun classSummaryEndpoint(classCode: ClassCode, languageName: LanguageName, groupName: GroupName) =
    "${classSummaryEndpoint(classCode)}&$LANG_TYPE_QP=$languageName&$GROUP_NAME_QP=$groupName"

  fun studentSummaryEndpoint(classCode: ClassCode, languageName: LanguageName, student: User) =
    "$STUDENT_SUMMARY_ENDPOINT?$CLASS_CODE_QP=$classCode&$LANG_TYPE_QP=$languageName&$USER_ID_QP=${student.id}"
}

internal object StaticFileNames {
  const val WHITE_CHECK = "white-check.jpg"
  const val GREEN_CHECK = "green-check.jpg"
  const val LIKE_CLEAR_FILE = "like-clear.png"
  const val DISLIKE_CLEAR_FILE = "dislike-clear.png"
  const val LIKE_COLOR_FILE = "like-color.png"
  const val DISLIKE_COLOR_FILE = "dislike-color.png"
  const val RUN_BUTTON = "run-button.png"
}

internal object ParameterIds {
  const val STATUS_ID = "statusId"
  const val SPINNER_ID = "spinnerId"
  const val LIKE_STATUS_ID = "likeStatusId"
  const val LIKE_SPINNER_ID = "likeSpinnerId"
  const val FEEDBACK_ID = "feedbackId"
  const val HINT_ID = "hintId"
  const val SUCCESS_ID = "successId"
  const val NEXTPREVCHANCE_ID = "nextPrevChanceId"
  const val LIKE_CLEAR = "likeClear"
  const val LIKE_COLOR = "likeColor"
  const val DISLIKE_CLEAR = "dislikeClear"
  const val DISLIKE_COLOR = "dislikeColor"
}

internal object FormFields {
  const val RETURN_PARAM = "returnPath"

  const val FULLNAME_PARAM = "fullname"
  const val EMAIL_PARAM = "email"
  const val PASSWORD_PARAM = "passwd"
  const val CONFIRM_PASSWORD_PARAM = "confirm_passwd"
  const val CLASS_CODE_NAME_PARAM = "class_code"
  const val CLASS_DESC_PARAM = "class_desc"
  const val CLASSES_CHOICE_PARAM = "classes_choice"
  const val DISABLED_MODE = "classes_disabled"
  const val CURR_PASSWORD_PARAM = "curr_passwd"
  const val NEW_PASSWORD_PARAM = "new_passwd"
  const val USER_PREFS_ACTION_PARAM = "pref_action"
  const val RESET_ID_PARAM = "reset_id"
  const val ADMIN_ACTION_PARAM = "admin_action"

  const val LANGUAGE_NAME_PARAM = "language_name_key"
  const val GROUP_NAME_PARAM = "group_name_key"
  const val CHALLENGE_NAME_PARAM = "challenge_name_key"
  const val CORRECT_ANSWERS_PARAM = "correct_answers_key"
  const val CHALLENGE_ANSWERS_PARAM = "challenge_answers_key"

  const val UPDATE_PASSWORD = "Update Password"
  const val JOIN_CLASS = "Join Class"
  const val CREATE_CLASS = "Create Class"
  const val DELETE_CLASS = "Delete Class"
  const val UPDATE_ACTIVE_CLASS = "Update Active Class"
  const val WITHDRAW_FROM_CLASS = "Withdraw From Class"
  const val DELETE_ACCOUNT = "Delete Account"
  const val DELETE_ALL_DATA = "Delete All Data"
}

internal object AuthName {
  const val SESSION = "session"
  const val FORM = "form"
  const val AUTH_COOKIE = "auth"
}

internal object AuthRoutes {
  const val LOGOUT = "/logout"
  const val COOKIES = "/cookies"
}