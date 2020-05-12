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
import io.ktor.html.respondHtml
import io.ktor.locations.Location
import io.ktor.locations.get
import io.ktor.routing.routing
import io.ktor.sessions.get
import io.ktor.sessions.sessions
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

internal fun Application.locations(readingBatContent: ReadingBatContent) {
  routing {

    fun validateLanguage(languageType: LanguageType) {
      if (!readingBatContent.hasLanguage(languageType) || !readingBatContent.hasGroups(languageType))
        throw InvalidConfigurationException("Invlaid language: $languageType")
    }

    get<Language> {
      // This lookup has to take place outside of the lambda for proper exception handling
      val session = call.sessions.get<ClientSession>()
      logger.info { session }
      validateLanguage(it.languageType)
      val languageGroup = readingBatContent.findLanguage(it.languageType)
      call.respondHtml { languageGroupPage(readingBatContent, it.languageType, languageGroup.challengeGroups) }
    }

    get<Language.Group> {
      validateLanguage(it.languageType)
      val challengeGroup = readingBatContent.findGroup(it.languageType, it.groupName)
      call.respondHtml { challengeGroupPage(challengeGroup) }
    }

    get<Language.Group.Challenge> {
      validateLanguage(it.languageType)
      val challenge = readingBatContent.findChallenge(it.languageType, it.groupName, it.challengeName)
      call.respondHtml { challengePage(challenge) }
    }

    get<PlaygroundRequest> {
      val challenge = readingBatContent.findLanguage(Kotlin).findChallenge(it.groupName, it.challengeName)
      call.respondHtml { playgroundPage(challenge) }
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