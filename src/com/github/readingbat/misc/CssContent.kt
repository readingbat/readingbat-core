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

import com.github.readingbat.Constants.answer
import com.github.readingbat.Constants.arrow
import com.github.readingbat.Constants.back
import com.github.readingbat.Constants.bodyHeader
import com.github.readingbat.Constants.challengeDesc
import com.github.readingbat.Constants.checkAnswers
import com.github.readingbat.Constants.checkBar
import com.github.readingbat.Constants.codeBlock
import com.github.readingbat.Constants.feedback
import com.github.readingbat.Constants.funcChoice
import com.github.readingbat.Constants.funcCol
import com.github.readingbat.Constants.funcItem
import com.github.readingbat.Constants.groupItemSrc
import com.github.readingbat.Constants.kotlinCode
import com.github.readingbat.Constants.refs
import com.github.readingbat.Constants.selected
import com.github.readingbat.Constants.spinner
import com.github.readingbat.Constants.status
import com.github.readingbat.Constants.tabs
import com.github.readingbat.Constants.userInput
import kotlinx.css.*
import kotlinx.css.properties.TextDecoration

private val fs = 115.pct
private val codeFs = 100.pct

fun CSSBuilder.cssContent() {
  body {
    backgroundColor = Color.white
  }
  rule(".$challengeDesc") {
    fontSize = fs
    marginLeft = 1.em
    marginBottom = 1.em
  }
  rule(".$bodyHeader") {
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
  rule(".$userInput") {
    marginTop = 2.em
    marginLeft = 2.em
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
  rule(".$refs") {
    marginTop = 1.em
    fontSize = fs
  }
  rule(".$back") {
    marginTop = 1.em
    fontSize = fs
  }

  rule(".$codeBlock") {
    marginTop = 2.em
    marginLeft = 1.em
    marginRight = 1.em
    fontSize = codeFs
  }
  rule(".language-java") {
    //width = 950.px  // !important
  }
  rule(".language-python") {
    //width = 950.px  // !important
  }
  rule(".language-kotlin") {
    //width = 950.px  // !important
  }
  rule(".$kotlinCode") {
    marginLeft = 1.em
    marginRight = 1.em
  }
  // KotlinPlayground code
  rule(".CodeMirror") {
    //height = 500.px
    fontSize = codeFs
  }
}