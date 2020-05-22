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

import com.github.pambrose.common.response.respondWith
import com.github.readingbat.PipelineCall
import com.github.readingbat.assignPrincipal
import com.github.readingbat.dsl.LanguageType.Companion.toLanguageType
import com.github.readingbat.dsl.LanguageType.Kotlin
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.AuthName.FORM
import com.github.readingbat.misc.Constants.CHALLENGE_ROOT
import com.github.readingbat.misc.Constants.PLAYGROUND_ROOT
import com.github.readingbat.misc.UserPrincipal
import com.github.readingbat.pages.challengeGroupPage
import com.github.readingbat.pages.challengePage
import com.github.readingbat.pages.languageGroupPage
import com.github.readingbat.pages.playgroundPage
import com.github.readingbat.retrievePrincipal
import io.ktor.auth.authenticate
import io.ktor.locations.Location
import io.ktor.locations.get
import io.ktor.locations.post
import io.ktor.routing.Routing

internal fun Routing.locations(content: ReadingBatContent) {
  get<Language> { language -> language(content, language, false) }
  get<Language.Group> { group -> group(content, group, false) }
  get<Language.Group.Challenge> { challenge -> challenge(content, challenge, false) }
  get<PlaygroundRequest> { request -> playground(content, request, false) }

  authenticate(FORM) {
    post<Language> { language -> language(content, language, true) }
    post<Language.Group> { group -> group(content, group, true) }
    post<Language.Group.Challenge> { challenge -> challenge(content, challenge, true) }
    post<PlaygroundRequest> { request -> playground(content, request, true) }
  }
}

internal fun PipelineCall.fetchPrincipal(loginAttempt: Boolean): UserPrincipal? =
  if (loginAttempt) assignPrincipal() else retrievePrincipal()

internal suspend fun PipelineCall.language(content: ReadingBatContent,
                                           language: Language,
                                           loginAttempt: Boolean) =
  respondWith {
    content.checkLanguage(language.languageType)
    languageGroupPage(content, language.languageType, loginAttempt)
  }

internal suspend fun PipelineCall.group(content: ReadingBatContent,
                                        group: Language.Group,
                                        loginAttempt: Boolean) =
  respondWith {
    content.checkLanguage(group.languageType)
    challengeGroupPage(content, content.findGroup(group.languageType, group.groupName), loginAttempt)
  }

internal suspend fun PipelineCall.challenge(content: ReadingBatContent,
                                            challenge: Language.Group.Challenge,
                                            loginAttempt: Boolean) =
  respondWith {
    registerBrowserSession()
    content.checkLanguage(challenge.languageType)
    challengePage(content,
                  content.findChallenge(challenge.languageType, challenge.groupName, challenge.challengeName),
                  loginAttempt)
  }

internal suspend fun PipelineCall.playground(content: ReadingBatContent,
                                             request: PlaygroundRequest,
                                             loginAttempt: Boolean) =
  respondWith {
    playgroundPage(content,
                   content.findLanguage(Kotlin).findChallenge(request.groupName, request.challengeName),
                   loginAttempt)
  }

@Location("$CHALLENGE_ROOT/{language}")
internal data class Language(val language: String) {
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

@Location("$PLAYGROUND_ROOT/{groupName}/{challengeName}")
internal class PlaygroundRequest(val groupName: String, val challengeName: String)