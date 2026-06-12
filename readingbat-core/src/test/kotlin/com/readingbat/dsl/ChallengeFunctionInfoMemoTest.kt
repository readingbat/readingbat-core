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

import com.readingbat.TestData
import com.readingbat.kotest.TestSupport.challengeByName
import com.readingbat.kotest.TestSupport.initTestProperties
import com.readingbat.kotest.TestSupport.javaGroup
import com.readingbat.kotest.TestSupport.testModule
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.ktor.server.testing.testApplication

/**
 * Guards that functionInfo() still memoizes its result after the #29 refactor that moved the
 * blocking fetch + script eval out of `ConcurrentHashMap.computeIfAbsent` to a double-checked
 * get/putIfAbsent: repeated calls must return the same cached instance.
 */
class ChallengeFunctionInfoMemoTest : StringSpec() {
  init {
    "functionInfo returns the same cached instance on repeated calls" {
      initTestProperties()
      testApplication {
        val testContent = TestData.readTestContent()
        application { testModule(testContent) }

        val challenge = testContent.javaGroup(TestData.GROUP_NAME).challengeByName("StringArrayTest1")
        challenge.functionInfo() shouldBeSameInstanceAs challenge.functionInfo()
      }
    }
  }
}
