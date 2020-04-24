package com.github.readingbat

import com.github.pambrose.common.util.isDoubleQuoted
import com.github.pambrose.common.util.isSingleQuoted
import com.github.pambrose.common.util.singleToDoubleQuoted
import com.github.readingbat.Constants.answer
import com.github.readingbat.Constants.arrow
import com.github.readingbat.Constants.back
import com.github.readingbat.Constants.checkAnswers
import com.github.readingbat.Constants.checkBar
import com.github.readingbat.Constants.cssName
import com.github.readingbat.Constants.feedback
import com.github.readingbat.Constants.fs
import com.github.readingbat.Constants.funcChoice
import com.github.readingbat.Constants.funcCol
import com.github.readingbat.Constants.funcItem
import com.github.readingbat.Constants.groupItemSrc
import com.github.readingbat.Constants.langSrc
import com.github.readingbat.Constants.refs
import com.github.readingbat.Constants.selected
import com.github.readingbat.Constants.solution
import com.github.readingbat.Constants.spinner
import com.github.readingbat.Constants.static
import com.github.readingbat.Constants.status
import com.github.readingbat.Constants.tabs
import com.github.readingbat.LanguageType.Companion.toLanguageType
import com.github.readingbat.LanguageType.Java
import com.github.readingbat.LanguageType.Python
import io.ktor.application.Application
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.*
import io.ktor.html.respondHtml
import io.ktor.http.ContentType.Text.Plain
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.content.resources
import io.ktor.http.content.static
import io.ktor.http.withCharset
import io.ktor.request.path
import io.ktor.request.receiveParameters
import io.ktor.request.uri
import io.ktor.response.respond
import io.ktor.response.respondRedirect
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.ShutDownUrl
import kotlinx.coroutines.delay
import kotlinx.css.*
import kotlinx.css.properties.TextDecoration
import org.slf4j.event.Level
import kotlin.text.Charsets.UTF_8
import kotlin.time.milliseconds

