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
import com.github.readingbat.RedisPool.redisAction
import com.github.readingbat.misc.*
import com.github.readingbat.misc.AuthName.FORM
import com.github.readingbat.misc.AuthName.SESSION
import com.github.readingbat.misc.Constants.CHALLENGE_ROOT
import com.github.readingbat.misc.Constants.STATIC_ROOT
import com.github.readingbat.misc.Endpoints.CSS_NAME
import com.github.readingbat.misc.Endpoints.FAV_ICON
import com.google.common.util.concurrent.RateLimiter
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.UserPasswordCredential
import io.ktor.auth.form
import io.ktor.auth.session
import io.ktor.features.*
import io.ktor.http.ContentType.Text.Plain
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.withCharset
import io.ktor.locations.Locations
import io.ktor.request.path
import io.ktor.response.respond
import io.ktor.server.engine.ShutDownUrl
import io.ktor.sessions.Sessions
import io.ktor.sessions.cookie
import mu.KotlinLogging
import org.slf4j.event.Level
import kotlin.text.Charsets.UTF_8

private val logger = KotlinLogging.logger {}
private val hostname: String by lazy { System.getenv("HOST_NAME") ?: "www.readingbat.com" }
internal val production: Boolean by lazy { System.getenv("PRODUCTION")?.toBoolean() ?: false }

internal fun Application.installs() {

  install(Locations)

  install(Sessions) {
    configureSessionIdCookie()
    configureAuthCookie()
  }

  install(Authentication) {
    //configureSessionAuth()
    configureFormAuth()
  }


  if (production)
    install(HerokuHttpsRedirect) {
      host = hostname
      // host = "testingbat.herokuapp.com"
      permanentRedirect = false

      excludePrefix(STATIC_ROOT + "/")
      excludeSuffix(CSS_NAME)
      excludeSuffix(FAV_ICON)
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

private fun Sessions.Configuration.configureSessionIdCookie() {
  cookie<BrowserSession>("readingbat_session_id") {
    //storage = RedisSessionStorage(redis = pool.resource)) {
    //storage = directorySessionStorage(File("server-sessions"), cached = true)) {
    cookie.path = CHALLENGE_ROOT + "/"
  }
}

private fun Sessions.Configuration.configureAuthCookie() {
  cookie<UserPrincipal>(
    // We set a cookie by this name upon login.
    Cookies.AUTH_COOKIE
    //,
    // Stores session contents in memory...good for development only.
    //storage = SessionStorageMemory()
                       ) {
    cookie.path = CHALLENGE_ROOT + "/"
    cookie.httpOnly = true

    //if (production)
    //  cookie.secure = true

    cookie.maxAgeInSeconds = 7L * 24 * 3600 // 7 days

    // CSRF protection in modern browsers. Make sure your important side-effect-y operations, like ordering,
    // uploads, and changing settings, use "unsafe" HTTP verbs like POST and PUT, not GET or HEAD.
    // https://developer.mozilla.org/en-US/docs/Web/HTTP/Cookies#SameSite_cookies
    cookie.extensions["SameSite"] = "lax"
  }
}

private val failedLoginLimiter = RateLimiter.create(1.0) // rate 2.0 is "2 permits per second"

/**
 * Form-based authentication is a interceptor that reads attributes off a POST request in order to validate the user.
 * Only needed by whatever your login form is POSTing to.
 *
 * If validation fails, the user will be challenged, e.g. sent to a login page to authenticate.
 */
private fun Authentication.Configuration.configureFormAuth() {
  form(FORM) {
    userParamName = FormFields.USERNAME
    passwordParamName = FormFields.PASSWORD

    challenge {
      // I don't think form auth supports multiple errors, but we're conservatively assuming there will be at
      // most one error, which we handle here. Worst case, we just send the user to login with no context.

      // val errors: List<AuthenticationFailedCause> = call.authentication.allFailures
      // logger.info { "Inside challenge: $errors" }

      // In apps that require a valid login, you would redirect the user to a login page from here
      // However, we allow non-logged in users, so we do nothing here.
      /*
        when (errors.singleOrNull()) {
          AuthenticationFailedCause.InvalidCredentials -> call.respondRedirect("$LOGIN?invalid")
          AuthenticationFailedCause.NoCredentials -> call.respondRedirect("$LOGIN?no")
          else -> call.respondRedirect(LOGIN)
        }
      */
    }

    validate { cred: UserPasswordCredential ->
      var principal: UserPrincipal? = null

      redisAction { redis ->
        val userIdKey = userIdKey(cred.name)
        val id = redis.get(userIdKey) ?: ""
        if (id.isNotEmpty()) {
          val userId = UserId(id)
          val salt = redis.get(userId.saltKey()) ?: ""
          val digest = redis.get(userId.passwordKey()) ?: ""
          if (salt.isNotEmpty() && digest.isNotEmpty() && digest == cred.password.sha256(salt)) {
            logger.info { "Found user ${cred.name} $id $salt $digest" }
            principal = UserPrincipal(cred.name)
          }
        }
      }

      logger.info { "Login ${if (principal == null) "failure" else "success"}" }

      if (principal == null)
        failedLoginLimiter.acquire() // may wait

      principal
    }
  }
}

/**
 * Let the user authenticate by their session (a cookie).
 *
 * This is related to the configureAuthCookie method by virtue of the common `PrincipalType` object.
 */
private fun Authentication.Configuration.configureSessionAuth() {
  session<UserPrincipal>(SESSION) {
    challenge {
      // What to do if the user isn't authenticated
      // Uncomment this to send user to login page
      //call.respondRedirect("${CommonRoutes.LOGIN}?no")
    }
    validate { principal: UserPrincipal ->
      // If you need to do additional validation on session data, you can do so here.
      principal
    }
  }
}
