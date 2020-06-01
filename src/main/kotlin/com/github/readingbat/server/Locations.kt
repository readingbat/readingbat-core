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

package com.github.readingbat.server

import com.github.pambrose.common.redis.RedisUtils.withRedisPool
import com.github.pambrose.common.response.respondWith
import com.github.pambrose.common.util.isNotValidEmail
import com.github.pambrose.common.util.randomId
import com.github.pambrose.common.util.sha256
import com.github.readingbat.dsl.LanguageType.Companion.toLanguageType
import com.github.readingbat.dsl.LanguageType.Kotlin
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.AuthName.FORM
import com.github.readingbat.misc.Constants.CHALLENGE_ROOT
import com.github.readingbat.misc.Constants.PLAYGROUND_ROOT
import com.github.readingbat.misc.KeyConstants
import com.github.readingbat.pages.ChallengeGroupPage.challengeGroupPage
import com.github.readingbat.pages.ChallengePage.challengePage
import com.github.readingbat.pages.LanguageGroupPage.languageGroupPage
import com.github.readingbat.pages.PlaygroundPage.playgroundPage
import com.github.readingbat.server.AdminRoutes.registerBrowserSession
import io.ktor.auth.authenticate
import io.ktor.http.Parameters
import io.ktor.locations.Location
import io.ktor.locations.get
import io.ktor.locations.post
import io.ktor.routing.Routing

internal object Locations {
  fun Routing.locations(content: ReadingBatContent) {
    get<Language> { languageLoc -> language(content, languageLoc, false) }
    get<Language.Group> { groupLoc -> group(content, groupLoc, false) }
    get<Language.Group.Challenge> { challengeLoc -> challenge(content, challengeLoc, false) }
    get<PlaygroundRequest> { request -> playground(content, request, false) }

    authenticate(FORM) {
      post<Language> { languageLoc -> language(content, languageLoc, true) }
      post<Language.Group> { groupLoc -> group(content, groupLoc, true) }
      post<Language.Group.Challenge> { challengeLoc -> challenge(content, challengeLoc, true) }
      post<PlaygroundRequest> { request -> playground(content, request, true) }
    }
  }

  private suspend fun PipelineCall.language(content: ReadingBatContent,
                                            language: Language,
                                            loginAttempt: Boolean) =
    respondWith {
      content.checkLanguage(language.languageType)
      withRedisPool { redis ->
        languageGroupPage(content, redis, language.languageType, loginAttempt)
      }
    }

  private suspend fun PipelineCall.group(content: ReadingBatContent,
                                         groupLoc: Language.Group,
                                         loginAttempt: Boolean) =
    respondWith {
      content.checkLanguage(groupLoc.languageType)
      withRedisPool { redis ->
        challengeGroupPage(content, redis, content.findGroup(groupLoc), loginAttempt)
      }
    }

  private suspend fun PipelineCall.challenge(content: ReadingBatContent,
                                             challengeLoc: Language.Group.Challenge,
                                             loginAttempt: Boolean) =
    respondWith {
      registerBrowserSession()
      content.checkLanguage(challengeLoc.languageType)
      withRedisPool { redis ->
        challengePage(content, redis, content.findChallenge(challengeLoc), loginAttempt)
      }
    }

  private suspend fun PipelineCall.playground(content: ReadingBatContent,
                                              request: PlaygroundRequest,
                                              loginAttempt: Boolean) =
    respondWith {
      withRedisPool { redis ->
        playgroundPage(content,
                       redis,
                       content.findLanguage(Kotlin).findChallenge(request.groupName, request.challengeName),
                       loginAttempt)
      }
    }
}

@Location("$CHALLENGE_ROOT/{lname}")
internal data class Language(val lname: String) {
  val languageType get() = languageName.toLanguageType()
  val languageName = LanguageName(lname)

  @Location("/{gname}")
  data class Group(val language: Language, val gname: String) {
    val languageType get() = language.languageType
    val groupName = GroupName(gname)

    @Location("/{cname}")
    data class Challenge(val group: Group, val cname: String) {
      val languageType get() = group.languageType
      val groupName get() = group.groupName
      val challengeName = ChallengeName(cname)
    }
  }
}

@Location("$PLAYGROUND_ROOT/{groupName}/{challengeName}")
internal class PlaygroundRequest(val groupName: String, val challengeName: String)

inline class LanguageName(val value: String) {
  override fun toString() = value
}

inline class GroupName(val value: String) {
  override fun toString() = value
}

inline class ChallengeName(val value: String) {
  override fun toString() = value
}

inline class Invocation(val value: String) {
  override fun toString() = value
}

inline class FullName(val value: String) {
  fun isBlank() = value.isBlank()

  override fun toString() = value

  companion object {
    val EMPTY_FULLNAME = FullName("")
    fun Parameters.getFullName(name: String) = this[name]?.let { FullName(it) } ?: EMPTY_FULLNAME
  }
}

inline class Password(val value: String) {
  fun isBlank() = value.isBlank()
  fun sha256(salt: String) = value.sha256(salt)
  val length get() = value.length

  override fun toString() = value

  companion object {
    val EMPTY_PASSWORD = Password("")
    fun Parameters.getPassword(name: String) = this[name]?.let { Password(it) } ?: EMPTY_PASSWORD
  }
}

inline class Email(val value: String) {
  fun isBlank() = value.isBlank()
  fun isNotBlank() = value.isNotBlank()
  fun isNotValidEmail() = value.isNotValidEmail()
  val userEmailKey get() = listOf(KeyConstants.USER_EMAIL_KEY, value).joinToString(KeyConstants.KEY_SEP)

  override fun toString() = value

  companion object {
    val EMPTY_EMAIL = Email("")
    fun Parameters.getEmail(name: String) = this[name]?.let { Email(it) } ?: EMPTY_EMAIL
  }
}

inline class ResetId(val value: String) {
  fun isBlank() = value.isBlank()
  fun isNotBlank() = value.isNotBlank()

  override fun toString() = value

  companion object {
    fun newResetId() = ResetId(randomId(15))

    val EMPTY_RESET_ID = ResetId("")
    fun Parameters.getResetId(name: String) = this[name]?.let { ResetId(it) } ?: EMPTY_RESET_ID
  }
}

