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

import com.github.readingbat.dsl.LanguageType.Companion.toLanguageType
import com.github.readingbat.dsl.LanguageType.Kotlin
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.AuthName.FORM
import com.github.readingbat.misc.Constants.challengeRoot
import com.github.readingbat.misc.Constants.playground
import com.github.readingbat.pages.challengeGroupPage
import com.github.readingbat.pages.challengePage
import com.github.readingbat.pages.languageGroupPage
import com.github.readingbat.pages.playgroundPage
import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.auth.UserIdPrincipal
import io.ktor.auth.authenticate
import io.ktor.auth.principal
import io.ktor.locations.Location
import io.ktor.locations.get
import io.ktor.locations.post
import io.ktor.routing.routing
import io.ktor.sessions.get
import io.ktor.sessions.sessions
import io.ktor.sessions.set
import io.ktor.util.pipeline.PipelineContext

typealias PipelineCall = PipelineContext<Unit, ApplicationCall>

internal fun Application.locations(readingBatContent: ReadingBatContent) {
  routing {

    fun PipelineCall.retrievePrincipal() =
      call.sessions.get<UserIdPrincipal>()

    fun PipelineCall.assignPrincipal() =
      call.principal<UserIdPrincipal>().apply { if (this != null) call.sessions.set(this) }  // Set the cookie

    suspend fun PipelineCall.langAction(lang: Language, principal: UserIdPrincipal?, loginAttempted: Boolean) =
      respondWith {
        readingBatContent.checkLanguage(lang.languageType)
        val languageGroup = readingBatContent.findLanguage(lang.languageType)
        languageGroupPage(principal,
                          loginAttempted,
                          readingBatContent,
                          lang.languageType,
                          languageGroup.challengeGroups)
      }

    get<Language> { language -> langAction(language, retrievePrincipal(), false) }

    authenticate(FORM) {
      post<Language> { language -> langAction(language, assignPrincipal(), true) }
    }

    suspend fun PipelineCall.groupAction(group: Language.Group, principal: UserIdPrincipal?) =
      respondWith {
        readingBatContent.checkLanguage(group.languageType)
        val challengeGroup = readingBatContent.findGroup(group.languageType, group.groupName)
        challengeGroupPage(principal, readingBatContent, challengeGroup)
      }

    get<Language.Group> { groupChallenge -> groupAction(groupChallenge, retrievePrincipal()) }

    authenticate(FORM) {
      post<Language.Group> { languageGroup -> groupAction(languageGroup, assignPrincipal()) }
    }

    suspend fun PipelineCall.challengeAction(gc: Language.Group.Challenge, principal: UserIdPrincipal?) =
      respondWith {
        readingBatContent.checkLanguage(gc.languageType)
        val challenge = readingBatContent.findChallenge(gc.languageType, gc.groupName, gc.challengeName)
        val clientSession = call.sessions.get<ClientSession>()
        challengePage(principal, readingBatContent, challenge, clientSession)
      }

    get<Language.Group.Challenge> { groupChallenge -> challengeAction(groupChallenge, retrievePrincipal()) }

    authenticate(FORM) {
      post<Language.Group.Challenge> { groupChallenge -> challengeAction(groupChallenge, assignPrincipal()) }
    }

    suspend fun PipelineCall.playground(request: PlaygroundRequest, principal: UserIdPrincipal?) =
      respondWith {
        val challenge = readingBatContent.findLanguage(Kotlin).findChallenge(request.groupName, request.challengeName)
        playgroundPage(principal, readingBatContent, challenge)
      }

    get<PlaygroundRequest> { request -> playground(request, retrievePrincipal()) }

    authenticate(FORM) {
      post<PlaygroundRequest> { request -> playground(request, assignPrincipal()) }
    }
  }
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
internal class PlaygroundRequest(val groupName: String, val challengeName: String)