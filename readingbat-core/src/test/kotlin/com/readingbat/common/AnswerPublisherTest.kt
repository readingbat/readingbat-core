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

package com.readingbat.common

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import kotlin.reflect.full.declaredMemberFunctions
import io.kotest.matchers.string.shouldContain as shouldContainSubstring
import io.kotest.matchers.string.shouldNotContain as shouldNotContainSubstring

class AnswerPublisherTest : StringSpec() {
  init {
    // The teacher dashboard renders this string via innerHTML over a WebSocket, so student
    // answers must be HTML-escaped to prevent stored XSS, while the <br> separators stay literal.
    "formatDashboardAnswers reverses, limits, and joins answers with <br>" {
      AnswerPublisher.formatDashboardAnswers(listOf("a", "b", "c"), 2) shouldBe "c<br>b"
    }

    "formatDashboardAnswers leaves a plain answer unchanged" {
      AnswerPublisher.formatDashboardAnswers(listOf("42"), 10) shouldBe "42"
    }

    "formatDashboardAnswers returns empty string for no answers" {
      AnswerPublisher.formatDashboardAnswers(emptyList(), 10) shouldBe ""
    }

    "formatDashboardAnswers HTML-escapes a script-injection answer" {
      val result = AnswerPublisher.formatDashboardAnswers(listOf("<img src=x onerror=alert(1)>"), 10)
      result shouldNotContainSubstring "<img"
      result shouldContainSubstring "&lt;img"
    }

    "formatDashboardAnswers keeps the <br> separator literal between escaped answers" {
      val result = AnswerPublisher.formatDashboardAnswers(listOf("<b>x", "<b>y"), 10)
      result shouldBe "&lt;b&gt;y<br>&lt;b&gt;x"
    }

    "AnswerPublisher should declare publishAnswers" {
      val methodNames = AnswerPublisher::class.declaredMemberFunctions.map { it.name }
      methodNames shouldContain "publishAnswers"
    }

    "AnswerPublisher should declare publishLikeDislike" {
      val methodNames = AnswerPublisher::class.declaredMemberFunctions.map { it.name }
      methodNames shouldContain "publishLikeDislike"
    }

    "User should not declare publishAnswers" {
      val methodNames = User::class.declaredMemberFunctions.map { it.name }
      methodNames shouldNotContain "publishAnswers"
    }

    "User should not declare publishLikeDislike" {
      val methodNames = User::class.declaredMemberFunctions.map { it.name }
      methodNames shouldNotContain "publishLikeDislike"
    }

    "publishAnswers should accept User as first parameter" {
      val method = AnswerPublisher::class.declaredMemberFunctions.first { it.name == "publishAnswers" }
      // Parameters: extension receiver (if any), then the explicit params
      // For a top-level object function: first param is the instance, then the actual params
      val paramTypes = method.parameters.map { it.type.classifier }
      paramTypes shouldContain User::class
    }

    "publishLikeDislike should accept User as first parameter" {
      val method = AnswerPublisher::class.declaredMemberFunctions.first { it.name == "publishLikeDislike" }
      val paramTypes = method.parameters.map { it.type.classifier }
      paramTypes shouldContain User::class
    }
  }
}
