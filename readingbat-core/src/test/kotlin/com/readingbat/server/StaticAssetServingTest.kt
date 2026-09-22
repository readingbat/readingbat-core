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

package com.readingbat.server

import com.readingbat.TestData
import com.readingbat.common.Endpoints.STATIC_PATH
import com.readingbat.common.KtorProperty
import com.readingbat.common.Property
import com.readingbat.kotest.TestSupport.initTestProperties
import com.readingbat.kotest.TestSupport.testModule
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType.Text.Html
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode.Companion.NotModified
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.io.File

/**
 * Verifies that static assets are served by the app itself, out of the jar, rather than from a CDN.
 *
 * Two distinct failures are covered, and they are not the same thing — which is the whole point of
 * this spec. The route can be mounted correctly while the pages emit URLs that point somewhere else
 * entirely; that is exactly the state this codebase was in, with `staticResources` mounted at an
 * absolute CDN URL and therefore unreachable. Nothing failed, because nothing checked.
 *
 * Note that a missing asset answers **200** with the HTML not-found page, so status alone proves
 * nothing. The assertions below check content type and byte length instead.
 */
class StaticAssetServingTest : StringSpec() {
  /** Every file packaged under `static/`, discovered rather than listed, so new assets are covered. */
  private fun packagedAssets(): Pair<File, List<File>> {
    val root = File(javaClass.getResource("/static")!!.toURI())
    return root to root.walkTopDown().filter { it.isFile }.toList()
  }

  private val staticUrlRegex = """(?:src|href)="($STATIC_PATH/[^"]+)"""".toRegex()

  private suspend fun HttpClient.linksOn(path: String, pattern: Regex): List<String> =
    pattern.findAll(get(path).bodyAsText()).map { it.groupValues[1] }.toList()

  init {
    "every packaged static asset is served as itself" {
      initTestProperties()
      val testContent = TestData.readTestContent()
      val (root, assets) = packagedAssets()

      testApplication {
        application { testModule(testContent) }

        assets shouldHaveAtLeastSize 30

        assets.forEach { file ->
          val path = "$STATIC_PATH/${file.relativeTo(root).invariantSeparatorsPath}"
          val response = client.get(path)

          withClue(path) {
            response.status shouldBe OK
            // The not-found page is HTML and also answers 200, so this is the real assertion...
            response.contentType()?.match(Html) shouldBe false
            // ...and so is this: the body has to be the file, not a page about the file.
            response.bodyAsBytes().size.toLong() shouldBe file.length()
          }
        }
      }
    }

    "every static url the pages emit resolves" {
      initTestProperties()
      val testContent = TestData.readTestContent()

      testApplication {
        application { testModule(testContent) }

        val pages: MutableList<String> = ["/", "/help", "/no-such-page"]

        // Walk down to real challenge pages: only they carry the Prism and like/dislike assets.
        ["java", "python", "kotlin"].forEach { lang ->
          val languagePath = "/content/$lang"
          pages += languagePath
          val groups = client.linksOn(languagePath, """href="($languagePath/[^"/]+)"""".toRegex())
          groups.take(1).forEach { group ->
            pages += group
            pages += client.linksOn(group, """href="($group/[^"/]+)"""".toRegex()).take(1)
          }
        }

        val emitted = pages.flatMap { client.linksOn(it, staticUrlRegex) }.distinct()
        withClue("no page emitted a $STATIC_PATH url") { emitted shouldHaveAtLeastSize 5 }

        // Prove the crawl actually reached a challenge page: the head icons alone would satisfy the
        // count above, and every page emits those. Prism is emitted only by ChallengePage.
        // (The like/dislike images on that page render only when the DBMS is enabled, so they are
        // out of reach here.)
        withClue("crawl never reached a challenge page: $emitted") {
          emitted.any { "prism" in it } shouldBe true
        }

        emitted.forEach { url ->
          val response = client.get(url)
          withClue("emitted by a page: $url") {
            response.status shouldBe OK
            response.contentType()?.match(Html) shouldBe false
          }
        }
      }
    }

    "static assets are cacheable and carry no session cookie" {
      initTestProperties()
      val testContent = TestData.readTestContent()

      testApplication {
        application { testModule(testContent) }

        val response = client.get("$STATIC_PATH/white-check.jpg")

        response.headers[HttpHeaders.CacheControl].shouldNotBeNull() shouldContain "max-age="
        response.headers[HttpHeaders.ETag].shouldNotBeNull()
        // A cacheable public response must never carry a session cookie into a shared cache.
        response.headers[HttpHeaders.SetCookie] shouldBe null
      }
    }

    "images are not compressed, text still is" {
      initTestProperties()
      val testContent = TestData.readTestContent()

      testApplication {
        application { testModule(testContent) }

        suspend fun encodingOf(path: String) =
          client.get(path) { header(HttpHeaders.AcceptEncoding, "gzip, deflate") }
            .headers[HttpHeaders.ContentEncoding]

        // Compressing an already-compressed format burns CPU for nothing. This used to happen to
        // every image: deflate declared its own condition, which opted it out of Ktor's default
        // content-type exclusions entirely, and its priority put it ahead of gzip.
        encodingOf("$STATIC_PATH/white-check.jpg") shouldBe null
        encodingOf("$STATIC_PATH/icons/favicon.ico") shouldBe null

        // ...while text still compresses, and now via gzip.
        encodingOf("$STATIC_PATH/prism/java-prism.js") shouldBe "gzip"
      }
    }

    "a matching ETag is answered with 304" {
      initTestProperties()
      val testContent = TestData.readTestContent()

      testApplication {
        application { testModule(testContent) }

        val path = "$STATIC_PATH/white-check.jpg"
        val etag = client.get(path).headers[HttpHeaders.ETag].shouldNotBeNull()
        val cached = client.get(path) { header(HttpHeaders.IfNoneMatch, etag) }

        // staticResources only attaches the version; ConditionalHeaders is what turns it into this.
        cached.status shouldBe NotModified
      }
    }

    "a configured CDN prefix changes what the pages emit" {
      initTestProperties()
      val testContent = TestData.readTestContent()

      testApplication {
        application { testModule(testContent) }

        try {
          Property.STATIC_URL_PREFIX.setProperty("https://static.example.com")
          val body = client.get("/").bodyAsText()

          body shouldContain "https://static.example.com/icons/favicon-32x32.png"
          // The app still serves the files itself; only the emitted prefix moves.
          client.get("$STATIC_PATH/white-check.jpg").status shouldBe OK
        } finally {
          KtorProperty.configStore.remove(Property.STATIC_URL_PREFIX.propertyName)
        }
      }
    }

    "the explicit Tailwind route outranks the static tree" {
      initTestProperties()
      val testContent = TestData.readTestContent()

      testApplication {
        application { testModule(testContent) }

        // Both routes match this path; Ktor scores the constant segment above the tailcard. The
        // charset is what distinguishes them, so it pins which handler answered.
        client.get("$STATIC_PATH/tailwind.css")
          .contentType()
          .shouldNotBeNull()
          .toString() shouldContain "charset"
      }
    }
  }
}
