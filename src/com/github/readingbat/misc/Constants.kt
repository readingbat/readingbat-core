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

package com.github.readingbat.misc

import io.ktor.http.ContentType.Text.CSS
import java.util.concurrent.atomic.AtomicInteger

internal object Constants {
  const val sessionid = "sessionid"
  const val groupItemSrc = "groupItem"
  const val funcItem = "funcItem"
  const val funcChoice = "funcChoice"
  const val userResp = "userResp"
  const val solution = "solution"
  const val funcCol = "funcCol"
  const val arrow = "arrow"
  const val feedback = "feedback"
  const val userAnswers = "userAnswers"
  const val codeBlock = "codeBlock"
  const val checkBar = "checkBar"
  const val checkAnswers = "checkAnswers"
  const val spinner = "spinner"
  const val status = "status"
  const val refs = "refs"
  const val backLink = "backLink"
  const val langSrc = "lang"
  const val tabs = "tabs"
  const val selected = "selected"
  const val processAnswers = "processAnswers"
  const val playground = "playground"
  const val titleText = "ReadingBat"
  const val static = "static"
  const val checkJpg = "/$static/check.jpg"
  const val cssName = "styles.css"
  const val github = "github.com"
  const val githubUserContent = "raw.githubusercontent.com"
  const val kotlinCode = "kotlin-code"
  const val challengeDesc = "challenge-desc"
  const val bodyHeader = "bodyHeader"
  val cssType = CSS.toString()
  val sessionCounter = AtomicInteger(0)
  val production: Boolean by lazy { System.getenv("PRODUCTION")?.toBoolean() ?: false }
}