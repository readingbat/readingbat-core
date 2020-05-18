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

import com.github.readingbat.PipelineCall
import com.github.readingbat.assignPrincipal
import com.github.readingbat.dsl.LanguageType.Companion.toLanguageType
import com.github.readingbat.dsl.LanguageType.Kotlin
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.AuthName.FORM
import com.github.readingbat.misc.BrowserSession
import com.github.readingbat.misc.Constants.challengeRoot
import com.github.readingbat.misc.Constants.playground
import com.github.readingbat.pages.challengeGroupPage
import com.github.readingbat.pages.challengePage
import com.github.readingbat.pages.languageGroupPage
import com.github.readingbat.pages.playgroundPage
import com.github.readingbat.respondWith
import com.github.readingbat.retrievePrincipal
import io.ktor.application.call
import io.ktor.auth.UserIdPrincipal
import io.ktor.auth.authenticate
import io.ktor.locations.Location
import io.ktor.locations.get
import io.ktor.locations.post
import io.ktor.routing.Routing
import io.ktor.sessions.get
import io.ktor.sessions.sessions

internal fun Routing.locations(content: ReadingBatContent) {
  get<Language> { language -> language(retrievePrincipal(), false, content, language) }
  get<Language.Group> { groupChallenge -> group(retrievePrincipal(), false, content, groupChallenge) }
  get<Language.Group.Challenge> { gc -> challenge(retrievePrincipal(), false, content, gc) }
  get<PlaygroundRequest> { request -> playground(retrievePrincipal(), false, content, request) }

  authenticate(FORM) {
    post<Language> { language -> language(assignPrincipal(), true, content, language) }
    post<Language.Group> { languageGroup -> group(assignPrincipal(), true, content, languageGroup) }
    post<Language.Group.Challenge> { gc -> challenge(assignPrincipal(), true, content, gc) }
    post<PlaygroundRequest> { request -> playground(assignPrincipal(), true, content, request) }
  }
}

suspend fun PipelineCall.language(principal: UserIdPrincipal?,
                                  loginAttempt: Boolean,
                                  content: ReadingBatContent,
                                  lang: Language) =
  respondWith {
    content.checkLanguage(lang.languageType)
    val languageGroup = content.findLanguage(lang.languageType)
    languageGroupPage(principal,
                      loginAttempt,
                      content,
                      lang.languageType,
                      languageGroup.challengeGroups)
  }

suspend fun PipelineCall.group(principal: UserIdPrincipal?,
                               loginAttempt: Boolean,
                               content: ReadingBatContent,
                               group: Language.Group) =
  respondWith {
    content.checkLanguage(group.languageType)
    val challengeGroup = content.findGroup(group.languageType, group.groupName)
    challengeGroupPage(principal, loginAttempt, content, challengeGroup)
  }

suspend fun PipelineCall.challenge(principal: UserIdPrincipal?,
                                   loginAttempt: Boolean,
                                   content: ReadingBatContent,
                                   gc: Language.Group.Challenge) =
  respondWith {
    registerBrowserSession()
    content.checkLanguage(gc.languageType)
    val challenge = content.findChallenge(gc.languageType, gc.groupName, gc.challengeName)
    val clientSession = call.sessions.get<BrowserSession>()
    challengePage(principal, loginAttempt, content, challenge, clientSession)
  }

suspend fun PipelineCall.playground(principal: UserIdPrincipal?,
                                    loginAttempt: Boolean,
                                    content: ReadingBatContent,
                                    request: PlaygroundRequest) =
  respondWith {
    val challenge = content.findLanguage(Kotlin).findChallenge(request.groupName, request.challengeName)
    playgroundPage(principal, loginAttempt, content, challenge)
  }


@Location("/$challengeRoot/{language}")
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
class PlaygroundRequest(val groupName: String, val challengeName: String)