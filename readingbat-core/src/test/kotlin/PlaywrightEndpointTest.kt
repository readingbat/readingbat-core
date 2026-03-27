/*
 * Copyright © 2024 Paul Ambrose (pambrose@mac.com)
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

import TestData.GROUP_NAME
import TestData.readTestContent
import com.github.readingbat.kotest.TestSupport.initTestProperties
import com.github.readingbat.kotest.TestSupport.testModule
import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.options.WaitForSelectorState
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer

/**
 * Playwright browser tests that run against either a local embedded server or a remote server.
 *
 * ## Local mode (default):
 *   ./gradlew :readingbat-core:test --tests "PlaywrightEndpointTest"
 *   Starts an embedded Ktor server with test content on a random port.
 *
 * ## Remote/production mode:
 *   TEST_BASE_URL=https://readingbat.com ./gradlew :readingbat-core:test --tests "PlaywrightEndpointTest"
 *   Runs against the specified server. Tests that depend on local test content are skipped.
 */
class PlaywrightEndpointTest : StringSpec() {
  private var server: EmbeddedServer<*, *>? = null
  private lateinit var baseUrl: String
  private lateinit var playwright: Playwright
  private lateinit var browser: Browser

  private val isLocal get() = System.getenv("TEST_BASE_URL").isNullOrBlank()

  init {
    beforeSpec {
      if (isLocal) {
        initTestProperties()
        val testContent = readTestContent()
        server = embeddedServer(CIO, port = 0) { testModule(testContent) }.also { it.start(wait = false) }
        val port = server!!.engine.resolvedConnectors().first().port
        baseUrl = "http://localhost:$port"
      } else {
        baseUrl = System.getenv("TEST_BASE_URL")
      }

      playwright = Playwright.create()
      browser = playwright.chromium().launch(BrowserType.LaunchOptions().setHeadless(true))
    }

    afterSpec {
      browser.close()
      playwright.close()
      server?.stop(1000, 5000)
    }

    // ---- Tests that work against any server ----

    "Home page loads and shows language tabs" {
      browser.newPage().use { page ->
        page.navigate(baseUrl)
        page.title() shouldContain "ReadingBat"
        page.content().apply {
          shouldContain("Python")
          shouldContain("Java")
          shouldContain("Kotlin")
        }
      }
    }

    "Python language page loads" {
      browser.newPage().use { page ->
        page.navigate("$baseUrl/content/python")
        page.content() shouldContain "Python"
      }
    }

    "Java language page loads" {
      browser.newPage().use { page ->
        page.navigate("$baseUrl/content/java")
        page.content() shouldContain "Java"
      }
    }

    "Kotlin language page loads" {
      browser.newPage().use { page ->
        page.navigate("$baseUrl/content/kotlin")
        page.content() shouldContain "Kotlin"
      }
    }

    "Static pages load correctly" {
      browser.newPage().use { page ->
        page.apply {
          navigate("$baseUrl/about")
          content() shouldContain "About"

          navigate("$baseUrl/help")
          content() shouldContain "Help"

          navigate("$baseUrl/privacy-policy")
          content() shouldContain "Privacy"
        }
      }
    }

    // ---- Tests that depend on local test content ----

    "Navigate to test challenge group page".config(enabledIf = { isLocal }) {
      browser.newPage().use { page ->
        page.navigate("$baseUrl/content/python/$GROUP_NAME")
        page.content().apply {
          shouldContain("boolean_array_test")
          shouldContain("int_array_test")
          shouldContain("float_test")
        }
      }
    }

    "Challenge page displays code and input fields".config(enabledIf = { isLocal }) {
      browser.newPage().use { page ->
        page.navigate("$baseUrl/content/python/$GROUP_NAME/float_test")
        page.content() shouldContain "inc1"
        page.locator("#response0").isVisible shouldBe true
        page.locator("#response1").isVisible shouldBe true
        page.locator("#response2").isVisible shouldBe true
        page.locator("#response3").isVisible shouldBe true
        page.locator("button:has-text('Check My Answers')").isVisible shouldBe true
      }
    }

    "Submit correct answers and see success message".config(enabledIf = { isLocal }) {
      browser.newPage().use { page ->
        page.navigate("$baseUrl/content/python/$GROUP_NAME/float_test")

        // inc1(3.4) -> 4.4, inc1(4.4) -> 5.4, inc1(5.4) -> 6.4, inc1(2.3) -> 3.3
        page.fill("#response0", "4.4")
        page.fill("#response1", "5.4")
        page.fill("#response2", "6.4")
        page.fill("#response3", "3.3")

        page.click("button:has-text('Check My Answers')")

        page.waitForSelector(
          "#successId:not(:empty)",
          Page.WaitForSelectorOptions().setState(WaitForSelectorState.ATTACHED).setTimeout(5000.0),
        )

        page.textContent("#successId") shouldContain "Congratulations"
      }
    }

    "Submit wrong answers and see feedback".config(enabledIf = { isLocal }) {
      browser.newPage().use { page ->
        page.navigate("$baseUrl/content/python/$GROUP_NAME/float_test")

        page.fill("#response0", "wrong")
        page.fill("#response1", "wrong")
        page.fill("#response2", "wrong")
        page.fill("#response3", "wrong")

        page.click("button:has-text('Check My Answers')")

        page.waitForSelector(
          "#feedbackId0[style*='background-color']",
          Page.WaitForSelectorOptions().setTimeout(5000.0),
        )

        val bgColor = page.evalOnSelector("#feedbackId0", "el => el.style.backgroundColor") as String
        bgColor.shouldNotBeBlank()
        page.textContent("#successId") shouldBe ""
      }
    }

    "Submit empty answers and see not-answered feedback".config(enabledIf = { isLocal }) {
      browser.newPage().use { page ->
        page.navigate("$baseUrl/content/python/$GROUP_NAME/float_test")

        page.click("button:has-text('Check My Answers')")

        page.waitForSelector(
          "#feedbackId0[style*='background-color']",
          Page.WaitForSelectorOptions().setTimeout(5000.0),
        )

        page.textContent("#successId") shouldBe ""
      }
    }

    "Enter key triggers answer check".config(enabledIf = { isLocal }) {
      browser.newPage().use { page ->
        page.navigate("$baseUrl/content/python/$GROUP_NAME/float_test")

        page.fill("#response0", "4.4")
        page.fill("#response1", "5.4")
        page.fill("#response2", "6.4")
        page.fill("#response3", "3.3")
        page.press("#response3", "Enter")

        page.waitForSelector(
          "#successId:not(:empty)",
          Page.WaitForSelectorOptions().setState(WaitForSelectorState.ATTACHED).setTimeout(5000.0),
        )

        page.textContent("#successId") shouldContain "Congratulations"
      }
    }

    "Navigate between challenges via links".config(enabledIf = { isLocal }) {
      browser.newPage().use { page ->
        page.navigate("$baseUrl/content/python/$GROUP_NAME/float_test")

        val groupLink = page.locator("a:has-text('$GROUP_NAME')")
        groupLink.isVisible shouldBe true
        groupLink.click()
        page.waitForLoadState()

        page.url() shouldContain "python"
        page.content() shouldContain "float_test"
      }
    }
  }
}

private inline fun <R> Page.use(block: (Page) -> R): R =
  try {
    block(this)
  } finally {
    close()
  }
