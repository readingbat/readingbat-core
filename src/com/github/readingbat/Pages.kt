package com.github.readingbat

import com.github.pambrose.common.util.decode
import com.github.readingbat.Constants.answer
import com.github.readingbat.Constants.arrow
import com.github.readingbat.Constants.back
import com.github.readingbat.Constants.checkAnswers
import com.github.readingbat.Constants.checkBar
import com.github.readingbat.Constants.checkJpg
import com.github.readingbat.Constants.cssName
import com.github.readingbat.Constants.cssType
import com.github.readingbat.Constants.feedback
import com.github.readingbat.Constants.funcChoice
import com.github.readingbat.Constants.funcCol
import com.github.readingbat.Constants.funcItem
import com.github.readingbat.Constants.groupItemSrc
import com.github.readingbat.Constants.processAnswers
import com.github.readingbat.Constants.production
import com.github.readingbat.Constants.refs
import com.github.readingbat.Constants.selected
import com.github.readingbat.Constants.solution
import com.github.readingbat.Constants.sp
import com.github.readingbat.Constants.spinner
import com.github.readingbat.Constants.static
import com.github.readingbat.Constants.status
import com.github.readingbat.Constants.tabs
import com.github.readingbat.Constants.titleText
import io.ktor.application.ApplicationCall
import io.ktor.http.ContentType
import io.ktor.response.respondText
import kotlinx.css.CSSBuilder
import kotlinx.html.*

fun HEAD.headDefault() {
  link { rel = "stylesheet"; href = cssName; type = cssType }
  title(titleText)

  if (production)
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

fun BODY.bodyHeader(languageType: LanguageType) {
  div(classes = "header") {
    a { href = "/"; span { style = "font-size:200%;"; +titleText } }
    rawHtml(sp)
    span { +"code reading practice" }
  }
  nav {
    ul {
      li(classes = "h2") {
        if (languageType.isJava()) id = selected
        a { href = "/${LanguageType.Java.lowerName}"; +LanguageType.Java.name }
      }
      li(classes = "h2") {
        if (languageType.isPython()) id = selected
        a { href = "/${LanguageType.Python.lowerName}"; +LanguageType.Python.name }
      }
    }
  }
}

fun TR.groupItem(prefix: String, group: ChallengeGroup) {
  val name = group.name
  val description = group.description
  val parsedDescription = group.parsedDescription

  td(classes = funcItem) {
    div(classes = groupItemSrc) {
      a(classes = funcChoice) { href = "/$prefix/$name"; +name }
      br { rawHtml(if (description.isNotBlank()) parsedDescription else sp) }
    }
  }
}

fun TR.funcCall(prefix: String, groupName: String, challenge: AbstractChallenge) {
  td(classes = funcItem) {
    img { src = checkJpg }
    rawHtml(sp)
    a(classes = funcChoice) { href = "/$prefix/$groupName/${challenge.name}"; +challenge.name }
  }
}

fun HTML.languageGroupPage(languageType: LanguageType, groups: List<ChallengeGroup>) {
  head {
    headDefault()
  }

  body {
    bodyHeader(languageType)
    div(classes = tabs) {
      table {
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

fun HTML.challengeGroupPage(challengeGroup: ChallengeGroup) {

  val languageType = challengeGroup.languageType
  val groupName = challengeGroup.name
  val challenges = challengeGroup.challenges
  val prefix = languageType.lowerName

  head {
    headDefault()
  }

  body {
    bodyHeader(languageType)

    div(classes = tabs) {
      h2 { +groupName.decode() }

      table {
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

fun HTML.challengePage(challenge: AbstractChallenge) {
  val languageType = challenge.languageType
  val groupName = challenge.groupName
  val name = challenge.name
  val funcArgs = challenge.inputOutput
  val languageName = languageType.lowerName

  head {
    link {
      rel = "stylesheet"; href = "https://cdnjs.cloudflare.com/ajax/libs/font-awesome/4.7.0/css/font-awesome.min.css"
    }
    link { rel = "stylesheet"; href = "/$static/$languageName-prism.css"; type = cssType }

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
      getScript(languageName)
    }

    headDefault()
  }

  body {
    bodyHeader(languageType)

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

fun HTMLTag.rawHtml(html: String) {
  unsafe { raw(html) }
}

suspend inline fun ApplicationCall.respondCss(builder: CSSBuilder.() -> Unit) {
  this.respondText(CSSBuilder().apply(builder).toString(), ContentType.Text.CSS)
}

fun Int.rows(cols: Int) = if (this % cols == 0) this / cols else (this / cols) + 1