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

package com.github.readingbat

import com.github.readingbat.TestUtils.module
import com.github.readingbat.TestUtils.readTestContent
import com.github.readingbat.dsl.LanguageType.Java
import com.github.readingbat.dsl.LanguageType.Kotlin
import com.github.readingbat.dsl.LanguageType.Python
import io.kotest.assertions.ktor.shouldHaveStatus
import io.kotest.core.spec.style.StringSpec
import io.ktor.http.*
import io.ktor.http.HttpStatusCode.Companion.Found
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.testing.*


class RootTest : StringSpec(
  {
    val testContent = readTestContent()
    withTestApplication({ testContent.validate(); module(true, testContent) }) {
      handleRequest(HttpMethod.Get, "/").apply { response shouldHaveStatus Found }
      handleRequest(HttpMethod.Get, Java.contentRoot).apply { response shouldHaveStatus OK }
      handleRequest(HttpMethod.Get, Python.contentRoot).apply { response shouldHaveStatus OK }
      handleRequest(HttpMethod.Get, Kotlin.contentRoot).apply { response shouldHaveStatus OK }
    }
  })

