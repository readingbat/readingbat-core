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
  const val titleText = "ReadingBat"
  const val sessionid = "sessionid"
  const val ICONS = "icons"
  const val RETURN_PATH = "returnPath"
  const val STATIC_ROOT = "/static"
  const val CHALLENGE_ROOT = "/content"
  const val PLAYGROUND_ROOT = "/playground"
}

object Endpoints {
  const val PREFS = "/prefs"
  const val ABOUT = "/about.html"
  const val CREATE_ACCOUNT = "/create-account"
  const val PRIVACY = "/privacy.html"
  const val RESET_PASSWORD = "/reset"
  const val CHECK_ANSWERS_ROOT = "/check-answers"
  const val CSS_NAME = "/styles.css"
  const val FAV_ICON = "/favicon.ico"
}

object KeyPrefixes {
  const val USER_ID = "userId"
  const val SALT = "salt"
  const val PASSWD = "password"
  const val CHALLENGE_ANSWERS = "challenge-answers"
  const val ANSWER_HISTORY = "answer-history"
  const val AUTH = "auth"
  const val NO_AUTH = "noauth"
}

object FormFields {
  const val USERNAME = "username"
  const val PASSWORD = "passwd"
}

object AuthName {
  const val SESSION = "session"
  const val FORM = "form"
}

object AuthRoutes {
  const val LOGOUT = "/logout"
  const val COOKIES = "/cookies"
}

object Cookies {
  const val AUTH_COOKIE = "auth"
}
