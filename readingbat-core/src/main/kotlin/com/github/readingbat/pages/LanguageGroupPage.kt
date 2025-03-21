/*
 * Copyright © 2023 Paul Ambrose (pambrose@mac.com)
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

import com.github.pambrose.common.util.pathOf
import com.github.readingbat.common.Constants.MSG
import com.github.readingbat.common.CssNames.GROUP_CHOICE
import com.github.readingbat.common.CssNames.GROUP_ITEM_SRC
import com.github.readingbat.common.Endpoints.CHALLENGE_ROOT
import com.github.readingbat.common.Endpoints.STATIC_ROOT
import com.github.readingbat.common.Message
import com.github.readingbat.common.StaticFileNames.GREEN_CHECK
import com.github.readingbat.common.StaticFileNames.WHITE_CHECK
import com.github.readingbat.common.User
import com.github.readingbat.common.User.Companion.queryActiveTeachingClassCode
import com.github.readingbat.common.browserSession
import com.github.readingbat.dsl.ChallengeGroup
import com.github.readingbat.dsl.LanguageType
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.pages.ChallengeGroupPage.displayClassDescription
import com.github.readingbat.pages.PageUtils.bodyHeader
import com.github.readingbat.pages.PageUtils.headDefault
import com.github.readingbat.pages.PageUtils.loadPingdomScript
import com.github.readingbat.pages.PageUtils.rawHtml
import com.github.readingbat.server.GroupName.Companion.EMPTY_GROUP
import com.github.readingbat.server.ServerUtils.queryParam
import com.github.readingbat.server.ServerUtils.rows
import io.ktor.server.routing.RoutingContext
import kotlinx.html.Entities.nbsp
import kotlinx.html.TR
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.img
import kotlinx.html.p
import kotlinx.html.stream.createHTML
import kotlinx.html.table
import kotlinx.html.td
import kotlinx.html.tr

internal object LanguageGroupPage {
  fun RoutingContext.languageGroupPage(
    content: ReadingBatContent,
    user: User?,
    languageType: LanguageType,
    loginAttempt: Boolean,
  ) =
    createHTML()
      .html {
        val browserSession = call.browserSession
        val languageName = languageType.languageName
        val loginPath = pathOf(CHALLENGE_ROOT, languageName)
        val groups = content[languageType].challengeGroups
        val activeTeachingClassCode = queryActiveTeachingClassCode(user)
        val enrollees = activeTeachingClassCode.fetchEnrollees()

        fun TR.groupItem(user: User?, challengeGroup: ChallengeGroup<*>) {
          val groupName = challengeGroup.groupName
          val challenges = challengeGroup.challenges

          var cnt = 0
          val maxCnt = 12
          var maxFound = false

          if (activeTeachingClassCode.isNotEnabled) {
            for (challenge in challenges) {
              if (challenge.isCorrect(user, browserSession))
                cnt++
              if (cnt == maxCnt + 1) {
                maxFound = true
                break
              }
            }
          }

          td {
            div(classes = GROUP_ITEM_SRC) {
              a(classes = GROUP_CHOICE) {
                href = pathOf(CHALLENGE_ROOT, languageName, groupName)
                +groupName.toString()
              }

              p {
                rawHtml(if (challengeGroup.description.isNotBlank()) challengeGroup.parsedDescription else nbsp.text)
              }

              if (activeTeachingClassCode.isNotEnabled) {
                if (cnt == 0) {
                  img { src = pathOf(STATIC_ROOT, WHITE_CHECK) }
                } else {
                  repeat(if (maxFound) cnt - 1 else cnt) { img { src = pathOf(STATIC_ROOT, GREEN_CHECK) } }
                  if (maxFound) rawHtml("&hellip;")
                }
              }
            }
          }
        }

        head { headDefault() }

        body {
          val msg = Message(queryParam(MSG))

          bodyHeader(content, user, languageType, loginAttempt, loginPath, true, activeTeachingClassCode, msg)

          if (activeTeachingClassCode.isEnabled)
            displayClassDescription(activeTeachingClassCode, languageName, EMPTY_GROUP, enrollees)

          table {
            val cols = 3
            val size = groups.size
            val rows = size.rows(cols)

            repeat(rows) { i ->
              tr {
                groups[i].also { group -> groupItem(user, group) }
                groups.elementAtOrNull(i + rows)?.also { groupItem(user, it) } ?: td {}
                groups.elementAtOrNull(i + (2 * rows))?.also { groupItem(user, it) } ?: td {}
              }
            }
          }

          loadPingdomScript()
        }
      }
}
