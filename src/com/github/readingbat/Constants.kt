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
  val github = "github.com"
  val githubUserContent = "raw.githubusercontent.com"
  val sessionCounter = AtomicInteger(0)
  val production: Boolean by lazy { System.getenv("PRODUCTION")?.toBoolean() ?: false }
}