/*
 * Copyright © 2026 Paul Ambrose (pambrose@mac.com)
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

package com.readingbat.playwright

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Playwright
import com.readingbat.TestData
import com.readingbat.kotest.TestSupport.initTestProperties
import com.readingbat.kotest.TestSupport.testModule
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer

/**
 * Verifies the two geometric promises the language tab strip makes.
 *
 * The selected tab has to read as an open tab: it covers the horizontal divider below the strip
 * with its own white background, so the assertion is geometric — the bottom of the selected tab's
 * painted box has to reach past the bottom of the divider. WebKit and Blink size that box
 * differently (WebKit leaves it fractional), which is why this runs in both: a version that only
 * lines up in one engine leaves a hairline showing in the other.
 *
 * The divider itself has to run the full width of the viewport. It is a block inside the body, so
 * it needs a negative margin to escape the body's 8px gutter, and nothing on the page may overflow
 * horizontally — either one leaves the rule visibly short of an edge.
 */
class PlaywrightTabsTest : StringSpec() {
  private var server: EmbeddedServer<*, *>? = null
  private lateinit var baseUrl: String
  private lateinit var playwright: Playwright
  private lateinit var browsers: Map<String, Browser>

  init {
    beforeSpec {
      initTestProperties()
      val testContent = TestData.readTestContent()
      server = embeddedServer(CIO, port = 0) { testModule(testContent) }.also { it.start(wait = false) }
      baseUrl = "http://localhost:${server!!.engine.resolvedConnectors().first().port}"

      playwright = Playwright.create()
      val options = BrowserType.LaunchOptions().setHeadless(true)
      browsers =
        mapOf(
          "chromium" to playwright.chromium().launch(options),
          "webkit" to playwright.webkit().launch(options),
        )
    }

    afterSpec {
      browsers.values.forEach { it.close() }
      playwright.close()
      server?.stop(1000, 5000)
    }

    listOf("chromium", "webkit").forEach { engine ->
      "Selected language tab hides the divider beneath it in $engine" {
        browsers.getValue(engine).newPage().use { page ->
          page.navigate("$baseUrl/content/java")

          val tabTop = page.evaluate(TAB_TOP) as Number
          val tabBottom = page.evaluate(TAB_BOTTOM) as Number
          val dividerTop = page.evaluate(DIVIDER_TOP) as Number
          val dividerBottom = page.evaluate(DIVIDER_BOTTOM) as Number

          // The tab has to sit on the divider before it can hide it.
          tabTop.toDouble() shouldBeLessThan dividerTop.toDouble()

          // And its painted box has to reach past the divider's far edge, or a hairline survives.
          tabBottom.toDouble() shouldBeGreaterThanOrEqual dividerBottom.toDouble()
        }
      }

      "Divider below the tab strip spans the full viewport width in $engine" {
        browsers.getValue(engine).newPage().use { page ->
          page.navigate("$baseUrl/content/java")

          val viewportWidth = (page.evaluate(VIEWPORT_WIDTH) as Number).toDouble()

          (page.evaluate(DIVIDER_LEFT) as Number).toDouble() shouldBe 0.0
          (page.evaluate(DIVIDER_RIGHT) as Number).toDouble() shouldBe viewportWidth

          // A page that scrolls sideways leaves the rule short again as soon as it is scrolled.
          (page.evaluate(SCROLL_WIDTH) as Number).toDouble() shouldBe viewportWidth
        }
      }
    }
  }

  companion object {
    private const val SELECTED_TAB = "document.getElementById('selected').getBoundingClientRect()"
    private const val DIVIDER = "document.querySelector('div.border-t').getBoundingClientRect()"
    private const val TAB_TOP = "() => $SELECTED_TAB.top"
    private const val TAB_BOTTOM = "() => $SELECTED_TAB.bottom"
    private const val DIVIDER_TOP = "() => $DIVIDER.top"
    private const val DIVIDER_BOTTOM = "() => $DIVIDER.bottom"
    private const val DIVIDER_LEFT = "() => $DIVIDER.left"
    private const val DIVIDER_RIGHT = "() => $DIVIDER.right"
    private const val VIEWPORT_WIDTH = "() => document.documentElement.clientWidth"
    private const val SCROLL_WIDTH = "() => document.documentElement.scrollWidth"
  }
}
