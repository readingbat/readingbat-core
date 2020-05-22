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

package com.github.readingbat.config

import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.AuthRoutes.LOGOUT
import com.github.readingbat.misc.CheckAnswers.checkUserAnswers
import com.github.readingbat.misc.Constants.CHALLENGE_ROOT
import com.github.readingbat.misc.Constants.ICONS
import com.github.readingbat.misc.Constants.RETURN_PATH
import com.github.readingbat.misc.Constants.ROOT
import com.github.readingbat.misc.Constants.STATIC_ROOT
import com.github.readingbat.misc.Endpoints.ABOUT
import com.github.readingbat.misc.Endpoints.CHECK_ANSWERS_ROOT
import com.github.readingbat.misc.Endpoints.CLASSROOM
import com.github.readingbat.misc.Endpoints.CREATE_ACCOUNT
import com.github.readingbat.misc.Endpoints.CSS_NAME
import com.github.readingbat.misc.Endpoints.FAV_ICON
import com.github.readingbat.misc.Endpoints.PREFS
import com.github.readingbat.misc.Endpoints.PRIVACY
import com.github.readingbat.misc.Endpoints.RESET_PASSWORD
import com.github.readingbat.misc.UserPrincipal
import com.github.readingbat.misc.createAccount
import com.github.readingbat.misc.cssContent
import com.github.readingbat.pages.*
import com.github.readingbat.queryParam
import com.github.readingbat.redirectTo
import com.github.readingbat.respondWith
import com.github.readingbat.retrievePrincipal
import io.ktor.application.call
import io.ktor.http.ContentType.Text.CSS
import io.ktor.http.content.resources
import io.ktor.http.content.static
import io.ktor.routing.Routing
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.sessions.clear
import io.ktor.sessions.sessions

internal fun Routing.userRoutes(content: ReadingBatContent) {

  get(ROOT) { redirectTo { defaultTab(content) } }

  get(CHALLENGE_ROOT) { redirectTo { defaultTab(content) } }

  post(CHECK_ANSWERS_ROOT) { checkUserAnswers(content, retrievePrincipal()) }

  get(CREATE_ACCOUNT) { respondWith { createAccountPage(content, "", "", queryParam(RETURN_PATH) ?: "/") } }

  post(CREATE_ACCOUNT) { createAccount(content) }

  get(PRIVACY) { respondWith { privacyPage(content, queryParam(RETURN_PATH) ?: "") } }

  get(ABOUT) { respondWith { aboutPage(content) } }

  get(CLASSROOM) { respondWith { classroomPage(content) } }

  get(RESET_PASSWORD) { respondWith { resetPasswordPage(content) } }

  get(PREFS) { respondWith { prefsPage(content, queryParam(RETURN_PATH) ?: "/") } }

  get(LOGOUT) {
    // Purge UserPrincipal from cookie data
    call.sessions.clear<UserPrincipal>()
    redirectTo { queryParam(RETURN_PATH) ?: "/" }
  }

  get(CSS_NAME) { respondWith(CSS) { cssContent } }

  get(FAV_ICON) { redirectTo { "$STATIC_ROOT/$ICONS/favicon.ico" } }

  static(STATIC_ROOT) {
    resources("static")
  }
}