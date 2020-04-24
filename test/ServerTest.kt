package org.github

import com.github.readingbat.LanguageType.Java
import com.github.readingbat.LanguageType.Python
import com.github.readingbat.module
import com.github.readingbat.readingBatContent
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class ServerTest {
  @Test
  fun testRoot() {
    val content = readingBatContent {}

    withTestApplication({ module(testing = true, content = content) }) {

      handleRequest(HttpMethod.Get, "/").apply {
        assertEquals(HttpStatusCode.OK, response.status())
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
