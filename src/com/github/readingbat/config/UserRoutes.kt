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
import com.github.readingbat.misc.BrowserSession
import com.github.readingbat.misc.CSSNames.checkAnswers
import com.github.readingbat.misc.CheckAnswers.checkUserAnswers
import com.github.readingbat.misc.Constants.RETURN_PATH
import com.github.readingbat.misc.Constants.challengeRoot
import com.github.readingbat.misc.Constants.cssName
import com.github.readingbat.misc.Constants.icons
import com.github.readingbat.misc.Constants.staticRoot
import com.github.readingbat.misc.Endpoints.ABOUT
import com.github.readingbat.misc.Endpoints.CREATE_ACCOUNT
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
import io.ktor.sessions.get
import io.ktor.sessions.sessions


internal fun Routing.userRoutes(content: ReadingBatContent) {

  get("/") { redirectTo { defaultTab(content) } }

  get("/$challengeRoot") { redirectTo { defaultTab(content) } }

  post("/$checkAnswers") { checkUserAnswers(content, retrievePrincipal(), call.sessions.get<BrowserSession>()) }

  get(CREATE_ACCOUNT) { respondWith { createAccountPage(content, "", "", queryParam(RETURN_PATH) ?: "/") } }

  post(CREATE_ACCOUNT) { createAccount(content) }

  get(PRIVACY) { respondWith { privacyPage(content, queryParam(RETURN_PATH) ?: "") } }

  get(ABOUT) { respondWith { aboutPage(content) } }

  get(RESET_PASSWORD) { respondWith { resetPasswordPage(content) } }

  get(PREFS) { respondWith { prefsPage(content) } }

  get(LOGOUT) {
    // Purge AuthPrincipal from cookie data
    call.sessions.clear<UserPrincipal>()
    redirectTo { "/" }
  }

  get("/$cssName") { respondWith(CSS) { cssContent } }

  get("/favicon.ico") { redirectTo { "/$staticRoot/$icons/favicon.ico" } }

  static("/$staticRoot") {
    resources(staticRoot)
  }
}
