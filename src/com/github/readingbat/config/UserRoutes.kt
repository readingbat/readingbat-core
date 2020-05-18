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

import com.github.pambrose.common.util.randomId
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.AuthRoutes.LOGOUT
import com.github.readingbat.misc.CSSNames.checkAnswers
import com.github.readingbat.misc.CheckAnswers.checkUserAnswers
import com.github.readingbat.misc.Constants.RETURN_PATH
import com.github.readingbat.misc.Constants.challengeRoot
import com.github.readingbat.misc.Endpoints.ABOUT
import com.github.readingbat.misc.Endpoints.CREATE_ACCOUNT
import com.github.readingbat.misc.Endpoints.PREFS
import com.github.readingbat.misc.Endpoints.PRIVACY
import com.github.readingbat.misc.KeyPrefixes.ANSWER_HISTORY
import com.github.readingbat.misc.KeyPrefixes.CHALLENGE_ANSWERS
import com.github.readingbat.misc.KeyPrefixes.PASSWD
import com.github.readingbat.misc.KeyPrefixes.SALT
import com.github.readingbat.misc.KeyPrefixes.USER_ID
import com.github.readingbat.misc.createAccount
import com.github.readingbat.pages.*
import io.ktor.application.call
import io.ktor.auth.UserHashedTableAuth
import io.ktor.auth.UserIdPrincipal
import io.ktor.http.ContentType
import io.ktor.http.ContentType.Text.Html
import io.ktor.response.respondRedirect
import io.ktor.response.respondText
import io.ktor.routing.Routing
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.sessions.clear
import io.ktor.sessions.get
import io.ktor.sessions.sessions
import io.ktor.util.getDigestFunction
import mu.KotlinLogging
import java.util.*


private val logger = KotlinLogging.logger {}

internal fun Routing.userRoutes(content: ReadingBatContent) {

  get("/") { redirectTo { defaultTab(content) } }

  get("/$challengeRoot") { redirectTo { defaultTab(content) } }

  post("/$checkAnswers") { checkUserAnswers(content, retrievePrincipal(), call.sessions.get<ClientSession>()) }

  get(CREATE_ACCOUNT) { respondWith { createAccountPage(content, "", "", queryParam(RETURN_PATH) ?: "/") } }

  post(CREATE_ACCOUNT) { createAccount(content) }

  get(PRIVACY) { respondWith { privacy(content) } }

  get(ABOUT) { respondWith { aboutPage(content) } }

  get(PREFS) { respondWith { prefsPage(content) } }

  get(LOGOUT) {
    // Purge UserIdPrincipal from cookie data
    call.sessions.clear<UserIdPrincipal>()
    call.respondRedirect("/")
  }
}

internal class UserId(val id: String = randomId(25)) {
  fun saltKey() = "$SALT|$id"

  fun passwordKey() = "$PASSWD|$id"

  fun challengeKey(languageName: String, groupName: String, challengeName: String) =
    listOf(CHALLENGE_ANSWERS, id, languageName, groupName, challengeName).joinToString("|")

  fun argumentKey(languageName: String, groupName: String, challengeName: String, argument: String) =
    listOf(ANSWER_HISTORY, id, languageName, groupName, challengeName, argument).joinToString("|")
}

internal fun userIdKey(username: String) = "$USER_ID|$username"

internal fun PipelineCall.queryParam(key: String): String? = call.request.queryParameters[key]

internal suspend fun PipelineCall.respondWith(contentTye: ContentType = Html, block: () -> String) =
  call.respondText(block.invoke(), contentTye)

internal suspend fun PipelineCall.redirectTo(block: () -> String) = call.respondRedirect(block.invoke())

internal data class ClientSession(val name: String, val id: String) {
  fun challengeKey(languageName: String, groupName: String, challengeName: String) =
    listOf(CHALLENGE_ANSWERS, id, languageName, groupName, challengeName).joinToString("|")

  fun argumentKey(languageName: String, groupName: String, challengeName: String, argument: String) =
    listOf(ANSWER_HISTORY, id, languageName, groupName, challengeName, argument).joinToString("|")
}

internal data class ChallengeAnswers(val id: String, val answers: MutableMap<String, String> = mutableMapOf())

// https://gist.github.com/lovubuntu/164b6b9021f5ba54cefc67f60f7a1a25
// load with sha256 for "test"
val hashedUserTable = UserHashedTableAuth(getDigestFunction("SHA-256") { "ktor${it.length}" },
                                          table = mapOf("test" to
                                                            Base64.getDecoder()
                                                              .decode("GSjkHCHGAxTTbnkEDBbVYd+PUFRlcWiumc4+MWE9Rvw="))
                                         )
