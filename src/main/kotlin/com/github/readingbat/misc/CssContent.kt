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

import com.github.readingbat.misc.CSSNames.ARROW
import com.github.readingbat.misc.CSSNames.CHALLENGE_DESC
import com.github.readingbat.misc.CSSNames.CHECK_ANSWERS
import com.github.readingbat.misc.CSSNames.CODE_BLOCK
import com.github.readingbat.misc.CSSNames.DASHBOARD
import com.github.readingbat.misc.CSSNames.FEEDBACK
import com.github.readingbat.misc.CSSNames.FUNC_COL
import com.github.readingbat.misc.CSSNames.FUNC_ITEM
import com.github.readingbat.misc.CSSNames.GROUP_CHOICE
import com.github.readingbat.misc.CSSNames.GROUP_ITEM_SRC
import com.github.readingbat.misc.CSSNames.HINT
import com.github.readingbat.misc.CSSNames.INDENT_1EM
import com.github.readingbat.misc.CSSNames.INDENT_2EM
import com.github.readingbat.misc.CSSNames.KOTLIN_CODE
import com.github.readingbat.misc.CSSNames.REFS
import com.github.readingbat.misc.CSSNames.SELECTED_TAB
import com.github.readingbat.misc.CSSNames.STATUS
import com.github.readingbat.misc.CSSNames.SUCCESS
import com.github.readingbat.misc.CSSNames.USER_RESP
import kotlinx.css.*
import kotlinx.css.properties.LineHeight
import kotlinx.css.properties.TextDecoration
import kotlinx.css.properties.boxShadow

internal object CSSNames {
  const val CHECK_ANSWERS = "checkAnswers"
  const val FEEDBACK = "hint"
  const val HINT = "feedback"
  const val FUNC_COL = "funcCol"
  const val ARROW = "arrow"
  const val REFS = "refs"
  const val CODE_BLOCK = "codeBlock"
  const val KOTLIN_CODE = "kotlin-code"
  const val USER_RESP = "userResponse"
  const val CHALLENGE_DESC = "challenge-desc"
  const val GROUP_CHOICE = "groupChoice"
  const val FUNC_ITEM = "funcItem"
  const val GROUP_ITEM_SRC = "groupItem"
  const val SELECTED_TAB = "selected"
  const val STATUS = "status"
  const val SUCCESS = "success"
  const val DASHBOARD = "dashboard"
  const val INDENT_1EM = "indent-1em"
  const val INDENT_2EM = "indent-2em"
}

internal val cssContent by lazy {
  val textFs = 115.pct
  val codeFs = 95.pct

  CSSBuilder()
    .apply {

      rule("html, body") {
        //+"font-size: small;"
      }
      rule("html, body") {
        /* MOBILE-CSS prevents crazy shrinking of font in table e.g. on section page */
        //"-webkit-text-size-adjust:none; text-size-adjust:none;"
      }
      //rule("body, a, p, td, h1, h2, h3") {
      rule("html, body") {
        fontSize = LinearDimension.auto
        fontFamily = "verdana, arial, helvetica, sans-serif"
      }
      p {
        maxWidth = 800.px
        lineHeight = LineHeight("1.5")
      }
      rule("p.max") {
        maxWidth = 800.px
      }
      rule(":link") {
        color = Color("#0000DD;")
      }
      rule(":visited") {
        color = Color("#551A8B;")
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
      rule(".$CHALLENGE_DESC") {
        fontSize = textFs
        marginLeft = 1.em
        marginBottom = 1.em
      }
      rule(".$FUNC_ITEM") {
        marginTop = 1.em
        width = 275.px
      }
      rule(".$GROUP_CHOICE") {
        fontSize = 166.pct
      }
      rule("th, td") {
        padding = "1px"
        textAlign = TextAlign.left
      }
      rule("th") {
        fontSize = textFs
      }
      rule("div.$GROUP_ITEM_SRC") {
        maxWidth = 300.px
        minWidth = 300.px
        margin = "15px"
        padding = "10px"
        border = "1px solid gray"
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
        border = "7px solid white"
      }
      rule("td.$HINT") {
        //width = 10.em
        //border = "7px solid white"
      }
      rule(".$DASHBOARD") {
        border = "1px solid #DDDDDD;"
        borderCollapse = BorderCollapse.collapse
      }
      rule(".$CHECK_ANSWERS") {
        width = 14.em
        height = 2.em
        backgroundColor = Color("#f1f1f1")
        fontSize = textFs
        fontWeight = FontWeight.bold
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
      // Turn links red on mouse hovers.
      rule("a:hover") {
        color = Color.red
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
      rule(".$REFS") {
        marginTop = 2.em
        fontSize = textFs
      }

      rule(".$CODE_BLOCK") {
        marginTop = 2.em
        marginLeft = 1.em
        marginRight = 1.em
        fontSize = codeFs
      }
      // This takes care of the blue vertical stripe to the left of the code
      rule("pre[class*=\"language-\"] > code") {
        val color = "#0600EE"
        borderLeft = "10px solid $color"
        boxShadow(Color(color), (-1).px, 0.px, 0.px, 0.px)
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
        height = LinearDimension.auto
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
