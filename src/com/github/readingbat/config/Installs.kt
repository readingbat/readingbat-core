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

import com.github.pambrose.common.util.simpleClassName
import com.github.readingbat.InvalidPathException
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.http.ContentType.Text.Plain
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.withCharset
import io.ktor.locations.Locations
import io.ktor.request.path
import io.ktor.response.respond
import io.ktor.response.respondRedirect
import io.ktor.server.engine.ShutDownUrl
import io.ktor.sessions.Sessions
import io.ktor.sessions.cookie
import mu.KotlinLogging
import org.slf4j.event.Level
import kotlin.text.Charsets.UTF_8

private val logger = KotlinLogging.logger {}
internal val production: Boolean by lazy { System.getenv("PRODUCTION")?.toBoolean() ?: false }

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

object TestCredentials {
  const val USERNAME = "foo"
  const val PASSWORD = "bar"
}

private fun Authentication.Configuration.configureFormAuth() {
  form(AuthName.FORM) {
    userParamName = FormFields.USERNAME
    passwordParamName = FormFields.PASSWORD
    challenge {
      // I don't think form auth supports multiple errors, but we're conservatively assuming there will be at
      // most one error, which we handle here. Worst case, we just send the user to login with no context.
      val errors: Map<Any, AuthenticationFailedCause> = call.authentication.errors
      when (errors.values.singleOrNull()) {
        AuthenticationFailedCause.InvalidCredentials ->
          call.respondRedirect("${CommonRoutes.LOGIN}?invalid")

        AuthenticationFailedCause.NoCredentials ->
          call.respondRedirect("${CommonRoutes.LOGIN}?no")

        else ->
          call.respondRedirect(CommonRoutes.LOGIN)
      }
    }
    validate { cred: UserPasswordCredential ->
      // Realistically you'd look up the user in a database or something here; this is just a toy example.
      // The values here will be whatever was submitted in the form.
      if (cred.name == TestCredentials.USERNAME && cred.password == TestCredentials.PASSWORD)
        UserIdPrincipal(cred.name)
      else
        null
    }
  }
}


internal fun Application.installs() {

  install(Compression) {
    gzip {
      priority = 1.0
    }
    deflate {
      priority = 10.0
      minimumSize(1024) // condition
    }
  }

  install(Locations)

  install(Sessions) {
    cookie<ClientSession>("readingbat_session_id") {
      //storage = RedisSessionStorage(redis = pool.resource)) {
      //storage = directorySessionStorage(File("server-sessions"), cached = true)) {
      cookie.path = "/"
    }
  }

  install(Authentication) {
    configureFormAuth()
  }


  install(CallLogging) {
    level = Level.INFO
    filter { call -> call.request.path().startsWith("/") }
  }

  install(DefaultHeaders) {
    header("X-Engine", "Ktor")
  }

  /*
  install(DropwizardMetrics) {
    val reporter =
      Slf4jReporter.forRegistry(registry)
        .outputTo(log)
        .convertRatesTo(TimeUnit.SECONDS)
        .convertDurationsTo(TimeUnit.MILLISECONDS)
        .build();
    //reporter.start(1, TimeUnit.HOURS);
  }
  */

  install(StatusPages) {
    exception<InvalidPathException> { cause ->
      call.respond(HttpStatusCode.NotFound)
      //call.respondHtml { errorPage(cause.message?:"") }
    }

    //statusFile(HttpStatusCode.NotFound, HttpStatusCode.Unauthorized, filePattern = "error#.html")

    // Catch all
    exception<Throwable> { cause ->
      logger.info(cause) { " Throwable caught: ${cause.simpleClassName}" }
      call.respond(HttpStatusCode.NotFound)
    }

    status(HttpStatusCode.NotFound) {
      call.respond(TextContent("${it.value} ${it.description}", Plain.withCharset(UTF_8), it))
    }
  }

  if (!production) {
    install(ShutDownUrl.ApplicationCallFeature) {
      // The URL that will be intercepted (you can also use the application.conf's ktor.deployment.shutdown.url key)
      shutDownUrl = "/ktor/application/shutdown"
      // A function that will be executed to get the exit code of the process
      exitCodeSupplier = { 0 } // ApplicationCall.() -> Int
    }
  }
}