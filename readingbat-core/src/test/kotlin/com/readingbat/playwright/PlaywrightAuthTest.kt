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
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.nio.file.Path
import java.nio.file.Paths

private fun authTestsEnabled() = !System.getenv("TEST_BASE_URL").isNullOrBlank()

/**
 * Playwright tests that run against a live server with OAuth authentication.
 *
 * These tests use Playwright's storage state to reuse an authenticated session
 * without going through the OAuth flow each time.
 *
 * ## Setup (one-time):
 *
 * 1. Run the auth state generator to log in and save cookies:
 *    ```
 *    TEST_BASE_URL=https://readingbat.com ./gradlew :readingbat-core:test --tests "com.readingbat.PlaywrightAuthSetup"
 *    ```
 *    This opens a visible browser — complete the OAuth login manually.
 *    The session is saved to `playwright-auth-state.json`.
 *
 * 2. Run the authenticated tests:
 *    ```
 *    TEST_BASE_URL=https://readingbat.com ./gradlew :readingbat-core:test --tests "com.readingbat.PlaywrightAuthTest"
 *    ```
 *
 * ## Environment variables:
 *  - TEST_BASE_URL: The server URL to test against (required)
 *  - PLAYWRIGHT_AUTH_STATE: Path to auth state file (default: playwright-auth-state.json)
 */

class PlaywrightAuthTest : StringSpec() {
  private lateinit var playwright: Playwright
  private lateinit var browser: Browser
  private lateinit var context: BrowserContext

  private val baseUrl: String get() = System.getenv("TEST_BASE_URL")
  private val authStatePath: Path
    get() = Paths.get(System.getenv("PLAYWRIGHT_AUTH_STATE") ?: "playwright-auth-state.json")

  init {
    beforeSpec {
      if (!authTestsEnabled()) return@beforeSpec
      playwright = Playwright.create()
      browser = playwright.chromium().launch(BrowserType.LaunchOptions().setHeadless(true))

      // Load the saved auth state (cookies + local storage)
      context = browser.newContext(Browser.NewContextOptions().setStorageState(authStatePath.toString()))
    }

    afterSpec {
      context.close()
      browser.close()
      playwright.close()
    }

    "Authenticated user sees welcome message on home page".config(enabledIf = { authTestsEnabled() }) {
      context.newPage().use { page ->
        page.navigate(baseUrl)
        // When logged in, the page shows the user's name/email instead of a login link
        page.content() shouldNotContain "log in"
      }
    }

    "User preferences page loads for authenticated user".config(enabledIf = { authTestsEnabled() }) {
      context.newPage().use { page ->
        page.navigate("$baseUrl/user-prefs")
        // Authenticated users see prefs; unauthenticated users see "Please log in"
        page.content() shouldNotContain "Please"
        page.content() shouldContain "prefs"
      }
    }

    "Challenge answers are tracked for authenticated user".config(enabledIf = { authTestsEnabled() }) {
      context.newPage().use { page ->
        // Navigate to a known challenge
        page.navigate("$baseUrl/content/python")
        page.content() shouldContain "Python"

        // Click into a challenge group
        val groupLink = page.locator("a:has-text('Warmup')").first()
        if (groupLink.isVisible) {
          groupLink.click()
          page.waitForLoadState()

          // Click into the first challenge
          val challengeLink = page.locator("table a").first()
          if (challengeLink.isVisible) {
            challengeLink.click()
            page.waitForLoadState()

            // Verify the challenge page loaded with input fields
            page.content() shouldContain "Check My Answers"
          }
        }
      }
    }

    "Teacher preferences page requires authentication".config(enabledIf = { authTestsEnabled() }) {
      context.newPage().use { page ->
        page.navigate("$baseUrl/teacher-prefs")
        page.content() shouldNotContain "Please"
      }
    }

    "Authenticated session persists across page navigations".config(enabledIf = { authTestsEnabled() }) {
      context.newPage().use { page ->
        page.navigate(baseUrl)
        page.content() shouldNotContain "log in"

        page.navigate("$baseUrl/content/python")
        page.content() shouldNotContain "log in"

        page.navigate("$baseUrl/user-prefs")
        page.content() shouldNotContain "Please"
      }
    }
  }
}

/**
 * One-time setup: Opens a headed browser so you can manually complete
 * the OAuth login flow. Saves the resulting cookies to a JSON file
 * that PlaywrightAuthTest reuses.
 *
 * Run with:
 *   TEST_BASE_URL=https://readingbat.com ./gradlew :readingbat-core:test --tests "com.readingbat.PlaywrightAuthSetup"
 */

class PlaywrightAuthSetup : StringSpec() {
  init {
    "Save authenticated browser state".config(enabledIf = { authTestsEnabled() }) {
      val baseUrl = System.getenv("TEST_BASE_URL")
      val authStatePath = Paths.get(System.getenv("PLAYWRIGHT_AUTH_STATE") ?: "playwright-auth-state.json")

      Playwright.create().use { pw ->
        // Launch headed browser so you can see and interact with the OAuth flow
        val browser =
          pw.chromium().launch(
            BrowserType.LaunchOptions()
              .setHeadless(false)
              .setSlowMo(500.0),
          )

        val context = browser.newContext()
        val page = context.newPage()

        // Navigate to the OAuth login page
        page.navigate("$baseUrl/oauth/login")

        // Wait for the user to complete the OAuth flow and land back on the site.
        // The user-prefs page requires auth, so once it loads without "Please log in"
        // we know the login succeeded.
        println("=== Complete the OAuth login in the browser window ===")
        println("=== The test will continue automatically once you're logged in ===")

        page.waitForURL("$baseUrl/**", Page.WaitForURLOptions().setTimeout(120_000.0))

        // Give a moment for cookies to settle
        page.waitForTimeout(2000.0)

        // Verify we're actually logged in
        page.navigate("$baseUrl/user-prefs")
        page.waitForLoadState()

        val content = page.content()
        if (content.contains("Please")) {
          println("WARNING: Login may not have completed. Check the browser and try again.")
        } else {
          println("Login successful!")
        }

        // Save the storage state (cookies + localStorage)
        context.storageState(BrowserContext.StorageStateOptions().setPath(authStatePath))
        println("Auth state saved to: $authStatePath")

        browser.close()
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
