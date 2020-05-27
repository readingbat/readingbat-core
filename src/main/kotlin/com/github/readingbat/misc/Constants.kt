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

package com.github.readingbat.misc

internal object Constants {
  const val READING_BAT = "ReadingBat"
  const val sessionid = "sessionid"
  const val ICONS = "icons"
  const val RETURN_PATH = "returnPath"
  const val RESET_ID = "reset_id"
  const val MSG = "msg"
  const val BACK_PATH = "backPath"
  const val ROOT = "/"
  const val STATIC_ROOT = "/static"
  const val CHALLENGE_ROOT = "/content"
  const val PLAYGROUND_ROOT = "/playground"
  const val WHITE_CHECK = "white-check.jpg"
  const val GREEN_CHECK = "green-check.jpg"
  const val DBMS_DOWN = "Database is down"
  const val INVALID_RESET_ID = "Invalid reset_id"
  const val RESP = "response"
}

internal object Endpoints {
  const val USER_PREFS = "/user-prefs"
  const val ABOUT = "/about.html"
  const val CLASSROOM = "/classroom.html"
  const val CREATE_ACCOUNT = "/create-account"
  const val PRIVACY = "/privacy.html"
  const val PASSWORD_CHANGE = "/password-change"
  const val PASSWORD_RESET = "/password-reset"
  const val CHECK_ANSWERS_ROOT = "/check-answers"
  const val CSS_NAME = "/styles.css"
  const val FAV_ICON = "/favicon.ico"
}

internal object KeyPrefixes {
  const val USER_ID = "userId"
  const val SALT = "salt"
  const val PASSWD = "password"
  const val CORRECT_ANSWERS = "correct-answers"
  const val CHALLENGE_ANSWERS = "challenge-answers"
  const val ANSWER_HISTORY = "answer-history"
  const val RESET = "password-reset"
  const val USERID_RESET = "userid_password-reset"
  const val AUTH = "auth"
  const val NO_AUTH = "noauth"
}

internal object FormFields {
  const val USERNAME = "username"
  const val PASSWORD = "passwd"
  const val CONFIRM_PASSWORD = "confirm_passwd"
  const val CLASS_CODE = "class_code"
  const val CURR_PASSWORD = "curr_passwd"
  const val NEW_PASSWORD = "new_passwd"
  const val USER_PREFS_ACTION = "pref_action"
  const val UPDATE_PASSWORD = "Update Password"
  const val JOIN_CLASS = "Join"
  const val DELETE_ACCOUNT = "Delete Account"
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

internal object EnvVars {
  const val REDIRECT_HOST_NAME = "REDIRECT_HOST_NAME"
  const val PRODUCTION = "PRODUCTION"
}
