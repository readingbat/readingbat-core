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

package com.readingbat.dsl

import com.readingbat.dsl.parse.KotlinParse.stripLeadingComments
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class StripLeadingCommentsTest : StringSpec() {
  init {
    "strips multi-line copyright block comment" {
      val lines =
        listOf(
        "/*",
        " * Copyright © 2023 Paul Ambrose",
        " */",
        "",
        "fun hello() = 1",
      )
      stripLeadingComments(lines) shouldBe listOf("fun hello() = 1")
    }

    "keeps non-copyright multi-line block comment" {
      val lines =
        listOf(
        "/* This is a regular comment */",
        "fun hello() = 1",
      )
      stripLeadingComments(lines) shouldBe lines
    }

    "strips single-line copyright block comment" {
      val lines =
        listOf(
        "/* Copyright 2023 */",
        "fun hello() = 1",
      )
      stripLeadingComments(lines) shouldBe listOf("fun hello() = 1")
    }

    "strips copyright line comment" {
      val lines =
        listOf(
        "// Copyright 2023",
        "fun hello() = 1",
      )
      stripLeadingComments(lines) shouldBe listOf("fun hello() = 1")
    }

    "keeps non-copyright line comment" {
      val lines =
        listOf(
        "// This is a regular comment",
        "fun hello() = 1",
      )
      stripLeadingComments(lines) shouldBe lines
    }

    "strips leading blank lines before code" {
      val lines =
        listOf(
        "",
        "",
        "fun hello() = 1",
      )
      stripLeadingComments(lines) shouldBe listOf("fun hello() = 1")
    }

    "strips copyright block followed by blank lines" {
      val lines =
        listOf(
        "/*",
        " * Copyright © 2023",
        " */",
        "",
        "",
        "fun hello() = 1",
      )
      stripLeadingComments(lines) shouldBe listOf("fun hello() = 1")
    }

    "keeps non-copyright block comment after copyright block" {
      val lines =
        listOf(
        "/* Copyright 2023 */",
        "",
        "/* This describes the function */",
        "fun hello() = 1",
      )
      stripLeadingComments(lines) shouldBe
        listOf(
        "/* This describes the function */",
        "fun hello() = 1",
      )
    }

    "handles empty input" {
      stripLeadingComments(emptyList()) shouldBe emptyList()
    }

    "handles all-blank input" {
      stripLeadingComments(listOf("", "  ", "")) shouldBe emptyList()
    }

    "copyright check is case-insensitive" {
      val lines =
        listOf(
        "/* cOpYrIgHt 2023 */",
        "fun hello() = 1",
      )
      stripLeadingComments(lines) shouldBe listOf("fun hello() = 1")
    }

    "keeps multi-line non-copyright block comment" {
      val lines =
        listOf(
        "/*",
        " * This is a doc comment",
        " */",
        "fun hello() = 1",
      )
      stripLeadingComments(lines) shouldBe lines
    }

    "handles unclosed multi-line copyright block at end of input" {
      val lines =
        listOf(
        "/*",
        " * Copyright 2023",
      )
      // The block never closes, but all lines contain copyright — should strip them
      stripLeadingComments(lines) shouldBe emptyList()
    }

    "handles code with no leading comments" {
      val lines =
        listOf(
        "fun hello() = 1",
        "fun world() = 2",
      )
      stripLeadingComments(lines) shouldBe lines
    }
  }
}
