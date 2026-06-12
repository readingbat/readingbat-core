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

package com.readingbat.posts

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Tests for [ChallengePost.boundedResponses]. The answer-check handler previously derived the loop
 * count from the client-supplied `response*` params and indexed the challenge's fixed-size
 * invocation/answer arrays directly, so a request with more `response*` params than the challenge
 * has invocations threw `IndexOutOfBoundsException` (an unauthenticated 500/abuse vector). The loop
 * must instead be bounded by the challenge's invocation count.
 */
class BoundedResponsesTest : StringSpec() {
  init {
    "boundedResponses returns one trimmed response per invocation" {
      ChallengePost.boundedResponses(2, mapOf("response0" to " a ", "response1" to "b")) shouldBe
        listOf("a", "b")
    }

    "boundedResponses ignores extra response params instead of throwing" {
      ChallengePost.boundedResponses(
        2,
        mapOf("response0" to "a", "response1" to "b", "response2" to "c", "response3" to "d"),
      ) shouldBe listOf("a", "b")
    }

    "boundedResponses treats a missing response as blank (unanswered)" {
      ChallengePost.boundedResponses(3, mapOf("response0" to "a", "response2" to "c")) shouldBe
        listOf("a", "", "c")
    }

    "boundedResponses ignores non-numeric response keys" {
      ChallengePost.boundedResponses(1, mapOf("response0" to "a", "responseFoo" to "x")) shouldBe
        listOf("a")
    }
  }
}
