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

import com.github.readingbat.dsl.ChallengeGroup
import com.github.readingbat.dsl.LanguageType
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.BrowserSession
import com.github.readingbat.misc.CSSNames.FUNC_ITEM
import com.github.readingbat.misc.CSSNames.GROUP_CHOICE
import com.github.readingbat.misc.CSSNames.GROUP_ITEM_SRC
import com.github.readingbat.misc.Constants.CHALLENGE_ROOT
import com.github.readingbat.misc.Constants.GREEN_CHECK
import com.github.readingbat.misc.Constants.MSG
import com.github.readingbat.misc.Constants.STATIC_ROOT
import com.github.readingbat.misc.Constants.WHITE_CHECK
import com.github.readingbat.misc.Message
import com.github.readingbat.misc.PageUtils.pathOf
import com.github.readingbat.misc.User
import com.github.readingbat.misc.User.Companion.fetchActiveClassCode
import com.github.readingbat.pages.ChallengeGroupPage.displayClassDescription
import com.github.readingbat.pages.ChallengeGroupPage.isCorrect
import com.github.readingbat.pages.PageCommon.bodyHeader
import com.github.readingbat.pages.PageCommon.headDefault
import com.github.readingbat.pages.PageCommon.rawHtml
import com.github.readingbat.pages.PageCommon.rows
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.ServerUtils.queryParam
import io.ktor.application.call
import io.ktor.sessions.get
import io.ktor.sessions.sessions
import kotlinx.html.*
import kotlinx.html.Entities.nbsp
import kotlinx.html.stream.createHTML
import redis.clients.jedis.Jedis

internal object LanguageGroupPage {

  fun PipelineCall.languageGroupPage(content: ReadingBatContent,
                                     user: User?,
                                     languageType: LanguageType,
                                     loginAttempt: Boolean,
                                     redis: Jedis?) =
    createHTML()
      .html {
        val browserSession = call.sessions.get<BrowserSession>()
        val languageName = languageType.languageName
        val loginPath = pathOf(CHALLENGE_ROOT, languageName)
        val groups = content[languageType].challengeGroups
        val activeClassCode = user.fetchActiveClassCode(redis)
        val enrollees = activeClassCode.fetchEnrollees(redis)

        fun TR.groupItem(user: User?,
                         challengeGroup: ChallengeGroup<*>,
                         redis: Jedis?) {
          val groupName = challengeGroup.groupName
          val challenges = challengeGroup.challenges

          var cnt = 0
          val maxCnt = 12
          var maxFound = false

          if (activeClassCode.isStudentMode) {
            for (challenge in challenges) {
              if (challenge.isCorrect(user, browserSession, redis))
                cnt++
              if (cnt == maxCnt + 1) {
                maxFound = true
                break
              }
            }
          }

          td(classes = FUNC_ITEM) {
            div(classes = GROUP_ITEM_SRC) {
              a(classes = GROUP_CHOICE) { href = pathOf(CHALLENGE_ROOT, languageName, groupName); +groupName.value }

              br { rawHtml(if (challengeGroup.description.isNotBlank()) challengeGroup.parsedDescription else nbsp.text) }

              if (activeClassCode.isStudentMode) {
                if (cnt == 0) {
                  img { src = "$STATIC_ROOT/$WHITE_CHECK" }
                }
                else {
                  repeat(if (maxFound) cnt - 1 else cnt) { img { src = "$STATIC_ROOT/$GREEN_CHECK" } }
                  if (maxFound) rawHtml("&hellip;")
                }
              }
            }
          }
        }

        head { headDefault(content) }

        body {
          val msg = Message(queryParam(MSG))
          bodyHeader(user,
                     loginAttempt,
                     content,
                     languageType,
                     loginPath,
                     true,
                     activeClassCode,
                     redis,
                     msg)


          if (activeClassCode.isTeacherMode)
            displayClassDescription(activeClassCode, enrollees, redis)

          table {
            val cols = 3
            val size = groups.size
            val rows = size.rows(cols)

            (0 until rows).forEach { i ->
              tr {
                groups[i].also { group -> groupItem(user, group, redis) }
                groups.elementAtOrNull(i + rows)?.also { groupItem(user, it, redis) } ?: td {}
                groups.elementAtOrNull(i + (2 * rows))?.also { groupItem(user, it, redis) } ?: td {}
              }
            }
          }
        }
      }
}