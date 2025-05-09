/*
 * Copyright © 2023 Paul Ambrose (pambrose@mac.com)
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

package com.github.readingbat.common

import com.github.readingbat.common.Constants.INCOMPLETE_COLOR
import com.github.readingbat.common.CssNames.ADMIN_BUTTON
import com.github.readingbat.common.CssNames.ARROW
import com.github.readingbat.common.CssNames.BTN
import com.github.readingbat.common.CssNames.CENTER
import com.github.readingbat.common.CssNames.CHALLENGE_DESC
import com.github.readingbat.common.CssNames.CHECK_ANSWERS
import com.github.readingbat.common.CssNames.CODE_BLOCK
import com.github.readingbat.common.CssNames.CODINGBAT
import com.github.readingbat.common.CssNames.DASHBOARD
import com.github.readingbat.common.CssNames.EXPERIMENT
import com.github.readingbat.common.CssNames.FEEDBACK
import com.github.readingbat.common.CssNames.FUNC_COL
import com.github.readingbat.common.CssNames.FUNC_ITEM1
import com.github.readingbat.common.CssNames.FUNC_ITEM2
import com.github.readingbat.common.CssNames.GROUP_CHOICE
import com.github.readingbat.common.CssNames.GROUP_ITEM_SRC
import com.github.readingbat.common.CssNames.HINT
import com.github.readingbat.common.CssNames.INDENT_1EM
import com.github.readingbat.common.CssNames.INDENT_2EM
import com.github.readingbat.common.CssNames.INVOC_STAT
import com.github.readingbat.common.CssNames.INVOC_TABLE
import com.github.readingbat.common.CssNames.INVOC_TD
import com.github.readingbat.common.CssNames.KOTLIN_CODE
import com.github.readingbat.common.CssNames.LIKE_BUTTONS
import com.github.readingbat.common.CssNames.SELECTED_TAB
import com.github.readingbat.common.CssNames.STATUS
import com.github.readingbat.common.CssNames.SUCCESS
import com.github.readingbat.common.CssNames.TD_PADDING
import com.github.readingbat.common.CssNames.UNDERLINE
import com.github.readingbat.common.CssNames.USER_RESP
import kotlinx.css.Border
import kotlinx.css.BorderCollapse
import kotlinx.css.BorderCollapse.separate
import kotlinx.css.BorderStyle
import kotlinx.css.Color
import kotlinx.css.CssBuilder
import kotlinx.css.Display
import kotlinx.css.Display.block
import kotlinx.css.FontWeight.Companion.bold
import kotlinx.css.LinearDimension
import kotlinx.css.LinearDimension.Companion.auto
import kotlinx.css.ListStyleType
import kotlinx.css.Margin
import kotlinx.css.Padding
import kotlinx.css.Position
import kotlinx.css.TextAlign
import kotlinx.css.VerticalAlign
import kotlinx.css.a
import kotlinx.css.background
import kotlinx.css.backgroundColor
import kotlinx.css.body
import kotlinx.css.border
import kotlinx.css.borderCollapse
import kotlinx.css.borderLeft
import kotlinx.css.borderRadius
import kotlinx.css.borderSpacing
import kotlinx.css.borderWidth
import kotlinx.css.boxShadow
import kotlinx.css.color
import kotlinx.css.display
import kotlinx.css.em
import kotlinx.css.fontFamily
import kotlinx.css.fontSize
import kotlinx.css.fontWeight
import kotlinx.css.h2
import kotlinx.css.height
import kotlinx.css.lineHeight
import kotlinx.css.listStyleType
import kotlinx.css.margin
import kotlinx.css.marginBottom
import kotlinx.css.marginLeft
import kotlinx.css.marginRight
import kotlinx.css.marginTop
import kotlinx.css.maxWidth
import kotlinx.css.minWidth
import kotlinx.css.p
import kotlinx.css.padding
import kotlinx.css.paddingLeft
import kotlinx.css.paddingRight
import kotlinx.css.pct
import kotlinx.css.position
import kotlinx.css.properties.BoxShadow
import kotlinx.css.properties.BoxShadows
import kotlinx.css.properties.LineHeight
import kotlinx.css.properties.TextDecoration
import kotlinx.css.properties.TextDecorationLine.underline
import kotlinx.css.px
import kotlinx.css.textAlign
import kotlinx.css.textDecoration
import kotlinx.css.top
import kotlinx.css.verticalAlign
import kotlinx.css.width

internal object CssNames {
  const val CHECK_ANSWERS = "checkAnswers"
  const val ADMIN_BUTTON = "loadChallenge"
  const val LIKE_BUTTONS = "likeButtons"
  const val FEEDBACK = "hint"
  const val HINT = "feedback"
  const val FUNC_COL = "funcCol"
  const val ARROW = "arrow"
  const val EXPERIMENT = "experiment"
  const val CODINGBAT = "codingbat"
  const val CODE_BLOCK = "codeBlock"
  const val KOTLIN_CODE = "kotlin-code"
  const val USER_RESP = "userResponse"
  const val CHALLENGE_DESC = "challenge-desc"
  const val GROUP_CHOICE = "groupChoice"
  const val FUNC_ITEM1 = "funcItem1"
  const val FUNC_ITEM2 = "funcItem2"
  const val TD_PADDING = "tdPadding"
  const val GROUP_ITEM_SRC = "groupItem"
  const val SELECTED_TAB = "selected"
  const val STATUS = "status"
  const val SUCCESS = "success"
  const val DASHBOARD = "dashboard"
  const val INDENT_1EM = "indent-1em"
  const val INDENT_2EM = "indent-2em"
  const val UNDERLINE = "underline"
  const val INVOC_TABLE = "invoc_table"
  const val INVOC_TD = "invoc_td"
  const val INVOC_STAT = "invoc_stat"
  const val BTN = "btn"
  const val CENTER = "center"
}

internal val cssContent by lazy {
  val textFs = 115.pct
  val codeFs = 95.pct

  CssBuilder()
    .apply {

      rule("html, body") {
        fontSize = 16.px
        fontFamily = "verdana, arial, helvetica, sans-serif"
      }
      rule("body") {
        display = block
        marginTop = 8.px
        marginLeft = 8.px
        marginRight = 8.px
        lineHeight = LineHeight.normal
      }
      rule("h1, h2, h3, h4") {
        fontWeight = bold
      }
      rule("li") {
        marginTop = 10.px
      }
      p {
        maxWidth = 800.px
        lineHeight = LineHeight("1.5")
      }
      rule("p.max") {
        maxWidth = 800.px
      }
      rule(":link") {
        color = Color("#0000DD")
      }
      rule(":visited") {
        color = Color("#551A8B")
      }
      rule("td") {
        verticalAlign = VerticalAlign.middle
      }
      rule(".h1") {
        fontSize = 300.pct
      }
      rule(".h2") {
        fontSize = 166.pct
        textDecoration = TextDecoration.none
      }
      rule(".h3") {
        fontSize = 120.pct
      }
      rule("td.no") {
        minWidth = 22.px
        backgroundColor = Color.red
      }
      rule("td.ok") {
        minWidth = 22.px
        backgroundColor = Color.green
      }
      body {
        backgroundColor = Color.white
      }
      a {
        textDecoration = TextDecoration.none
      }
      h2 {
        fontSize = 150.pct
      }
      rule(".$CENTER") {
        display = block
        marginLeft = auto
        marginRight = auto
        width = LinearDimension("50%")
      }
      rule(".$CHALLENGE_DESC") {
        fontSize = textFs
        marginLeft = 1.em
        marginBottom = 1.em
      }
      rule(".$FUNC_ITEM1") {
        marginTop = 1.em
      }
      rule(".$FUNC_ITEM2") {
        marginTop = 1.em
        width = 300.px
      }
      rule("div.$TD_PADDING th") {
        marginTop = 1.em
        paddingRight = 1.em
      }
      rule("div.$TD_PADDING td") {
        marginTop = 1.em
        paddingRight = 1.em
      }
      rule(".$GROUP_CHOICE") {
        fontSize = 155.pct
      }
      rule("th, td") {
        padding = Padding(1.px)
        textAlign = TextAlign.left
      }
      rule("th") {
        fontSize = textFs
      }
      rule("div.$GROUP_ITEM_SRC") {
        maxWidth = 300.px
        minWidth = 300.px
        margin = Margin(15.px)
        padding = Padding(10.px)
        border = Border(1.px, BorderStyle.solid, Color.gray)
        borderRadius = LinearDimension("1em")
      }
      rule("td.$FUNC_COL") {
        fontSize = textFs
      }
      rule("td.$ARROW") {
        width = 2.em
        fontSize = textFs
        textAlign = TextAlign.center
      }
      rule(".$USER_RESP") {
        width = 15.em
        fontSize = 90.pct
      }
      rule("td.$FEEDBACK") {
        width = 10.em
        border = Border(7.px, BorderStyle.solid, Color.white)
      }
      rule("td.$HINT") {
        // width = 10.em
        // border = "7px solid white"
      }
      rule(".$DASHBOARD") {
        border = Border(1.px, BorderStyle.solid, Color("#DDDDDD"))
        borderCollapse = BorderCollapse.collapse
      }
      rule(".$CHECK_ANSWERS") {
        width = 14.em
        height = 2.em
        backgroundColor = Color("#f1f1f1")
        fontSize = textFs
        fontWeight = bold
        borderRadius = 6.px
      }
      rule(".$ADMIN_BUTTON") {
        paddingLeft = 1.em
        paddingRight = 1.em
        height = 2.em
        backgroundColor = Color("#f1f1f1")
        fontSize = 80.pct
        fontWeight = bold
        borderRadius = 6.px
      }
      rule(".$LIKE_BUTTONS") {
        backgroundColor = Color("#f1f1f1")
        width = 4.em
        height = 4.em
        borderRadius = 6.px
      }
      rule(".$STATUS") {
        marginLeft = 5.px
        fontSize = textFs
      }
      rule(".$SUCCESS") {
        marginLeft = 14.px
        fontSize = textFs
        color = Color.black
      }
      rule(".$INDENT_1EM") {
        marginLeft = 1.em
      }
      rule(".$INDENT_2EM") {
        marginLeft = 2.em
        marginBottom = 2.em
      }
      rule("#$SELECTED_TAB") {
        position = Position.relative
        top = LinearDimension("1px")
        background = "white"
      }
      rule(".$UNDERLINE") {
        textDecoration = TextDecoration(setOf(underline))
      }

      rule(".$INVOC_TABLE") {
        borderCollapse = separate
        borderSpacing = LinearDimension("10px 5px")
      }
      rule(".$INVOC_TD") {
        borderCollapse = separate
        border = Border(1.px, BorderStyle.solid, Color.black)
        width = 7.px
        height = 15.px
        backgroundColor = Color(INCOMPLETE_COLOR)
      }
      rule(".$INVOC_STAT") {
        paddingLeft = 5.px
        paddingRight = 1.px
        width = 10.px
      }
      // Turn links red on mouse hovers.
      rule("a:hover") {
        color = Color.red
      }
      rule("nav ul") {
        listStyleType = ListStyleType.none
        padding = Padding(0.px)
        margin = Margin(0.px)
      }
      rule("nav li") {
        display = Display.inline
        border = Border(1.px, BorderStyle.solid)
        borderWidth = LinearDimension("1px 1px 0 1px")
        margin = Margin(0.px, 25.px, 0.px, 6.px)
      }
      rule("nav li a") {
        padding = Padding(0.px, 40.px)
      }
      rule(".$EXPERIMENT") {
        marginTop = 1.em
        fontSize = textFs
      }
      rule(".$CODINGBAT") {
        marginTop = 2.em
        fontSize = textFs
      }

      rule(".$CODE_BLOCK") {
        marginTop = 2.em
        marginLeft = 1.em
        marginRight = 1.em
        fontSize = codeFs
      }

      rule(".$BTN") {
        background = "white"
      }
      rule(".$BTN:hover") {
        background = "#e7e7e7"
      }

      // This takes care of the blue vertical stripe to the left of the code
      rule("pre[class*=\"language-\"] > code") {
        val color = "#0600EE"
        borderLeft = Border(10.px, BorderStyle.solid, Color(color))
        boxShadow =
          BoxShadows().apply {
            BoxShadow(Color(color), (-1).px, 0.px, 0.px, 0.px)
          }
        boxShadow =
          BoxShadows().apply {
            BoxShadow(Color("#dfdfdf"), 0.px, 0.px, 0.px, 1.px)
          }
      }
      rule(".language-java") {
        // width = 950.px  // !important
      }
      rule(".language-python") {
        // width = 950.px  // !important
      }
      rule(".language-kotlin") {
        // width = 950.px  // !important
      }
      rule(".$KOTLIN_CODE") {
        marginLeft = 1.em
        marginRight = 1.em
      }
      // KotlinPlayground code
      rule(".CodeMirror") {
        fontSize = codeFs
      }
      // This fixes a bug in the window size
      rule(".CodeMirror-scroll") {
        height = auto
      }
      // This will add an outline to all the tables
      /*
      rule("table, th, td") {
        border = "1px solid black;"
        borderCollapse = BorderCollapse.separate
      }
      */
    }.toString()
}
