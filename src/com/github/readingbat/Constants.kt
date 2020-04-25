package com.github.readingbat

import io.ktor.http.ContentType
import kotlinx.css.pct
import java.util.concurrent.atomic.AtomicInteger

object Constants {
  val sessionid = "sessionid"
  val groupItemSrc = "groupItem"
  val funcItem = "funcItem"
  val funcChoice = "funcChoice"
  val answer = "answer"
  val solution = "solution"
  val funcCol = "funcCol"
  val arrow = "arrow"
  val feedback = "feedback"
  val userInput = "userInput"
  val checkBar = "checkBar"
  val checkAnswers = "checkAnswers"
  val spinner = "spinner"
  val status = "status"
  val refs = "refs"
  val back = "back"
  val langSrc = "lang"
  val tabs = "tabs"
  val selected = "selected"
  val fs = 115.pct
  val processAnswers = "processAnswers"
  val titleText = "ReadingBat"
  val static = "static"
  val checkJpg = "/$static/check.jpg"
  val cssName = "styles.css"
  val cssType = ContentType.Text.CSS.toString()
  val sp = "&nbsp;"
  val sessionCounter = AtomicInteger(0)
  val production: Boolean by lazy { System.getenv("PRODUCTION")?.toBoolean() ?: false }
}