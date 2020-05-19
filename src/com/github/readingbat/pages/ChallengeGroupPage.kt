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
import com.github.pambrose.common.util.join
import com.github.readingbat.dsl.Challenge
import com.github.readingbat.dsl.ChallengeGroup
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.CSSNames.funcChoice
import com.github.readingbat.misc.CSSNames.funcItem
import com.github.readingbat.misc.CSSNames.tabs
import com.github.readingbat.misc.Constants.CHALLENGE_ROOT
import com.github.readingbat.misc.Constants.STATIC_ROOT
import com.github.readingbat.misc.UserPrincipal
import kotlinx.html.*
import kotlinx.html.Entities.nbsp
import kotlinx.html.stream.createHTML

internal fun challengeGroupPage(principal: UserPrincipal?,
                                loginAttempt: Boolean,
                                content: ReadingBatContent,
                                challengeGroup: ChallengeGroup<*>) =
  createHTML()
    .html {
      val languageType = challengeGroup.languageType
      val languageName = languageType.lowerName
      val groupName = challengeGroup.groupName
      val challenges = challengeGroup.challenges
      val loginPath = listOf(languageName, groupName).join()

      head {
        headDefault(content)
      }

      body {
        bodyHeader(principal, loginAttempt, content, languageType, loginPath)

        div(classes = tabs) {

          h2 { +groupName.decode() }

          table {
            val cols = 3
            val size = challenges.size
            val rows = size.rows(cols)

            (0 until rows).forEach { i ->
              tr {
                style = "height:30"
                challenges.apply {
                  elementAt(i).also { funcCall(languageName, groupName, it) }
                  elementAtOrNull(i + rows)?.also { funcCall(languageName, groupName, it) } ?: td {}
                  elementAtOrNull(i + (2 * rows))?.also { funcCall(languageName, groupName, it) } ?: td {}
                }
              }
            }
          }
        }

        backLink(CHALLENGE_ROOT, languageName)
      }
    }

private fun TR.funcCall(prefix: String, groupName: String, challenge: Challenge) {
  td(classes = funcItem) {
    img { src = "$STATIC_ROOT/check.jpg" }
    rawHtml(nbsp.text)
    a(classes = funcChoice) {
      href = listOf(CHALLENGE_ROOT, prefix, groupName, challenge.challengeName).join()
      +challenge.challengeName
    }
  }
}

