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

import com.github.readingbat.TestUtils.GROUP_NAME
import com.github.readingbat.TestUtils.functionInfo
import com.github.readingbat.TestUtils.module
import com.github.readingbat.TestUtils.pythonGroup
import com.github.readingbat.TestUtils.readTestContent
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.ktor.server.testing.*


class UserAnswers : StringSpec(
  {
    "Test user answers" {
      val testContent = readTestContent()
      withTestApplication({ testContent.validate(); module(true, testContent) }) {

        testContent.pythonGroup(GROUP_NAME)
          .apply {
            functionInfo("boolean_array_test")
              .apply {
                gradeResponseNB(0, "False, False").correct.shouldBeFalse()
                gradeResponseNB(0, "[false, False]").correct.shouldBeFalse()
                gradeResponseNB(0, "[true, False]").correct.shouldBeFalse()
                gradeResponseNB(0, "[False, False]").correct.shouldBeTrue()
              }
          }
      }
    }
  })

