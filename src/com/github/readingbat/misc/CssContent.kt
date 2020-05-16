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

import com.github.readingbat.misc.CSSNames.arrow
import com.github.readingbat.misc.CSSNames.backLinkCls
import com.github.readingbat.misc.CSSNames.bodyHeaderCls
import com.github.readingbat.misc.CSSNames.challengeDesc
import com.github.readingbat.misc.CSSNames.checkAnswers
import com.github.readingbat.misc.CSSNames.checkBar
import com.github.readingbat.misc.CSSNames.codeBlock
import com.github.readingbat.misc.CSSNames.codeFs
import com.github.readingbat.misc.CSSNames.feedback
import com.github.readingbat.misc.CSSNames.fs
import com.github.readingbat.misc.CSSNames.funcChoice
import com.github.readingbat.misc.CSSNames.funcCol
import com.github.readingbat.misc.CSSNames.funcItem
import com.github.readingbat.misc.CSSNames.groupItemSrc
import com.github.readingbat.misc.CSSNames.kotlinCode
import com.github.readingbat.misc.CSSNames.refs
import com.github.readingbat.misc.CSSNames.selected
import com.github.readingbat.misc.CSSNames.spinner
import com.github.readingbat.misc.CSSNames.status
import com.github.readingbat.misc.CSSNames.tabs
import com.github.readingbat.misc.CSSNames.userAnswers
import com.github.readingbat.misc.CSSNames.userResp
import kotlinx.css.*
import kotlinx.css.properties.TextDecoration
import kotlinx.css.properties.boxShadow

internal object CSSNames {
  const val checkBar = "checkBar"
  const val checkAnswers = "checkAnswers"
  const val spinner = "spinner"
  const val feedback = "feedback"
  const val funcCol = "funcCol"
  const val arrow = "arrow"
  const val refs = "refs"
  const val backLinkCls = "backLink"
  const val codeBlock = "codeBlock"
  const val kotlinCode = "kotlin-code"
  const val tabs = "tabs"
  const val userResp = "userResp"
  const val challengeDesc = "challenge-desc"
  const val userAnswers = "userAnswers"
  const val bodyHeaderCls = "bodyHeader"
  const val funcChoice = "funcChoice"
  const val funcItem = "funcItem"
  const val groupItemSrc = "groupItem"
  const val selected = "selected"
  const val status = "status"
  val fs = 115.pct
  val codeFs = 95.pct
}

internal val cssContent by lazy {
  CSSBuilder()
    .apply {
      rule("body, a, p, td, h1, h2, h3") {
        fontFamily = "normal small verdana, arial, helvetica, sans-serif"
      }
      body {
        backgroundColor = Color.white
      }
      a {
        textDecoration = TextDecoration.none
      }
      h2 {
        fontSize = LinearDimension("150%")
      }
      p {
        maxWidth = LinearDimension("700px")
      }
      rule(".$challengeDesc") {
        fontSize = fs
        marginLeft = 1.em
        marginBottom = 1.em
      }
      rule(".$bodyHeaderCls") {
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
      rule(".$userAnswers") {
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
      rule(".$userResp") {
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
      rule(".$backLinkCls") {
        //marginTop = 1.em
        fontSize = 120.pct
        marginLeft = 1.em
      }

      rule(".$codeBlock") {
        marginTop = 2.em
        marginLeft = 1.em
        marginRight = 1.em
        fontSize = codeFs
      }
      // This takes care of the blue vertical stripe to the left of the code
      rule("pre[class*=\"language-\"] > code") {
        val color = "#0600EE"
        borderLeft = "10px solid $color"
        boxShadow(Color(color), -1.px, 0.px, 0.px, 0.px)
        boxShadow(Color("#dfdfdf"), 0.px, 0.px, 0.px, 1.px)
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
        fontSize = codeFs
      }
      // This fixes a bug in the window size
      rule(".CodeMirror-scroll") {
        height = LinearDimension.auto
      }
    }.toString()
}
