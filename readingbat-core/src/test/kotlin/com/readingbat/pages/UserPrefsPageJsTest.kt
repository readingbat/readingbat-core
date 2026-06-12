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

package com.readingbat.pages

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Tests for the inline `onSubmit` confirm() handlers on the user prefs page. The user's email and
 * the enrolled class display string (which includes the teacher-set class description) were
 * interpolated directly into single-quoted JS strings, so an apostrophe could break out and inject
 * script. Both must be escaped with escapeEcmaScript.
 */
class UserPrefsPageJsTest : StringSpec() {
  init {
    "deleteAccountConfirmJs embeds a plain email verbatim" {
      UserPrefsPage.deleteAccountConfirmJs("user@example.com") shouldBe
        "return confirm('Are you sure you want to permanently delete the account for user@example.com ?')"
    }

    "deleteAccountConfirmJs escapes a quote-injection email" {
      UserPrefsPage.deleteAccountConfirmJs("'); alert(1); ('") shouldBe
        "return confirm('Are you sure you want to permanently delete the account for \\'); alert(1); (\\' ?')"
    }

    "withdrawConfirmJs embeds a plain class string verbatim" {
      UserPrefsPage.withdrawConfirmJs("Intro to Java [abc123]") shouldBe
        "return confirm('Are you sure you want to withdraw from class Intro to Java [abc123]?')"
    }

    "withdrawConfirmJs escapes a quote-injection class description" {
      UserPrefsPage.withdrawConfirmJs("'); alert(document.cookie); ('") shouldBe
        "return confirm('Are you sure you want to withdraw from class \\'); alert(document.cookie); (\\'?')"
    }
  }
}
