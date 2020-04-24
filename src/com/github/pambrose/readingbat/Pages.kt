package com.github.pambrose.readingbat

import com.github.pambrose.common.util.*
import com.github.pambrose.readingbat.LanguageType.Companion.toLanguageType
import com.github.pambrose.readingbat.LanguageType.Java
import com.github.pambrose.readingbat.LanguageType.Python
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.html.respondHtml
import io.ktor.http.ContentType
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
import kotlinx.html.*
import org.slf4j.event.Level
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.milliseconds


@JvmOverloads
fun Application.module(testing: Boolean = false, content: Content) {

  val sessionid = "sessionid"
  val groupItem = "groupItem"
  val funcItem = "funcItem"
  val funcChoice = "funcChoice"
  val answer = "answer"
  val solution = "solution"
  val funcCol = "funcCol"
  val arrow = "arrow"
  val feedback = "feedback"
  val checkBar = "checkBar"
  val checkAnswers = "checkAnswers"
  val spinner = "spinner"
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
  val cssType = ContentType.Text.CSS.toString()
  val sp = "&nbsp;"
  val sessionCounter = AtomicInteger(0)
  val production: Boolean by lazy { System.getenv("PRODUCTION")?.toBoolean() ?: false }

  fun HTMLTag.rawHtml(html: String) {
    unsafe { raw(html) }
  }

  fun HEAD.analytics() {
    if (production) {
      rawHtml(
        """
        <script async src="https://www.googletagmanager.com/gtag/js?id=UA-164310007-1"></script>
        <script>
          window.dataLayer = window.dataLayer || [];
          function gtag(){dataLayer.push(arguments);}
          gtag('js', new Date());
          gtag('config', 'UA-164310007-1');
        </script>
        """
      )
    }
  }

  fun BODY.header(languageType: LanguageType) {
    div(classes = "header") {
      a { href = "/"; span { style = "font-size:200%;"; +title } }
      rawHtml(sp)
      span { +"code reading practice" }
    }
    nav {
      ul {
        li(classes = "h2") {
          if (languageType.isJava()) id = selected
          a { href = "/${Java.lowerName}"; +Java.name }
        }
        li(classes = "h2") {
          if (languageType.isPython()) id = selected
          a { href = "/${Python.lowerName}"; +Python.name }
        }
      }
    }
  }

  fun TR.groupItem(prefix: String, group: ChallengeGroup) {
    val name = group.name
    val description = group.description
    val parsedDescription = group.parsedDescription

    td(classes = funcItem) {
      div(classes = groupItem) {
        a(classes = funcChoice) { href = "/$prefix/$name"; +name }
        br { rawHtml(if (description.isNotBlank()) parsedDescription else sp) }
      }
    }
  }

  fun TR.funcCall(prefix: String, groupName: String, challenge: AbstractChallenge) {
    td(classes = funcItem) {
      img { src = check }
      rawHtml(sp)
      a(classes = funcChoice) { href = "/$prefix/$groupName/${challenge.name}"; +challenge.name }
    }
  }

  fun Int.rows(cols: Int) = if (this % cols == 0) this / cols else (this / cols) + 1

  fun HTML.languageGroupPage(languageType: LanguageType) {
    head {
      title(title)
      link { rel = "stylesheet"; href = cssName; type = cssType }
      analytics()
    }

    body {
      header(languageType)

      div(classes = tabs) {

        table {
          val groups = content.getLanguage(languageType).challengeGroups
          val cols = 3
          val size = groups.size
          val rows = size.rows(cols)
          val languageName = languageType.lowerName

          (0 until rows).forEach { i ->
            tr {
              groups[i].also { group -> groupItem(languageName, group) }
              groups.elementAtOrNull(i + rows)?.also { groupItem(languageName, it) } ?: td {}
              groups.elementAtOrNull(i + (2 * rows))?.also { groupItem(languageName, it) } ?: td {}
            }
          }
        }
      }
    }
  }

  fun HTML.challengeGroupPage(languageType: LanguageType, groupName: String) {

    val prefix = languageType.lowerName

    head {
      title(title)
      link { rel = "stylesheet"; href = cssName; type = cssType }
      analytics()
    }

    body {
      header(languageType)

      div(classes = tabs) {
        h2 { +groupName.decode() }

        table {
          val challenges = content.getLanguage(languageType).find(groupName).challenges
          val cols = 3
          val size = challenges.size
          val rows = size.rows(cols)

          (0 until rows).forEach { i ->
            tr {
              challenges[i].also { funcCall(prefix, groupName, it) }
              challenges.elementAtOrNull(i + rows)?.also { funcCall(prefix, groupName, it) } ?: td {}
              challenges.elementAtOrNull(i + (2 * rows))?.also { funcCall(prefix, groupName, it) } ?: td {}
            }
          }
        }

        div(classes = back) { a { href = "/$prefix"; rawHtml("&larr; Back") } }
      }
    }
  }

  fun HTML.challengePage(languageType: LanguageType, groupName: String, challengeName: String) {
    val challenge =
      content.getLanguage(languageType)
        .find(groupName)
        .challenges
        .firstOrNull { it.name == challengeName }
        ?: throw InvalidPathException("Challenge ${challengeName.toDoubleQuoted()} not found.")
    val langName = challenge.languageType.lowerName
    val name = challenge.name
    val funcArgs = challenge.inputOutput
    val languageName = languageType.lowerName

    head {
      title(title)
      link {
        rel = "stylesheet"; href = "https://cdnjs.cloudflare.com/ajax/libs/font-awesome/4.7.0/css/font-awesome.min.css"
      }
      link { rel = "stylesheet"; href = "/$static/$languageName-prism.css"; type = cssType }
      link { rel = "stylesheet"; href = cssName; type = cssType }

      // Remove the prism shadow
      style {
        rawHtml(
          """
            pre[class*="language-"]:before,
            pre[class*="language-"]:after { display: none; }
          """
        )
      }

      script(type = ScriptType.textJavaScript) {
        rawHtml(
          """
            var re = new XMLHttpRequest();

            function $processAnswers(cnt) { 
              var data = "$sessionid=${sessionCounter.incrementAndGet()}&$lang=$langName";
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
                document.getElementById('$spinner').innerHTML = '<i class="fa fa-spinner fa-spin" style="font-size:24px"></i>';
                document.getElementById('$status').innerHTML = 'Checking answers...';
              }
              else if(re.readyState == 4) {  // done
                document.getElementById('$spinner').innerHTML = "";
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

      analytics()
    }

    body {
      header(languageType)

      div(classes = tabs) {
        h2 {
          a { href = "/$languageName/$groupName"; +groupName.decode() }; rawHtml("$sp&rarr;$sp"); +name
        }

        if (challenge.description.isNotEmpty())
          div(classes = "challenge-desc") { rawHtml(challenge.parsedDescription) }

        pre(classes = "line-numbers") {
          code(classes = "language-$languageName") { +challenge.funcInfo().code }
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
            funcArgs.withIndex().forEach { (i, v) ->
              tr {
                td(classes = funcCol) { +"${challenge.funcInfo().name}(${v.first})" }
                td(classes = arrow) { rawHtml("&rarr;") }
                td {
                  textInput(classes = answer) { id = "$answer$i"; onKeyPress = "$processAnswers(${funcArgs.size})" }
                }
                td(classes = feedback) { id = "$feedback$i" }
                td { hiddenInput { id = "$solution$i"; value = v.second } }
              }
            }
          }

          div(classes = checkBar) {
            table {
              tr {
                td {
                  button(classes = checkAnswers) { onClick = "$processAnswers(${funcArgs.size})"; +"Check My Answers!" }
                }
                td { span(classes = spinner) { id = spinner } }
                td { span(classes = status) { id = status } }
              }
            }
          }
          p(classes = refs) {
            +"Experiment with this code on "
            a { href = "https://gitpod.io/#${challenge.gitpodUrl}"; target = "_blank"; +"Gitpod.io" }
          }
          if (challenge.codingBatEquiv.isNotEmpty()) {
            p(classes = refs) {
              +"Work on a similar problem on "
              a { href = "https://codingbat.com/prob/${challenge.codingBatEquiv}"; target = "_blank"; +"CodingBat.com" }
            }
          }

          div(classes = back) { a { href = "/$languageName/$groupName"; rawHtml("&larr; Back") } }
        }
      }

      script { src = "/$static/$languageName-prism.js" }
    }
  }

  routing {

    get("/") {
      call.respondRedirect("/${Java.lowerName}")
    }

    get("/${Java.lowerName}") {
      call.respondHtml { languageGroupPage(Java) }
    }

    get("/${Python.lowerName}") {
      call.respondHtml { languageGroupPage(Python) }
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

          checkWithSolution(compareMap[lang] == "java", userResp, sol)
        }

      delay(200.milliseconds.toLongMilliseconds())
      call.respondText(results.toString())
    }

    // Static feature. Try to access `/static/ktor_logo.svg`
    static("/$static") {
      resources(static)
    }
  }

  intercept(ApplicationCallPipeline.Call) {
    val req = call.request.uri
    val items = req.split("/").filter { it.isNotEmpty() }

    if (items.size > 1 && (items[0] in listOf(Java.lowerName, Python.lowerName))) {
      when (items.size) {
        2 -> {

          call.respondHtml { challengeGroupPage(items[0].toLanguageType(), items[1]) }
        }
        3 -> {
          call.respondHtml { challengePage(items[0].toLanguageType(), items[1], items[2]) }
        }
        else -> throw InvalidPathException("Invalid path: $req")
      }
    }
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
      println("I am here with: ${cause.message}")
      call.respond(HttpStatusCode.NotFound)
    }

    //statusFile(HttpStatusCode.NotFound, HttpStatusCode.Unauthorized, filePattern = "error#.html")

    status(HttpStatusCode.NotFound) {
      println("I am in NotFound")
      call.respond(TextContent("${it.value} ${it.description}", ContentType.Text.Plain.withCharset(Charsets.UTF_8), it))
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
class AuthenticationException : RuntimeException()
class AuthorizationException : RuntimeException()

suspend inline fun ApplicationCall.respondCss(builder: CSSBuilder.() -> Unit) {
  this.respondText(CSSBuilder().apply(builder).toString(), ContentType.Text.CSS)
}