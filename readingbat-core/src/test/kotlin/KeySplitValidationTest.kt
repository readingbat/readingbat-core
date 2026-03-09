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

import com.github.readingbat.common.KeyConstants.KEY_SEP
import com.github.readingbat.common.KeyConstants.keyOf
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe

class KeySplitValidationTest : StringSpec(
  {
    "valid key with 4 segments passes bounds check" {
      val key = keyOf("prefix", "auth", "userId123", "md5hash")
      val parts = key.split(KEY_SEP)
      parts.size shouldBeGreaterThanOrEqual 4
      parts[1] shouldBe "auth"
      parts[2] shouldBe "userId123"
      parts[3] shouldBe "md5hash"
    }

    "valid key with more than 4 segments passes bounds check" {
      val key = keyOf("prefix", "auth", "userId123", "md5hash", "extra")
      val parts = key.split(KEY_SEP)
      parts.size shouldBeGreaterThanOrEqual 4
      parts[1] shouldBe "auth"
      parts[2] shouldBe "userId123"
      parts[3] shouldBe "md5hash"
    }

    "empty string has fewer than 4 segments" {
      val parts = "".split(KEY_SEP)
      parts.size shouldBeLessThan 4
    }

    "single value has fewer than 4 segments" {
      val parts = "onlyOneValue".split(KEY_SEP)
      parts.size shouldBeLessThan 4
    }

    "two segments has fewer than 4 segments" {
      val parts = "a${KEY_SEP}b".split(KEY_SEP)
      parts.size shouldBe 2
      parts.size shouldBeLessThan 4
    }

    "three segments has fewer than 4 segments" {
      val parts = "a${KEY_SEP}b${KEY_SEP}c".split(KEY_SEP)
      parts.size shouldBe 3
      parts.size shouldBeLessThan 4
    }

    "exactly 4 segments passes bounds check" {
      val parts = "a${KEY_SEP}b${KEY_SEP}c${KEY_SEP}d".split(KEY_SEP)
      parts.size shouldBe 4
      parts.size shouldBeGreaterThanOrEqual 4
    }

    "keyOf produces correctly split keys" {
      val key = keyOf("correctAnswers", "auth", "user42", "abc123def")
      val parts = key.split(KEY_SEP)
      parts.size shouldBe 4
      parts[0] shouldBe "correctAnswers"
      parts[1] shouldBe "auth"
      parts[2] shouldBe "user42"
      parts[3] shouldBe "abc123def"
    }

    "malformed key without separator fails bounds check" {
      val parts = "malformedkey".split(KEY_SEP)
      parts.size shouldBeLessThan 4
    }

    "key with pipe in value does not affect split" {
      val key = "prefix${KEY_SEP}auth${KEY_SEP}id${KEY_SEP}md5"
      val parts = key.split(KEY_SEP)
      parts.size shouldBeGreaterThanOrEqual 4
      parts[1] shouldBe "auth"
      parts[2] shouldBe "id"
      parts[3] shouldBe "md5"
    }
  },
)
