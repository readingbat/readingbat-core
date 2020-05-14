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
import com.github.pambrose.common.util.toPath
import com.github.readingbat.dsl.Challenge
import com.github.readingbat.misc.CSSNames.challengeDesc
import com.github.readingbat.misc.CSSNames.kotlinCode
import com.github.readingbat.misc.CSSNames.tabs
import com.github.readingbat.misc.Constants.root
import io.ktor.auth.UserIdPrincipal
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import org.apache.commons.text.StringEscapeUtils

// Playground customization details are here:
// https://jetbrains.github.io/kotlin-playground/
// https://jetbrains.github.io/kotlin-playground/examples/

fun playgroundPage(principal: UserIdPrincipal?, challenge: Challenge) =
  createHTML()
    .html {
      val languageType = challenge.languageType
      val languageName = languageType.lowerName
      val readingBatContent = challenge.readingBatContent
      val groupName = challenge.groupName
      val challengeName = challenge.challengeName
      val loginPath = listOf(languageName, groupName, challengeName).toPath()

      head {
        script { src = "https://unpkg.com/kotlin-playground@1"; attributes["data-selector"] = "code" }
        headDefault(readingBatContent)
      }

      body {
        bodyHeader(principal, readingBatContent, languageType, loginPath)

        div(classes = tabs) {
          h2 {
            this@body.addLink(groupName.decode(), "/$languageName/$groupName")
            rawHtml("${Entities.nbsp.text}&rarr;${Entities.nbsp.text}")
            this@body.addLink(challengeName.decode(), "/$languageName/$groupName/$challengeName")
          }

          if (challenge.description.isNotEmpty())
            div(classes = challengeDesc) { rawHtml(challenge.parsedDescription) }

          div(classes = kotlinCode) {
            val options =
              """theme="idea" indent="2" lines="true" highlight-on-fly="true" data-autocomplete="true" match-brackets="true""""
            rawHtml(
              """
              <code class="$kotlinCode" $options >
              ${StringEscapeUtils.escapeHtml4(challenge.funcInfo().originalCode)}
              </code>
              """)
          }
        }

        backLink("/$root/$languageName/$groupName/$challengeName")
      }
    }