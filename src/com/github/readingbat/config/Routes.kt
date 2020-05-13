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
import com.github.readingbat.config.ThreadDumpInfo.threadDump
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.CSSNames.checkAnswers
import com.github.readingbat.misc.CheckAnswers.checkUserAnswers
import com.github.readingbat.misc.Constants.cssName
import com.github.readingbat.misc.Constants.icons
import com.github.readingbat.misc.Constants.root
import com.github.readingbat.misc.Constants.staticRoot
import com.github.readingbat.misc.cssContent
import com.github.readingbat.pages.defaultTab
import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.auth.*
import io.ktor.http.ContentType
import io.ktor.http.ContentType.Text.Html
import io.ktor.http.content.resources
import io.ktor.http.content.static
import io.ktor.response.respondRedirect
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.sessions.clear
import io.ktor.sessions.get
import io.ktor.sessions.sessions
import io.ktor.sessions.set
import io.ktor.util.getDigestFunction
import kotlinx.css.CSSBuilder
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import mu.KotlinLogging
import java.io.ByteArrayOutputStream
import java.lang.management.ManagementFactory
import java.util.*

private val logger = KotlinLogging.logger {}

internal data class ClientSession(val name: String, val id: String) {
  fun challengeKey(languageName: String, groupName: String, challengeName: String) =
    listOf(id, languageName, groupName, challengeName).joinToString("|")

  fun argumentKey(languageName: String, groupName: String, challengeName: String, argument: String) =
    listOf(id, languageName, groupName, challengeName, argument).joinToString("|")
}

internal data class ChallengeAnswers(val id: String, val answers: MutableMap<String, String> = mutableMapOf())

// load with sha256 for "test"
val hashedUserTable = UserHashedTableAuth(getDigestFunction("SHA-256") { "ktor${it.length}" },
                                          table = mapOf("test" to
                                                            Base64.getDecoder()
                                                              .decode("GSjkHCHGAxTTbnkEDBbVYd+PUFRlcWiumc4+MWE9Rvw="))
                                         )

internal fun Application.routes(readingBatContent: ReadingBatContent) {

  routing {
    route("/login") {
      authentication {
        form {
          validate { up: UserPasswordCredential ->
            when {
              up.password == "ppp" -> UserIdPrincipal(up.name)
              else -> null
            }
          }
        }
      }

      handle {
        val principal = call.authentication.principal<UserIdPrincipal>()
        if (principal != null) {
          call.respondText("Hello, ${principal.name}")
        }
        else {
          val html =
            createHTML()
              .html {
                body {
                  form(action = "/login",
                       encType = FormEncType.applicationXWwwFormUrlEncoded,
                       method = FormMethod.post) {
                    p { +"User:"; textInput(name = "user") { value = principal?.name ?: "" } }
                    p { +"Password:"; passwordInput(name = "pass") }
                    p { submitInput() { value = "Login" } }
                  }
                }
              }
          call.respondText(html, Html)
        }
      }
    }

    get("/") {
      call.respondRedirect(defaultTab(readingBatContent))
    }

    get("/$root") {
      call.respondRedirect(defaultTab(readingBatContent))
    }

    get("/favicon.ico") {
      call.respondRedirect("/$staticRoot/$icons/favicon.ico")
    }

    get("/$cssName") {
      call.respondCss {
        cssContent()
      }
    }

    post("/$checkAnswers") {
      val clientSession: ClientSession? = call.sessions.get<ClientSession>()
      checkUserAnswers(readingBatContent, clientSession)
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

    get("/ping") { call.respondText { "pong" } }

    get("/threaddump") {
      try {
        val baos = ByteArrayOutputStream()
        baos.use { threadDump.dump(true, true, it) }
        val output = String(baos.toByteArray(), Charsets.UTF_8)
        call.respondText { output }
      } catch (e: NoClassDefFoundError) {
        call.respondText { "Sorry, your runtime environment does not allow dump threads." }
      }
    }

    static("/$staticRoot") {
      resources(staticRoot)
    }

  }
}

private object ThreadDumpInfo {
  internal val threadDump by lazy { ThreadDump(ManagementFactory.getThreadMXBean()) }
}

private suspend inline fun ApplicationCall.respondCss(builder: CSSBuilder.() -> Unit) {
  respondText(CSSBuilder().apply(builder).toString(), ContentType.Text.CSS)
}