@JvmOverloads
fun Application.module(testing: Boolean = false, content: Content) {

  routing {

    get("/") {
      call.respondRedirect("/${Java.lowerName}")
    }

    get(cssName) {
      call.respondCss {
        body {
          backgroundColor = Color.white
        }
        rule(".challenge-desc") {
          fontSize = fs
          marginLeft = 1.em
          marginBottom = 1.em
        }
        rule(".header") {
          marginBottom = 2.em
        }
        rule(".$funcItem") {
          marginTop = 1.em
        }
        rule(".$funcChoice") {
          fontSize = 140.pct
        }
        rule("th, td") {
          padding = "5px"
          textAlign = TextAlign.left
        }
        rule("th") {
          fontSize = fs
        }
        rule("div.$groupItemSrc") {
          maxWidth = 300.px
          minWidth = 300.px
          margin = "15px"
          padding = "10px"
          border = "1px solid gray"
          borderRadius = LinearDimension("1em")
        }
        rule("td.$funcCol") {
          fontSize = fs
        }
        rule("td.$arrow") {
          width = 2.em
          fontSize = fs
          textAlign = TextAlign.center
        }
        rule(".$answer") {
          width = 15.em
          fontSize = 90.pct
        }
        rule("td.$feedback") {
          width = 10.em
          border = "7px solid white"
        }
        // This will add an outline to all the tables
        /*
        rule("table th td") {
          border = "1px solid black;"
          borderCollapse = BorderCollapse.collapse
        }
        */
        rule(".$checkBar") {
          marginTop = 1.em
        }
        rule(".$checkAnswers") {
          width = 14.em
          height = 2.em
          backgroundColor = Color("#f1f1f1")
          fontSize = fs
          fontWeight = FontWeight.bold
          borderRadius = 6.px
        }
        rule(".$spinner") {
          marginLeft = 1.em
          verticalAlign = VerticalAlign.bottom
        }
        rule(".$status") {
          marginLeft = 5.px
          fontSize = fs
          verticalAlign = VerticalAlign.bottom
        }
        rule(".h2") {
          fontSize = 166.pct
          textDecoration = TextDecoration.none
        }
        rule("a:link") {
          textDecoration = TextDecoration.none
        }
        rule("div.$tabs") {
          borderTop = "1px solid"
          clear = Clear.both
        }
        rule("#$selected") {
          position = Position.relative
          top = LinearDimension("1px")
          background = "white"
        }
        rule("nav ul") {
          listStyleType = ListStyleType.none
          padding = "0"
          margin = "0"
        }
        rule("nav li") {
          display = Display.inline
          border = "solid"
          borderWidth = LinearDimension("1px 1px 0 1px")
          margin = "0 25px 0 6px"
        }
        rule("nav li a") {
          padding = "0 40px"
        }
        rule(".language-java") {
          width = 950.px  // !important
        }
        rule(".language-python") {
          width = 950.px  // !important
        }
        rule(".$refs") {
          marginTop = 1.em
          fontSize = fs
        }
        rule(".$back") {
          marginTop = 2.em
          fontSize = fs
        }
      }
    }

    post("/$checkAnswers") {
      val params = call.receiveParameters()
      val compareMap = params.entries().map { it.key to it.value[0] }.toMap()
      val answers = params.entries().filter { it.key.startsWith(answer) }
      val results =
        answers.indices.map { i ->
          val userResp = compareMap[answer + i]?.trim()
          val sol = compareMap[solution + i]?.trim()

          fun checkWithSolution(isJava: Boolean, userResp: String?, solution: String?) =
            try {
              fun String.isJavaBoolean() = this == "true" || this == "false"
              fun String.isPythonBoolean() = this == "True" || this == "False"

              if (isJava)
                when {
                  userResp.isNullOrEmpty() || solution.isNullOrEmpty() -> false
                  userResp.isDoubleQuoted() || solution.isDoubleQuoted() -> userResp == solution
                  userResp.contains(".") || solution.contains(".") -> userResp.toDouble() == solution.toDouble()
                  userResp.isJavaBoolean() && solution.isJavaBoolean() -> userResp.toBoolean() == solution.toBoolean()
                  else -> userResp.toInt() == solution.toInt()
                }
              else
                when {
                  userResp.isNullOrEmpty() || solution.isNullOrEmpty() -> false
                  userResp.isDoubleQuoted() -> userResp == solution
                  userResp.isSingleQuoted() -> userResp.singleToDoubleQuoted() == solution
                  userResp.contains(".") || solution.contains(".") -> userResp.toDouble() == solution.toDouble()
                  userResp.isPythonBoolean() && solution.isJavaBoolean() -> userResp.toBoolean() == solution.toBoolean()
                  else -> userResp.toInt() == solution.toInt()
                }
            } catch (e: Exception) {
              false
            }

          checkWithSolution(compareMap[langSrc] == "java", userResp, sol)
        }

      delay(200.milliseconds.toLongMilliseconds())
      call.respondText(results.toString())
    }

    static("/$static") {
      resources(static)
    }
  }

  intercept(ApplicationCallPipeline.Call) {
    val req = call.request.uri
    val items = req.split("/").filter { it.isNotEmpty() }

    if (items.isNotEmpty() && (items[0] in listOf(Java.lowerName, Python.lowerName))) {
      val languageType = items[0].toLanguageType()
      val groupName = items.elementAtOrNull(1) ?: ""
      val challengeName = items.elementAtOrNull(2) ?: ""
      when (items.size) {
        1 -> {
          // This lookup has to take place outside of the lambda for proper exception handling
          val groups = content.getLanguage(languageType).challengeGroups
          call.respondHtml { languageGroupPage(Java, groups) }
        }
        2 -> {
          val challengeGroup = content.getLanguage(languageType).findChallengeGroup(groupName)
          call.respondHtml { challengeGroupPage(challengeGroup) }
        }
        3 -> {
          val challenge = content.getLanguage(languageType).findChallenge(groupName, challengeName)
          call.respondHtml { challengePage(challenge) }
        }
        else -> throw InvalidPathException("Invalid path: $req")
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

  install(ShutDownUrl.ApplicationCallFeature) {
    // The URL that will be intercepted (you can also use the application.conf's ktor.deployment.shutdown.url key)
    shutDownUrl = "/ktor/application/shutdown"
    // A function that will be executed to get the exit code of the process
    exitCodeSupplier = { 0 } // ApplicationCall.() -> Int
  }
}

class InvalidPathException(msg: String) : RuntimeException(msg)