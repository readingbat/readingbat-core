package com.github.pambrose.readingbat

import com.github.pambrose.common.util.decode
import com.github.pambrose.common.util.isDoubleQuoted
import com.github.pambrose.common.util.isSingleQuoted
import com.github.pambrose.common.util.singleToDoubleQuoted
import com.github.pambrose.readingbat.LanguageType.Java
import com.github.pambrose.readingbat.LanguageType.Python
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.html.respondHtml
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.resources
import io.ktor.http.content.static
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
import kotlinx.html.*
import org.slf4j.event.Level
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.milliseconds

@Suppress("unused") // Referenced in application.conf
@JvmOverloads
fun Application.module(testing: Boolean = false, config: Configuration) {

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

  install(ShutDownUrl.ApplicationCallFeature) {
    // The URL that will be intercepted (you can also use the application.conf's ktor.deployment.shutdown.url key)
    shutDownUrl = "/ktor/application/shutdown"
    // A function that will be executed to get the exit code of the process
    exitCodeSupplier = { 0 } // ApplicationCall.() -> Int
  }

  val sessionid = "sessionid"
  val groupItem = "groupItem"
  val funcItem = "funcItem"
  val funcChoice = "funcChoice"
  val answer = "answer"
  val solution = "solution"
  val funcCol = "funcCol"
  val arrow = "arrow"
  val feedback = "feedback"
  val checkAnswers = "checkAnswers"
  val status = "status"
  val refs = "refs"
  val back = "back"
  val lang = "lang"
  val tabs = "tabs"
  val selected = "selected"
  val fs = 115.pct
  val processAnswers = "processAnswers"
  val title = "ReadingBat"
  val static = "static"
  val check = "/$static/check.jpg"
  val cssName = "/styles.css"
  val sessionCounter = AtomicInteger(0)
  val production: Boolean by lazy { System.getenv("PRODUCTION")?.toBoolean() ?: false }

  val analytics: HEAD.() -> Unit = {
    if (production) {
      unsafe {
        raw(
          """
        <script async src="https://www.googletagmanager.com/gtag/js?id=UA-164310007-1"></script>
        <script>
          window.dataLayer = window.dataLayer || [];
          function gtag(){dataLayer.push(arguments);}
          gtag('js', new Date());
          gtag('config', 'UA-164310007-1');
        </script>
      """.trimIndent()
        )
      }
    }
  }

  fun header(languageType: LanguageType): BODY.() -> Unit = {
    div(classes = "header") {
      a { href = "/"; span { style = "font-size:200%;"; +title } }
      unsafe { raw("&nbsp;") }
      span { +"code reading practice" }
    }
    nav {
      ul {
        li(classes = "h2") {
          if (languageType.isJava())
            id = selected
          a { href = "/java"; +"Java" }
        }
        li(classes = "h2") {
          if (languageType.isPython())
            id = selected
          a { href = "/python"; +"Python" }
        }
      }
    }
  }

  fun groupItem(prefix: String, name: String, description: String): TR.() -> Unit {
    return {
      td(classes = funcItem) {
        div(classes = groupItem) {
          a(classes = funcChoice) { href = "/$prefix/$name"; +name }
          br { if (description.isNotBlank()) +description else unsafe { raw("&nbsp;") } }
        }
      }
    }
  }

  fun Int.rows(cols: Int) = if (this % cols == 0) this / cols else (this / cols) + 1

  fun languageGroupPage(languageType: LanguageType): HTML.() -> Unit = {
    head {
      title(title)
      link { rel = "stylesheet"; href = cssName; type = "text/css" }
      analytics.invoke(this)
    }

    body {
      header(languageType).invoke(this)

      div(classes = tabs) {

        table {
          val groups = config.forLanguage(languageType).challengeGroups
          val cols = 3
          val size = groups.size
          val rows = size.rows(cols)
          val prefix = languageType.lowerName

          (0 until rows).forEach { i ->
            tr {
              groups[i].also { g -> groupItem(prefix, g.name, g.description).invoke(this) }

              if (i + rows < size)
                groups[i + rows].also { g -> groupItem(prefix, g.name, g.description).invoke(this) }
              else
                td {}

              if (i + (2 * rows) < size)
                groups[i + (2 * rows)].also { g -> groupItem(prefix, g.name, g.description).invoke(this) }
              else
                td {}
            }
          }
        }
      }
    }
  }

  fun challengeGroupPage(languageType: LanguageType, groupName: String): HTML.() -> Unit = {
    val prefix = languageType.lowerName

    head {
      title(title)
      link { rel = "stylesheet"; href = cssName; type = "text/css" }
      analytics.invoke(this)
    }

    body {
      header(languageType).invoke(this)

      div(classes = tabs) {
        h2 { +groupName.decode() }

        table {
          val challenges = config.forLanguage(languageType).find(groupName).challenges
          val cols = 3
          val size = challenges.size
          val rows = size.rows(cols)

          (0 until rows).forEach { i ->
            tr {
              challenges[i].also { g ->
                td(classes = funcItem) {
                  img { src = check }
                  unsafe { raw("&nbsp;") }
                  a(classes = funcChoice) { href = "/$prefix/$groupName/${g.name}"; +g.name }
                }
              }

              if (i + rows < size)
                challenges[i + rows].also { g ->
                  td(classes = funcItem) {
                    img { src = check }
                    unsafe { raw("&nbsp;") }
                    a(classes = funcChoice) { href = "/$prefix/$groupName/${g.name}"; +g.name }
                  }
                }
              else
                td {}

              if (i + (2 * rows) < size)
                challenges[i + (2 * rows)].also { g ->
                  td(classes = funcItem) {
                    img { src = check }
                    unsafe { raw("&nbsp;") }
                    a(classes = funcChoice) { href = "/$prefix/$groupName/${g.name}"; +g.name }
                  }
                }
              else
                td {}
            }
          }
        }

        div(classes = back) { a { href = "/$prefix"; unsafe { raw("&larr; Back") } } }
      }
    }
  }

  fun challengePage(languageType: LanguageType, groupName: String, challengeName: String): HTML.() -> Unit = {

    val challenge = config.forLanguage(languageType).find(groupName).challenges.first { it.name == challengeName }
    val funcType = challenge.languageType
    val name = challenge.name
    val funcName = challenge.funcName
    val funcArgs = challenge.inputOutput

    head {
      title(title)

      script(type = ScriptType.textJavaScript) {
        unsafe {
          raw(
            """
            var re = new XMLHttpRequest();

            function $processAnswers(cnt) { 
              var data = "$sessionid=${sessionCounter.incrementAndGet()}&$lang=${funcType.name.toLowerCase()}";
              try {
                for (var i = 0; i < cnt; i++) {
                  var x = document.getElementById("$feedback"+i);
                  x.style.backgroundColor = "white";
                  
                  var a = document.getElementById("$answer"+i).value;
                  data += "&$answer" + i + "="+encodeURIComponent(a);
                  var s = document.getElementById("$solution"+i).value;
                  console.log("Adding: " + s);
                  data += "&$solution" + i + "="+encodeURIComponent(s);
                }
              }
              catch(err) {
                console.log(err.message);
                return 0;
              }
              
              re.onreadystatechange = handleDone;  
              re.open("POST", '/$checkAnswers', true);
              re.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
              re.send(data);
              return 1;
            }
            
            function handleDone(){
              if(re.readyState == 1) {  // starting
                document.getElementById('$status').innerHTML = 'Checking answers...';
              }
              else if(re.readyState == 4) {  // done
                document.getElementById('$status').innerHTML = "";
                var results = eval(re.responseText);
                for (var i = 0; i < results.length; i++) {
                  var x = document.getElementById("$feedback"+i);
                  if (results[i]) 
                    x.style.backgroundColor = "green";
                  else 
                    x.style.backgroundColor = "red";
               }
              }
            }
            """
          )
        }
      }

      link { rel = "stylesheet"; href = "/$static/${languageType.lowerName}-prism.css"; type = "text/css" }
      link { rel = "stylesheet"; href = cssName; type = "text/css" }

      style {
        unsafe {
          raw(
            // This removes the prism shadow
            """
              pre[class*="language-"]:before,
              pre[class*="language-"]:after {
                display: none;
              }
            """
          )
        }
      }
      analytics.invoke(this)
    }

    body {
      header(languageType).invoke(this)

      div(classes = tabs) {
        h2 {
          a {
            href = "/${languageType.lowerName}/$groupName"; +groupName.decode()
          }; unsafe { raw("&rarr;") }; +name
        }

        pre(classes = "line-numbers") {
          code(classes = "language-${languageType.lowerName}") { +challenge.funcText() }
        }

        div {
          style = "margin-top: 2em;margin-left:2em"

          table {
            tr {
              th { +"Function Call" }
              th { +"" }
              th { +"Return Value" }
              th { +"" }
            }
            funcArgs.withIndex().forEach { (k, v) ->
              tr {
                td(classes = funcCol) { +"$funcName(${v.first})" }
                td(classes = arrow) { unsafe { raw("&rarr;") } }
                td { textInput(classes = answer) { id = "$answer$k" } }
                td(classes = feedback) { id = "$feedback$k" }
                td { hiddenInput { id = "$solution$k"; value = v.second } }
              }
            }
          }

          button(classes = checkAnswers) { onClick = "$processAnswers(${funcArgs.size});"; +"Check My Answers!" }
          span(classes = status) { id = status }

          p(classes = refs) {
            +"Experiment with this code on "
            a { href = "https://gitpod.io/#${challenge.gitpodUrl}"; target = "_blank"; +"Gitpod" }
          }
          if (challenge.codingBatEquiv.isNotEmpty()) {
            p(classes = refs) {
              +"Work on a similar problem on "
              a { href = "https://codingbat.com/prob/${challenge.codingBatEquiv}"; target = "_blank"; +"CodingBat" }
            }
          }

          div(classes = back) { a { href = "/${languageType.lowerName}/$groupName"; unsafe { raw("&larr; Back") } } }
        }
      }

      script { src = "/$static/${languageType.lowerName}-prism.js" }
    }
  }

  intercept(ApplicationCallPipeline.Call) {
    val req = call.request.uri
    val items = req.split("/").filter { it.isNotEmpty() }

    if (items.size > 1 && (items[0] in listOf(Java.lowerName, Python.lowerName))) {
      when (items.size) {
        2 -> call.respondHtml(block = challengeGroupPage(fromPath(items[0]), items[1]))
        3 -> call.respondHtml(block = challengePage(fromPath(items[0]), items[1], items[2]))
      }
    }
  }

  routing {

    get("/") {
      call.respondRedirect("/java")
    }

    get("/${Java.lowerName}") {
      call.respondHtml(block = languageGroupPage(Java))
    }

    get("/${Python.lowerName}") {
      call.respondHtml(block = languageGroupPage(Python))
    }

    get(cssName) {
      call.respondCss {
        body {
          backgroundColor = Color.white
        }
        p {
          fontSize = 2.em
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
        rule("div.$groupItem") {
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
        rule(".$checkAnswers") {
          width = 14.em
          height = 2.em
          backgroundColor = Color("#f1f1f1")
          fontSize = fs
          fontWeight = FontWeight.bold
          borderRadius = 6.px
          marginTop = 1.em
        }
        rule(".$status") {
          marginTop = 0.em
          marginLeft = 2.em
          fontSize = fs
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
          compareValues(compareMap[lang] == "java", userResp, sol)
        }

      delay(200.milliseconds.toLongMilliseconds())
      call.respondText(results.toString())
    }

    // Static feature. Try to access `/static/ktor_logo.svg`
    static("/$static") {
      resources(static)
    }

    install(StatusPages) {
      exception<AuthenticationException> { cause ->
        call.respond(HttpStatusCode.Unauthorized)
      }
      exception<AuthorizationException> { cause ->
        call.respond(HttpStatusCode.Forbidden)
      }
    }
  }
}

class AuthenticationException : RuntimeException()
class AuthorizationException : RuntimeException()

suspend inline fun ApplicationCall.respondCss(builder: CSSBuilder.() -> Unit) {
  this.respondText(CSSBuilder().apply(builder).toString(), ContentType.Text.CSS)
}

fun compareValues(isJava: Boolean, userResp: String?, solution: String?) =
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
