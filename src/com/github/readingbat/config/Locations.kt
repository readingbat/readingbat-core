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

package com.github.readingbat.config

import com.github.readingbat.InvalidConfigurationException
import com.github.readingbat.dsl.LanguageType
import com.github.readingbat.dsl.LanguageType.Companion.toLanguageType
import com.github.readingbat.dsl.LanguageType.Kotlin
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.Constants.playground
import com.github.readingbat.misc.Constants.root
import com.github.readingbat.pages.challengeGroupPage
import com.github.readingbat.pages.challengePage
import com.github.readingbat.pages.languageGroupPage
import com.github.readingbat.pages.playgroundPage
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.http.ContentType.Text.Html
import io.ktor.locations.Location
import io.ktor.locations.get
import io.ktor.response.respondText
import io.ktor.routing.routing
import io.ktor.sessions.get
import io.ktor.sessions.sessions

internal fun Application.locations(readingBatContent: ReadingBatContent) {
  routing {

    fun validateLanguage(languageType: LanguageType) {
      if (!readingBatContent.hasLanguage(languageType) || !readingBatContent.hasGroups(languageType))
        throw InvalidConfigurationException("Invalid language: $languageType")
    }

    get<Language> { lang ->
      // This lookup has to take place outside of the lambda for proper exception handling
      validateLanguage(lang.languageType)
      val languageGroup = readingBatContent.findLanguage(lang.languageType)
      val html = languageGroupPage(readingBatContent, lang.languageType, languageGroup.challengeGroups)
      call.respondText(html, Html)
    }

    get<Language.Group> { group ->
      validateLanguage(group.languageType)
      val challengeGroup = readingBatContent.findGroup(group.languageType, group.groupName)
      val html = challengeGroupPage(challengeGroup)
      call.respondText(html, Html)
    }

    get<Language.Group.Challenge> { gc ->
      validateLanguage(gc.languageType)
      val challenge = readingBatContent.findChallenge(gc.languageType, gc.groupName, gc.challengeName)
      val clientSession: ClientSession? = call.sessions.get<ClientSession>()
      val html = challengePage(challenge, clientSession)
      call.respondText(html, Html)
    }

    get<PlaygroundRequest> { request ->
      val challenge = readingBatContent.findLanguage(Kotlin).findChallenge(request.groupName, request.challengeName)
      val html = playgroundPage(challenge)
      call.respondText(html, Html)
    }
  }
}

@Location("/$root/{language}")
data class Language(val language: String) {
  val languageType get() = language.toLanguageType()

  @Location("/{groupName}")
  data class Group(val language: Language, val groupName: String) {
    val languageType get() = language.languageType

    @Location("/{challengeName}")
    data class Challenge(val group: Group, val challengeName: String) {
      val languageType get() = group.languageType
      val groupName get() = group.groupName
    }
  }
}

@Location("/$playground/{groupName}/{challengeName}")
internal class PlaygroundRequest(val groupName: String, val challengeName: String)