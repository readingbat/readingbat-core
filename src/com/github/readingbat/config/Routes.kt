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

import com.codahale.metrics.jvm.ThreadDump
import com.github.pambrose.common.util.randomId
import com.github.readingbat.RedisPool.redisAction
import com.github.readingbat.config.ThreadDumpInfo.threadDump
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.AuthRoutes.LOGOUT
import com.github.readingbat.misc.AuthRoutes.PROFILE
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
import com.github.readingbat.misc.FormFields.PASSWORD
import com.github.readingbat.misc.FormFields.USERNAME
import com.github.readingbat.misc.KeyPrefixes.ANSWER_HISTORY
import com.github.readingbat.misc.KeyPrefixes.CHALLENGE_ANSWERS
import com.github.readingbat.misc.KeyPrefixes.PASSWD
import com.github.readingbat.misc.KeyPrefixes.SALT
import com.github.readingbat.misc.KeyPrefixes.USER_ID
import com.github.readingbat.misc.cssContent
import com.github.readingbat.misc.newStringSalt
import com.github.readingbat.misc.sha256
import com.github.readingbat.pages.*
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.auth.UserHashedTableAuth
import io.ktor.auth.UserIdPrincipal
import io.ktor.html.respondHtml
import io.ktor.http.ContentType.Text.CSS
import io.ktor.http.ContentType.Text.Html
import io.ktor.http.ContentType.Text.Plain
import io.ktor.http.content.resources
import io.ktor.http.content.static
import io.ktor.request.receiveParameters
import io.ktor.response.respondRedirect
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.sessions.clear
import io.ktor.sessions.get
import io.ktor.sessions.sessions
import io.ktor.sessions.set
import io.ktor.util.getDigestFunction
import kotlinx.coroutines.runBlocking
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.div
import mu.KotlinLogging
import java.io.ByteArrayOutputStream
import java.lang.management.ManagementFactory
import java.util.*
import java.util.regex.Pattern
import kotlin.text.Charsets.UTF_8


private val logger = KotlinLogging.logger {}

internal fun Application.routes(content: ReadingBatContent) {

  routing {

    get("/") {
      val tab = defaultTab(content)
      call.respondRedirect(tab)
    }

    get("/$challengeRoot") {
      val tab = defaultTab(content)
      call.respondRedirect(tab)
    }

    post("/$checkAnswers") {
      val principal = retrievePrincipal()
      checkUserAnswers(content, principal, call.sessions.get<ClientSession>())
    }

    get(LOGOUT) {
      // Purge UserIdPrincipal from cookie data
      call.sessions.clear<UserIdPrincipal>()
      call.respondRedirect("/")
    }

    get(PROFILE) {
      val principal = call.sessions.get<UserIdPrincipal>()
      call.respondHtml {
        body {
          div { +"Hello, ${principal?.name}!" }
          div { a(href = LOGOUT) { +"log out" } }
        }
      }
    }

    get("/$cssName") {
      val css = cssContent
      call.respondText(css, CSS)
    }

    get("/favicon.ico") {
      call.respondRedirect("/$staticRoot/$icons/favicon.ico")
    }

    get("/session-register") {
      val session = call.sessions.get<ClientSession>()
      if (session == null) {
        call.sessions.set(ClientSession(name = "Student name", id = randomId()))
        logger.info { call.sessions.get<ClientSession>() }
      }
      call.respondText { "registered ${call.sessions.get<ClientSession>()}" }
    }

    get("/session-check") {
      val session = call.sessions.get<ClientSession>()
      logger.info { session }
      call.respondText { "checked $session" }
    }

    get("/session-2clear") {
      logger.info { call.sessions.get<ClientSession>() }
      call.sessions.clear<ClientSession>()
      call.respondText { "cleared" }
    }

    get(ABOUT) { respondWith { aboutPage(content) } }

    get(PREFS) { respondWith { prefsPage(content) } }

    get(CREATE_ACCOUNT) {
      val returnPath = call.request.queryParameters[RETURN_PATH] ?: "/"
      respondWith { createAccount(content, "", "", returnPath) }
    }

    post(CREATE_ACCOUNT) {
      val parameters = call.receiveParameters()
      val username = parameters[USERNAME] ?: ""
      val password = parameters[PASSWORD] ?: ""
      val returnPath = parameters[RETURN_PATH] ?: "/"
      logger.info { "Return path = $returnPath" }
      when {
        username.isBlank() ->
          respondWith {
            createAccount(content, "", "Empty email value", returnPath)
          }
        !username.isValidEmail() ->
          respondWith {
            createAccount(content, username, "Invalid email value", returnPath)
          }
        password.isBlank() ->
          respondWith {
            createAccount(content, username, "Empty password value", returnPath)
          }
        password.length < 6 ->
          respondWith {
            createAccount(content, username, "Password value too short (must have at least 6 characters)", returnPath)
          }
        password == "password" -> respondWith {
          createAccount(content, username, "Surely you can come up with a more clever password", returnPath)
        }
        else -> {
          redisAction { redis ->
            runBlocking {
              // Check if username already exists
              val userIdKey = userIdKey(username)
              if (redis.exists(userIdKey)) {
                respondWith { createAccount(content, "", "Username already exists: $username", returnPath) }
              }
              else {
                // The userName (email) is stored in only one KV pair, enabling changes to the userName
                // Three things are stored:
                // username -> userId
                // userId -> salt
                // userId -> sha256-encoded password

                val userId = UserId()
                val salt = newStringSalt()

                redis.multi().apply {
                  set(userIdKey, userId.id)
                  set(userId.saltKey(), salt)
                  set(userId.passwordKey(), password.sha256(salt))
                  exec()
                }

                call.respondRedirect(returnPath)
              }
            }
          }
        }
      }
    }

    get(PRIVACY) { respondWith { privacy(content) } }

    get("/ping") { call.respondText("pong", Plain) }

    get("/threaddump") {
      try {
        val baos = ByteArrayOutputStream()
        baos.use { threadDump.dump(true, true, it) }
        val output = String(baos.toByteArray(), UTF_8)
        call.respondText(output, Plain)
      } catch (e: NoClassDefFoundError) {
        call.respondText("Sorry, your runtime environment does not allow dump threads.", Plain)
      }
    }

    static("/$staticRoot") {
      resources(staticRoot)
    }
  }
}

private val emailPattern by lazy {
  Pattern.compile(
    "^(([\\w-]+\\.)+[\\w-]+|([a-zA-Z]|[\\w-]{2,}))@"
        + "((([0-1]?[0-9]{1,2}|25[0-5]|2[0-4][0-9])\\.([0-1]?"
        + "[0-9]{1,2}|25[0-5]|2[0-4][0-9])\\."
        + "([0-1]?[0-9]{1,2}|25[0-5]|2[0-4][0-9])\\.([0-1]?"
        + "[0-9]{1,2}|25[0-5]|2[0-4][0-9]))|"
        + "([a-zA-Z]+[\\w-]+\\.)+[a-zA-Z]{2,4})$")
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

private fun String.isValidEmail() = emailPattern.matcher(this).matches()

suspend fun PipelineCall.respondWith(block: () -> String) = call.respondText(block.invoke(), Html)

private object ThreadDumpInfo {
  internal val threadDump by lazy { ThreadDump(ManagementFactory.getThreadMXBean()) }
}

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
