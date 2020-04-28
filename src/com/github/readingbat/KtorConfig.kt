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

package com.github.readingbat

import com.github.readingbat.Constants.checkAnswers
import com.github.readingbat.Constants.cssName
import com.github.readingbat.Constants.playground
import com.github.readingbat.Constants.static
import com.github.readingbat.dsl.LanguageType.*
import com.github.readingbat.dsl.LanguageType.Companion.toLanguageType
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.checkAnswers
import com.github.readingbat.misc.cssContent
import com.github.readingbat.pages.challengeGroupPage
import com.github.readingbat.pages.challengePage
import com.github.readingbat.pages.languageGroupPage
import com.github.readingbat.pages.playgroundPage
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.html.respondHtml
import io.ktor.http.ContentType
import io.ktor.http.ContentType.Text.Plain
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.content.resources
import io.ktor.http.content.static
import io.ktor.http.withCharset
import io.ktor.request.path
import io.ktor.request.uri
import io.ktor.response.respond
import io.ktor.response.respondRedirect
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.ShutDownUrl
import kotlinx.css.CSSBuilder
import org.slf4j.event.Level
import kotlin.text.Charsets.UTF_8

@JvmOverloads
fun Application.module(testing: Boolean = false, content: ReadingBatContent) {

  routing {

    get("/") {
      call.respondRedirect("/${Java.lowerName}")
    }

    get("/$cssName") {
      call.respondCss {
        cssContent()
      }
    }

    post("/$checkAnswers") {
      checkAnswers()
    }

    static("/$static") {
      resources(static)
    }
  }

  intercept(ApplicationCallPipeline.Call) {
    val req = call.request.uri
    val items = req.split("/").filter { it.isNotEmpty() }
    if (items.isNotEmpty()) {

      if (items.size == 3 && items[0] == playground) {
        val challenge = content.findLanguage(Kotlin).findChallenge(items[1], items[2])
        call.respondHtml { playgroundPage(challenge) }
      }

      if (items[0] in listOf(Java.lowerName, Python.lowerName, Kotlin.lowerName)) {
        val languageType = items[0].toLanguageType()
        val groupName = items.elementAtOrNull(1) ?: ""
        val challengeName = items.elementAtOrNull(2) ?: ""
        when (items.size) {
          1 -> {
            // This lookup has to take place outside of the lambda for proper exception handling
            val groups = content.findLanguage(languageType).challengeGroups
            call.respondHtml { languageGroupPage(languageType, groups) }
          }
          2 -> {
            val challengeGroup = content.findLanguage(languageType).findGroup(groupName)
            call.respondHtml { challengeGroupPage(challengeGroup) }
          }
          3 -> {
            val challenge = content.findLanguage(languageType).findChallenge(groupName, challengeName)
            call.respondHtml { challengePage(challenge) }
          }
          else -> throw InvalidPathException("Invalid path: $req")
        }
      }
    }
  }

  intercept(ApplicationCallPipeline.Monitoring) {
    // Set up metrics here
  }

  intercept(ApplicationCallPipeline.Fallback) {
    // Count not found pages here
  }

  install(Compression) {
    gzip {
      priority = 1.0
    }
    deflate {
      priority = 10.0
      minimumSize(1024) // condition
    }
  }

  install(CallLogging) {
    level = Level.INFO
    filter { call -> call.request.path().startsWith("/") }
  }

  install(StatusPages) {
    exception<InvalidPathException> { cause ->
      call.respond(HttpStatusCode.NotFound)
      //call.respondHtml { errorPage(cause.message?:"") }
    }

    //statusFile(HttpStatusCode.NotFound, HttpStatusCode.Unauthorized, filePattern = "error#.html")

    status(HttpStatusCode.NotFound) {
      call.respond(TextContent("${it.value} ${it.description}", Plain.withCharset(UTF_8), it))
    }

    // Catch all
    exception<Throwable> { cause ->
      call.respond(HttpStatusCode.InternalServerError)
    }
  }

  //if (!production) {
  install(ShutDownUrl.ApplicationCallFeature) {
    // The URL that will be intercepted (you can also use the application.conf's ktor.deployment.shutdown.url key)
    shutDownUrl = "/ktor/application/shutdown"
    // A function that will be executed to get the exit code of the process
    exitCodeSupplier = { 0 } // ApplicationCall.() -> Int
  }
  //}
}

suspend inline fun ApplicationCall.respondCss(builder: CSSBuilder.() -> Unit) {
  this.respondText(CSSBuilder().apply(builder).toString(), ContentType.Text.CSS)
}

class InvalidPathException(msg: String) : RuntimeException(msg)