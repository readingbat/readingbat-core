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

import com.github.pambrose.common.redis.RedisUtils.withRedisPool
import com.github.pambrose.common.util.join
import com.github.readingbat.dsl.ChallengeGroup
import com.github.readingbat.dsl.LanguageType
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.CSSNames.funcItem
import com.github.readingbat.misc.CSSNames.groupChoice
import com.github.readingbat.misc.CSSNames.groupItemSrc
import com.github.readingbat.misc.CSSNames.tabs
import com.github.readingbat.misc.Constants.CHALLENGE_ROOT
import com.github.readingbat.misc.Constants.GREEN_CHECK
import com.github.readingbat.misc.Constants.STATIC_ROOT
import com.github.readingbat.misc.Constants.WHITE_CHECK
import com.github.readingbat.misc.UserId
import com.github.readingbat.misc.UserId.BrowserSession
import com.github.readingbat.misc.UserId.Companion.lookupUserId
import com.github.readingbat.misc.UserId.UserPrincipal
import kotlinx.html.*
import kotlinx.html.Entities.nbsp
import kotlinx.html.stream.createHTML
import redis.clients.jedis.Jedis

internal fun languageGroupPage(content: ReadingBatContent,
                               languageType: LanguageType,
                               loginAttempt: Boolean,
                               principal: UserPrincipal?,
                               browserSession: BrowserSession?) =
  createHTML()
    .html {
      val languageName = languageType.lowerName
      val loginPath = listOf(CHALLENGE_ROOT, languageName).join()
      val groups = content.findLanguage(languageType).challengeGroups

      fun TR.groupItem(redis: Jedis?, userId: UserId?, challengeGroup: ChallengeGroup<*>) {
        val groupName = challengeGroup.groupName
        val parsedDescription = challengeGroup.parsedDescription
        val challenges = challengeGroup.challenges

        val maxCnt = 12
        var cnt = 0
        var maxFound = false
        for (challenge in challenges) {
          if (challenge.isCorrect(redis, userId, browserSession)) cnt++
          if (cnt == maxCnt + 1) {
            maxFound = true
            break
          }
        }

        td(classes = funcItem) {
          div(classes = groupItemSrc) {
            a(classes = groupChoice) { href = listOf(CHALLENGE_ROOT, languageName, groupName).join(); +groupName }
            br { rawHtml(if (parsedDescription.isNotBlank()) parsedDescription else nbsp.text) }
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

      head { headDefault(content) }

      body {
        bodyHeader(principal, loginAttempt, content, languageType, loginPath, "Welcome to ReadingBat.")

        div(classes = tabs) {
          table {
            val cols = 3
            val size = groups.size
            val rows = size.rows(cols)

            withRedisPool { redis ->
              val userId = lookupUserId(principal, redis)

              (0 until rows).forEach { i ->
                tr {
                  groups[i].also { group -> groupItem(redis, userId, group) }
                  groups.elementAtOrNull(i + rows)?.also { groupItem(redis, userId, it) } ?: td {}
                  groups.elementAtOrNull(i + (2 * rows))?.also { groupItem(redis, userId, it) } ?: td {}
                }
              }
            }
          }
        }
      }
    }