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
 * Tests for [StudentSummaryPage.confirmRemovalScript], which builds the inline `onSubmit`
 * confirm() handler for the remove-from-class button. The student name is user-controlled, so a
 * name containing a single quote (or other JS metacharacters) must be escaped to prevent it from
 * breaking out of the JS string literal and injecting script into the teacher's page.
 */
class StudentSummaryPageTest : StringSpec() {
  init {
    "confirmRemovalScript embeds a plain name verbatim" {
      StudentSummaryPage.confirmRemovalScript("Ada Lovelace") shouldBe
        "return confirm('Are you sure you want to remove Ada Lovelace from the class?')"
    }

    "confirmRemovalScript escapes quotes so a name cannot break out of the JS string" {
      // Every injected apostrophe is backslash-escaped, so it stays inside the confirm() literal
      // instead of terminating it and starting an alert() call.
      StudentSummaryPage.confirmRemovalScript("'); alert(document.cookie); ('") shouldBe
        "return confirm('Are you sure you want to remove \\'); alert(document.cookie); (\\' from the class?')"
    }

    "confirmRemovalScript escapes an apostrophe in a real name" {
      StudentSummaryPage.confirmRemovalScript("O'Brien") shouldBe
        "return confirm('Are you sure you want to remove O\\'Brien from the class?')"
    }
  }
}
