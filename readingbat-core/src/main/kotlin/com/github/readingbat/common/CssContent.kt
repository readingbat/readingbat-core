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

import kotlinx.css.Border
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
import kotlinx.css.borderLeft
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
import kotlinx.css.marginLeft
import kotlinx.css.marginRight
import kotlinx.css.marginTop
import kotlinx.css.maxWidth
import kotlinx.css.minWidth
import kotlinx.css.p
import kotlinx.css.padding
import kotlinx.css.paddingRight
import kotlinx.css.pct
import kotlinx.css.position
import kotlinx.css.properties.BoxShadow
import kotlinx.css.properties.BoxShadows
import kotlinx.css.properties.LineHeight
import kotlinx.css.properties.TextDecoration
import kotlinx.css.px
import kotlinx.css.textAlign
import kotlinx.css.textDecoration
import kotlinx.css.top
import kotlinx.css.verticalAlign
import kotlinx.css.width

/**
 * Legacy CSS content served at /static/styles.css.
 *
 * Global element rules (body, links, nav, headings) are duplicated in
 * tailwind-input.css @layer base. This file can be removed once Tailwind
 * is fully enabled and the CSS_ENDPOINT is retired.
 */
internal val cssContent by lazy {
  CssBuilder()
    .apply {
      // -- Global element rules --
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
      rule("th, td") {
        padding = Padding(1.px)
        textAlign = TextAlign.left
      }
      rule("th") {
        fontSize = 115.pct
      }
      rule("a:hover") {
        color = Color.red
      }

      // -- Navigation tabs --
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

      // -- Selected tab (used as id) --
      rule("#selected") {
        position = Position.relative
        top = LinearDimension("1px")
        background = "white"
      }

      // -- TD padding descendant selector --
      rule("div.tdPadding th") {
        marginTop = 1.em
        paddingRight = 1.em
      }
      rule("div.tdPadding td") {
        marginTop = 1.em
        paddingRight = 1.em
      }

      // -- Kotlin playground code block --
      rule(".kotlin-code") {
        marginLeft = 1.em
        marginRight = 1.em
      }

      // -- Bootstrap dropdown button --
      rule(".btn") {
        background = "white"
      }
      rule(".btn:hover") {
        background = "#e7e7e7"
      }

      // -- Prism.js code block left stripe --
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

      // -- CodeMirror (Kotlin Playground) fixes --
      rule(".CodeMirror") {
        fontSize = 95.pct
      }
      rule(".CodeMirror-scroll") {
        height = auto
      }
    }.toString()
}
