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

import com.github.pambrose.common.util.decode
import com.github.pambrose.common.util.pathOf
import com.github.readingbat.common.CssNames.CHALLENGE_DESC
import com.github.readingbat.common.CssNames.INDENT_1EM
import com.github.readingbat.common.CssNames.KOTLIN_CODE
import com.github.readingbat.common.Endpoints.CHALLENGE_ROOT
import com.github.readingbat.common.Endpoints.STATIC_ROOT
import com.github.readingbat.common.StaticFileNames.RUN_BUTTON
import com.github.readingbat.common.User
import com.github.readingbat.common.User.Companion.queryActiveTeachingClassCode
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.dsl.challenge.Challenge
import com.github.readingbat.pages.PageUtils.addLink
import com.github.readingbat.pages.PageUtils.backLink
import com.github.readingbat.pages.PageUtils.bodyHeader
import com.github.readingbat.pages.PageUtils.headDefault
import com.github.readingbat.pages.PageUtils.loadPingdomScript
import com.github.readingbat.pages.PageUtils.rawHtml
import kotlinx.html.body
import kotlinx.html.br
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.img
import kotlinx.html.script
import kotlinx.html.span
import kotlinx.html.stream.createHTML
import kotlinx.html.style
import org.apache.commons.text.StringEscapeUtils.escapeHtml4
import kotlin.collections.set

// Playground customization details are here:
// https://jetbrains.github.io/kotlin-playground/
// https://jetbrains.github.io/kotlin-playground/examples/

internal object PlaygroundPage {
  fun playgroundPage(
    content: ReadingBatContent,
    user: User?,
    challenge: Challenge,
    loginAttempt: Boolean,
  ) =
    createHTML()
      .html {
        val languageType = challenge.languageType
        val languageName = languageType.languageName
        val groupName = challenge.groupName
        val challengeName = challenge.challengeName
        val funcInfo = challenge.functionInfo()
        val loginPath = pathOf(CHALLENGE_ROOT, languageName, groupName, challengeName)
        val activeTeachingClassCode = queryActiveTeachingClassCode(user)

        head {
          script {
            src = "https://unpkg.com/kotlin-playground@1"
            attributes["data-selector"] = ".$KOTLIN_CODE"
          }
          headDefault()
        }

        body {
          bodyHeader(content, user, languageType, loginAttempt, loginPath, false, activeTeachingClassCode)

          h2 {
            val groupPath = pathOf(CHALLENGE_ROOT, languageName, groupName)
            this@body.addLink(groupName.value.decode(), groupPath)
            span {
              style = "padding-left:2px; padding-right:2px"
              rawHtml("&rarr;")
            }
            this@body.addLink(challengeName.value.decode(), pathOf(groupPath, challengeName))
          }

          if (challenge.description.isNotBlank())
            div(classes = CHALLENGE_DESC) { rawHtml(challenge.parsedDescription) }

          val options =
            """
              theme="idea" indent="2" lines="true"
              highlight-on-fly="true" data-autocomplete="true" match-brackets="true"
            """.trimIndent()

          rawHtml(
            """
              <div class=$KOTLIN_CODE $options>
              ${escapeHtml4(funcInfo.originalCode)}
              </div>
            """.trimIndent(),
          )

          br
          div(classes = INDENT_1EM) {
            +"Click on"
            img {
              height = "25"
              style = "vertical-align: bottom"
              src = pathOf(STATIC_ROOT, RUN_BUTTON)
            }
            +" to run the code"
          }

          backLink(CHALLENGE_ROOT, languageName.value, groupName.value, challengeName.value)
          loadPingdomScript()
        }
      }
}
