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

package com.github.readingbat.pages

import com.github.readingbat.Constants
import com.github.readingbat.dsl.ChallengeGroup
import com.github.readingbat.dsl.LanguageType
import kotlinx.html.*

fun HTML.languageGroupPage(languageType: LanguageType, groups: List<ChallengeGroup>) {
  head {
    headDefault()
  }

  body {
    bodyHeader(languageType)
    div(classes = Constants.tabs) {
      table {
        val cols = 3
        val size = groups.size
        val rows = size.rows(cols)
        val languageName = languageType.lowerName

        (0 until rows).forEach { i ->
          tr {
            groups[i].also { group -> groupItem(languageName, group) }
            groups.elementAtOrNull(i + rows)?.also { groupItem(languageName, it) } ?: td {}
            groups.elementAtOrNull(i + (2 * rows))?.also { groupItem(languageName, it) } ?: td {}
          }
        }
      }
    }
  }
}