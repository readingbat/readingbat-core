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
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class AppConfigStoreTest : StringSpec() {
  init {
    "setProperty should store in configStore not System properties" {
      val prop = KtorProperty("test.appconfig.store")
      prop.setProperty("testValue")

      // Value should be in configStore
      KtorProperty.configStore["test.appconfig.store"] shouldBe "testValue"

      // Value should NOT be in System properties
      System.getProperty("test.appconfig.store") shouldBe null

      // Cleanup
      KtorProperty.configStore.remove("test.appconfig.store")
    }

    "getProperty should read from configStore" {
      KtorProperty.configStore["test.appconfig.read"] = "fromStore"

      val prop = KtorProperty("test.appconfig.read")
      prop.getProperty("default", errorOnNonInit = false) shouldBe "fromStore"

      KtorProperty.configStore.remove("test.appconfig.read")
    }

    "getProperty should return default when not in configStore" {
      val prop = KtorProperty("test.appconfig.missing")
      prop.getProperty("myDefault", errorOnNonInit = false) shouldBe "myDefault"
    }

    "getProperty boolean should read from configStore" {
      KtorProperty.configStore["test.appconfig.bool"] = "true"

      val prop = KtorProperty("test.appconfig.bool")
      prop.getProperty(false, errorOnNonInit = false) shouldBe true

      KtorProperty.configStore.remove("test.appconfig.bool")
    }

    "getProperty int should read from configStore" {
      KtorProperty.configStore["test.appconfig.int"] = "42"

      val prop = KtorProperty("test.appconfig.int")
      prop.getProperty(0, errorOnNonInit = false) shouldBe 42

      KtorProperty.configStore.remove("test.appconfig.int")
    }

    "getPropertyOrNull should return null when not in configStore" {
      val prop = KtorProperty("test.appconfig.nullable")
      prop.getPropertyOrNull(errorOnNonInit = false) shouldBe null
    }

    "getPropertyOrNull should return value when in configStore" {
      KtorProperty.configStore["test.appconfig.nullable2"] = "exists"

      val prop = KtorProperty("test.appconfig.nullable2")
      prop.getPropertyOrNull(errorOnNonInit = false) shouldBe "exists"

      KtorProperty.configStore.remove("test.appconfig.nullable2")
    }

    "isADefinedProperty should check configStore" {
      val prop = KtorProperty("test.appconfig.defined")
      prop.isADefinedProperty() shouldBe false

      KtorProperty.configStore["test.appconfig.defined"] = "value"
      prop.isADefinedProperty() shouldBe true

      KtorProperty.configStore.remove("test.appconfig.defined")
    }

    "resetForTesting should clear configStore and reset initialization" {
      KtorProperty.configStore["test.reset.key1"] = "val1"
      KtorProperty.configStore["test.reset.key2"] = "val2"

      KtorProperty.configStore["test.reset.key1"] shouldNotBe null
      KtorProperty.configStore["test.reset.key2"] shouldNotBe null

      KtorProperty.resetForTesting()

      KtorProperty.configStore.isEmpty() shouldBe true
    }

    "configStore should be isolated from System properties" {
      // Set a value via System properties
      System.setProperty("test.system.only", "systemValue")

      val prop = KtorProperty("test.system.only")
      // Should NOT find it in configStore
      prop.getProperty("default", errorOnNonInit = false) shouldBe "default"
      prop.getPropertyOrNull(errorOnNonInit = false) shouldBe null

      System.clearProperty("test.system.only")
    }

    "configStore should support concurrent access" {
      val props =
        (1..100).map {
          KtorProperty("test.concurrent.$it").also { prop ->
            prop.setProperty("value$it")
          }
        }

      props.forEachIndexed { i, prop ->
        prop.getProperty("", errorOnNonInit = false) shouldBe "value${i + 1}"
      }

      // Cleanup
      (1..100).forEach { KtorProperty.configStore.remove("test.concurrent.$it") }
    }
  }
}
