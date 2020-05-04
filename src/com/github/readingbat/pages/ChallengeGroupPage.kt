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

import com.github.pambrose.common.util.decode
import com.github.readingbat.dsl.Challenge
import com.github.readingbat.dsl.ChallengeGroup
import com.github.readingbat.misc.Constants.checkJpg
import com.github.readingbat.misc.Constants.funcChoice
import com.github.readingbat.misc.Constants.funcItem
import com.github.readingbat.misc.Constants.root
import com.github.readingbat.misc.Constants.tabs
import kotlinx.html.*
import kotlinx.html.Entities.nbsp

internal fun HTML.challengeGroupPage(challengeGroup: ChallengeGroup<*>) {

  val languageType = challengeGroup.languageType
  val languageName = languageType.lowerName
  val groupName = challengeGroup.name
  val challenges = challengeGroup.challenges

  head {
    headDefault()
  }

  body {
    bodyHeader(languageType)

    div(classes = tabs) {

      h2 { +groupName.decode() }

      table {
        val cols = 3
        val size = challenges.size
        val rows = size.rows(cols)

        (0 until rows).forEach { i ->
          tr {
            challenges.apply {
              elementAt(i).also { funcCall(languageName, groupName, it) }
              elementAtOrNull(i + rows)?.also { funcCall(languageName, groupName, it) } ?: td {}
              elementAtOrNull(i + (2 * rows))?.also { funcCall(languageName, groupName, it) } ?: td {}
            }
          }
        }
      }
    }

    backLink("/$root/$languageName")
  }
}

private fun TR.funcCall(prefix: String, groupName: String, challenge: Challenge) {
  td(classes = funcItem) {
    img { src = checkJpg }
    rawHtml(nbsp.text)
    a(classes = funcChoice) { href = "/$root/$prefix/$groupName/${challenge.name}"; +challenge.name }
  }
}

