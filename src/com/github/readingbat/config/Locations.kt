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
import com.github.readingbat.misc.Constants.CHALLENGE_ROOT
import com.github.readingbat.misc.Constants.PLAYGROUND_ROOT
import com.github.readingbat.misc.UserPrincipal
import com.github.readingbat.pages.challengeGroupPage
import com.github.readingbat.pages.challengePage
import com.github.readingbat.pages.languageGroupPage
import com.github.readingbat.pages.playgroundPage
import com.github.readingbat.respondWith
import com.github.readingbat.retrievePrincipal
import io.ktor.application.call
import io.ktor.auth.authenticate
import io.ktor.locations.Location
import io.ktor.locations.get
import io.ktor.locations.post
import io.ktor.routing.Routing
import io.ktor.sessions.get
import io.ktor.sessions.sessions

internal fun Routing.locations(content: ReadingBatContent) {
  get<Language> { language -> language(false, content, language) }
  get<Language.Group> { groupChallenge -> group(false, content, groupChallenge) }
  get<Language.Group.Challenge> { gc -> challenge(false, content, gc) }
  get<PlaygroundRequest> { request -> playground(false, content, request) }

  authenticate(FORM) {
    post<Language> { language -> language(true, content, language) }
    post<Language.Group> { languageGroup -> group(true, content, languageGroup) }
    post<Language.Group.Challenge> { gc -> challenge(true, content, gc) }
    post<PlaygroundRequest> { request -> playground(true, content, request) }
  }
}

fun PipelineCall.fetchPrincipal(loginAttempt: Boolean): UserPrincipal? =
  if (loginAttempt) assignPrincipal() else retrievePrincipal()

suspend fun PipelineCall.language(loginAttempt: Boolean,
                                  content: ReadingBatContent,
                                  lang: Language) =
  respondWith {
    content.checkLanguage(lang.languageType)
    val languageGroup = content.findLanguage(lang.languageType)
    val browserSession = call.sessions.get<BrowserSession>()
    languageGroupPage(fetchPrincipal(loginAttempt),
                      browserSession,
                      loginAttempt,
                      content,
                      lang.languageType,
                      languageGroup.challengeGroups)
  }

suspend fun PipelineCall.group(loginAttempt: Boolean,
                               content: ReadingBatContent,
                               group: Language.Group) =
  respondWith {
    content.checkLanguage(group.languageType)
    val challengeGroup = content.findGroup(group.languageType, group.groupName)
    val browserSession = call.sessions.get<BrowserSession>()
    challengeGroupPage(fetchPrincipal(loginAttempt),
                       browserSession,
                       loginAttempt,
                       content,
                       challengeGroup)
  }

suspend fun PipelineCall.challenge(loginAttempt: Boolean,
                                   content: ReadingBatContent,
                                   gc: Language.Group.Challenge) =
  respondWith {
    registerBrowserSession()
    content.checkLanguage(gc.languageType)
    val challenge = content.findChallenge(gc.languageType, gc.groupName, gc.challengeName)
    val browserSession = call.sessions.get<BrowserSession>()
    challengePage(fetchPrincipal(loginAttempt),
                  browserSession,
                  loginAttempt,
                  content,
                  challenge)
  }

suspend fun PipelineCall.playground(loginAttempt: Boolean,
                                    content: ReadingBatContent,
                                    request: PlaygroundRequest) =
  respondWith {
    val challenge = content.findLanguage(Kotlin).findChallenge(request.groupName, request.challengeName)
    playgroundPage(fetchPrincipal(loginAttempt),
                   loginAttempt,
                   content,
                   challenge)
  }


@Location("$CHALLENGE_ROOT/{language}")
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

@Location("$PLAYGROUND_ROOT/{groupName}/{challengeName}")
class PlaygroundRequest(val groupName: String, val challengeName: String)