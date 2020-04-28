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
import com.github.readingbat.Constants.challengeDesc
import com.github.readingbat.Constants.kotlinCode
import com.github.readingbat.Constants.tabs
import com.github.readingbat.dsl.Challenge
import kotlinx.html.*
import org.apache.commons.text.StringEscapeUtils

fun HTML.playgroundPage(challenge: Challenge) {
  val languageType = challenge.languageType
  val languageName = languageType.lowerName
  val groupName = challenge.groupName
  val name = challenge.name

  head {
    script { src = "https://unpkg.com/kotlin-playground@1"; attributes["data-selector"] = "code" }

    headDefault()
  }

  body {

    bodyHeader(languageType)

    div(classes = tabs) {
      h2 {
        a { href = "/$languageName/$groupName"; +groupName.decode() }
        rawHtml("${Entities.nbsp.text}&rarr;${Entities.nbsp.text}")
        a { href = "/$languageName/$groupName/$name"; +name.decode() }
      }

      if (challenge.description.isNotEmpty())
        div(classes = challengeDesc) { rawHtml(challenge.parsedDescription) }

      // Customization details are here: https://jetbrains.github.io/kotlin-playground/
      div(classes = kotlinCode) {
        rawHtml("""
          <code class="$kotlinCode" theme="idea" indent="2" lines=true highlight-on-fly=true data-autocomplete=true
            match-brackets=true >
          ${StringEscapeUtils.escapeHtml4(challenge.funcInfo().code)}
          </code>
        """)
      }
    }

    backLink("/$languageName/$groupName/$name")
  }
}