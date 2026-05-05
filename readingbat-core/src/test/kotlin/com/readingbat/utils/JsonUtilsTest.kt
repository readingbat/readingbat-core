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

package com.readingbat.utils

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class JsonUtilsTest : StringSpec() {
  @Serializable
  private data class Person(val name: String, val age: Int)

  @Serializable
  private data class Wrapper(val tag: String, val values: List<Int>, val person: Person)

  init {
    "toJson serializes a primitive string" {
      "hello".toJson() shouldBe "\"hello\""
    }

    "toJson serializes a primitive number" {
      42.toJson() shouldBe "42"
    }

    "toJson serializes a boolean" {
      true.toJson() shouldBe "true"
    }

    "toJson serializes a simple data class" {
      Person("Ada", 36).toJson() shouldBe """{"name":"Ada","age":36}"""
    }

    "toJson serializes a list of primitives" {
      listOf(1, 2, 3).toJson() shouldBe "[1,2,3]"
    }

    "toJson serializes a map with string keys" {
      val map = mapOf("a" to 1, "b" to 2)
      map.toJson() shouldBe """{"a":1,"b":2}"""
    }

    "toJson serializes a nested data class round-trips through Json.decodeFromString" {
      val original = Wrapper(tag = "t", values = listOf(1, 2, 3), person = Person("Bob", 7))
      val encoded = original.toJson()
      Json.decodeFromString<Wrapper>(encoded) shouldBe original
    }

    "toJson serializes null when value is a nullable null" {
      val value: String? = null
      value.toJson() shouldBe "null"
    }
  }
}
