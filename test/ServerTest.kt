package com.github.readingbat

import com.github.readingbat.LanguageType.Java
import com.github.readingbat.LanguageType.Python
import com.github.readingbat.ReadingBatServer.userContent
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ServerTest {
  @Test
  fun testRoot() {
    val content = readingBatContent {

      java {
        repoRoot = "Something"
      }

      python {
        repoRoot = "Something"
      }
    }

    withTestApplication({
      content.validate()
      module(testing = true, content = userContent)
    }
    ) {

      handleRequest(HttpMethod.Get, "/").apply {
        assertEquals(HttpStatusCode.Found, response.status())
      }

      handleRequest(HttpMethod.Get, "/${Java.lowerName}").apply {
        assertEquals(HttpStatusCode.OK, response.status())
      }
      handleRequest(HttpMethod.Get, "/${Python.lowerName}").apply {
        assertEquals(HttpStatusCode.OK, response.status())
      }
    }
  }
}
