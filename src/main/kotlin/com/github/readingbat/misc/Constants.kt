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
  const val MSG = "msg"
  const val BACK_PATH = "backPath"
  const val ROOT = "/"
  const val STATIC_ROOT = "/static"
  const val CHALLENGE_ROOT = "/content"
  const val PLAYGROUND_ROOT = "/playground"
  const val WHITE_CHECK = "white-check.jpg"
  const val GREEN_CHECK = "green-check.jpg"
}

object Endpoints {
  const val USER_PREFS = "/user-prefs"
  const val ABOUT = "/about.html"
  const val CLASSROOM = "/classroom.html"
  const val CREATE_ACCOUNT = "/create-account"
  const val PRIVACY = "/privacy.html"
  const val RESET_PASSWORD = "/reset-password"
  const val CHECK_ANSWERS_ROOT = "/check-answers"
  const val CSS_NAME = "/styles.css"
  const val FAV_ICON = "/favicon.ico"
}

object KeyPrefixes {
  const val USER_ID = "userId"
  const val SALT = "salt"
  const val PASSWD = "password"
  const val CORRECT_ANSWERS = "correct-answers"
  const val CHALLENGE_ANSWERS = "challenge-answers"
  const val ANSWER_HISTORY = "answer-history"
  const val AUTH = "auth"
  const val NO_AUTH = "noauth"
}

object FormFields {
  const val USERNAME = "username"
  const val PASSWORD = "passwd"
  const val CONFIRM_PASSWORD = "confirm_passwd"
  const val CURR_PASSWORD = "curr_passwd"
  const val NEW_PASSWORD = "new_passwd"
  const val PREF_ACTION = "pref_action"
  const val UPDATE_PASSWORD = "Update Password"
  const val DELETE_ACCOUNT = "Delete Account"
}

object AuthName {
  const val SESSION = "session"
  const val FORM = "form"
  const val AUTH_COOKIE = "auth"
}

object AuthRoutes {
  const val LOGOUT = "/logout"
  const val COOKIES = "/cookies"
}

object EnvVars {
  const val REDIRECT_HOST_NAME = "REDIRECT_HOST_NAME"
  const val PRODUCTION = "PRODUCTION"
  const val REDISTOGO_URL = "REDISTOGO_URL"
}
