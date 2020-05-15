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
  const val sessionid = "sessionid"
  const val playground = "playground"
  const val titleText = "ReadingBat"
  const val staticRoot = "static"
  const val icons = "icons"
  const val cssName = "styles.css"
  const val rootPath = "content"
  const val languagePath = "language"
  const val groupNamePath = "groupName"
  const val challengeNamePath = "challengeName"
}

object FormFields {
  const val USERNAME = "username"
  const val PASSWORD = "password"
}

object AuthName {
  const val SESSION = "session"
  const val FORM = "form"
}

object CommonRoutes {
  const val LOGIN = "/login"
  const val LOGOUT = "/logout"
  const val PROFILE = "/profile"
}

object Cookies {
  const val AUTH_COOKIE = "auth"
}

object TestCredentials {
  const val userEmail = "pambrose@mac.com"
  const val password = "bar"
}
