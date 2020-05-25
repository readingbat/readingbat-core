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
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.CSSNames.challengeDesc
import com.github.readingbat.misc.CSSNames.kotlinCode
import com.github.readingbat.misc.CSSNames.tabs
import com.github.readingbat.misc.Constants.CHALLENGE_ROOT
import com.github.readingbat.misc.Constants.STATIC_ROOT
import com.github.readingbat.misc.PageUtils.pathOf
import com.github.readingbat.pages.PageCommon.addLink
import com.github.readingbat.pages.PageCommon.backLink
import com.github.readingbat.pages.PageCommon.bodyHeader
import com.github.readingbat.pages.PageCommon.headDefault
import com.github.readingbat.pages.PageCommon.rawHtml
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.fetchPrincipal
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import org.apache.commons.text.StringEscapeUtils.escapeHtml4
import kotlin.collections.set

// Playground customization details are here:
// https://jetbrains.github.io/kotlin-playground/
// https://jetbrains.github.io/kotlin-playground/examples/

internal object PlaygroundPage {

  fun PipelineCall.playgroundPage(content: ReadingBatContent,
                                  challenge: Challenge,
                                  loginAttempt: Boolean) =
    createHTML()
      .html {
        val principal = fetchPrincipal(loginAttempt)
        val languageType = challenge.languageType
        val groupName = challenge.groupName
        val challengeName = challenge.challengeName
        val languageName = languageType.lowerName
        val funcInfo = challenge.funcInfo(content)
        val loginPath = pathOf(CHALLENGE_ROOT, languageName, groupName, challengeName)

        head {
          script { src = "https://unpkg.com/kotlin-playground@1"; attributes["data-selector"] = ".$kotlinCode" }
          headDefault(content)
        }

        body {
          bodyHeader(principal, loginAttempt, content, languageType, loginPath)

          div(classes = tabs) {
            h2 {
              val groupPath = pathOf(CHALLENGE_ROOT, languageName, groupName)
              this@body.addLink(groupName.decode(), groupPath)
              span { style = "padding-left:2px; padding-right:2px;"; rawHtml("&rarr;") }
              this@body.addLink(challengeName.decode(), pathOf(groupPath, challengeName))
            }

            if (challenge.description.isNotEmpty())
              div(classes = challengeDesc) { rawHtml(challenge.parsedDescription) }

            val options =
              """
              theme="idea" indent="2" lines="true"  
              highlight-on-fly="true" data-autocomplete="true" match-brackets="true" 
            """.trimIndent()

            rawHtml(
              """
              <div class=$kotlinCode $options>  
              ${escapeHtml4(funcInfo.originalCode)}
              </div>
            """.trimIndent())
          }

          br
          div {
            style = "margin-left: 1em;"
            +"Click on"
            img { height = "25"; style = "vertical-align: bottom"; src = "$STATIC_ROOT/run-button.png" }
            +" to run the code"
          }

          backLink(CHALLENGE_ROOT, languageName, groupName, challengeName)
        }
      }
}