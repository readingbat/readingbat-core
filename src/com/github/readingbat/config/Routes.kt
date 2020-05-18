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
import com.github.readingbat.misc.Constants.ABOUT
import com.github.readingbat.misc.Constants.CREATE_ACCOUNT
import com.github.readingbat.misc.Constants.PREFS
import com.github.readingbat.misc.Constants.PRIVACY
import com.github.readingbat.misc.Constants.challengeRoot
import com.github.readingbat.misc.Constants.cssName
import com.github.readingbat.misc.Constants.icons
import com.github.readingbat.misc.Constants.staticRoot
import com.github.readingbat.misc.FormFields.PASSWORD
import com.github.readingbat.misc.FormFields.USERNAME
import com.github.readingbat.misc.cssContent
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

internal fun Application.routes(readingBatContent: ReadingBatContent) {

  routing {

    //languageGroup(readingBatContent)
    //challengeGroup(readingBatContent)

    get("/") {
      val tab = defaultTab(readingBatContent)
      call.respondRedirect(tab)
    }

    get("/$challengeRoot") {
      val tab = defaultTab(readingBatContent)
      call.respondRedirect(tab)
    }

    post("/$checkAnswers") {
      checkUserAnswers(readingBatContent, call.sessions.get<ClientSession>())
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

    get(ABOUT) { respondWith { aboutPage(readingBatContent) } }

    get(PREFS) { respondWith { prefsPage(readingBatContent) } }

    get(CREATE_ACCOUNT) { respondWith { createAccount(readingBatContent) } }

    post(CREATE_ACCOUNT) {
      val parameters = call.receiveParameters()
      val username = parameters[USERNAME]
      val password = parameters[PASSWORD]
      when {
        username.isNullOrEmpty() -> respondWith { createAccount(readingBatContent, "Empty email value") }
        !username.isValidEmail() -> respondWith { createAccount(readingBatContent, username, "Invalid email value") }
        password.isNullOrBlank() -> respondWith { createAccount(readingBatContent, username, "Empty password value") }
        password.length < 7 -> respondWith {
          createAccount(readingBatContent,
                        username,
                        "Password value too short (must have at least 6 characters)")
        }
        password == "password" -> respondWith {
          createAccount(readingBatContent,
                        username,
                        "Surely you can come up with a more clever password")
        }
        else -> {
          redisAction { redis ->
            // Check if username alread exists
            val userKey = userKey(username)
            if (redis.exists(userKey)) {
              runBlocking { respondWith { createAccount(readingBatContent, "Empty email value") } }
            }
            else {
              redis.set(userKey, password.sha256(username))
            }
          }
        }
      }
    }

    get(PRIVACY) { respondWith { privacy(readingBatContent) } }

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

internal fun userKey(username: String) = "user|$username"

private fun String.isValidEmail() = emailPattern.matcher(this).matches()

suspend fun PipelineCall.respondWith(block: () -> String) = call.respondText(block.invoke(), Html)

private object ThreadDumpInfo {
  internal val threadDump by lazy { ThreadDump(ManagementFactory.getThreadMXBean()) }
}

internal data class ClientSession(val name: String, val id: String) {
  fun challengeKey(languageName: String, groupName: String, challengeName: String) =
    listOf("challenge-answers", id, languageName, groupName, challengeName).joinToString("|")

  fun argumentKey(languageName: String, groupName: String, challengeName: String, argument: String) =
    listOf("answer-history", id, languageName, groupName, challengeName, argument).joinToString("|")
}

internal data class ChallengeAnswers(val id: String, val answers: MutableMap<String, String> = mutableMapOf())

// https://gist.github.com/lovubuntu/164b6b9021f5ba54cefc67f60f7a1a25
// load with sha256 for "test"
val hashedUserTable = UserHashedTableAuth(getDigestFunction("SHA-256") { "ktor${it.length}" },
                                          table = mapOf("test" to
                                                            Base64.getDecoder()
                                                              .decode("GSjkHCHGAxTTbnkEDBbVYd+PUFRlcWiumc4+MWE9Rvw="))
                                         )
