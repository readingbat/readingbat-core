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

import com.github.pambrose.common.response.respondWith
import com.github.pambrose.common.util.encode
import com.github.pambrose.common.util.isNotValidEmail
import com.github.pambrose.common.util.randomId
import com.github.pambrose.common.util.sha256
import com.github.readingbat.common.AuthName.FORM
import com.github.readingbat.common.CommonUtils.md5Of
import com.github.readingbat.common.Constants.UNKNOWN
import com.github.readingbat.common.Endpoints.CHALLENGE_ROOT
import com.github.readingbat.common.Endpoints.PLAYGROUND_ROOT
import com.github.readingbat.common.Metrics
import com.github.readingbat.common.Metrics.Companion.GET
import com.github.readingbat.common.Metrics.Companion.POST
import com.github.readingbat.dsl.InvalidRequestException
import com.github.readingbat.dsl.LanguageType
import com.github.readingbat.dsl.LanguageType.Java
import com.github.readingbat.dsl.LanguageType.Kotlin
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.dsl.agentLaunchId
import com.github.readingbat.pages.ChallengeGroupPage.challengeGroupPage
import com.github.readingbat.pages.ChallengePage.challengePage
import com.github.readingbat.pages.LanguageGroupPage.languageGroupPage
import com.github.readingbat.pages.PlaygroundPage.playgroundPage
import com.github.readingbat.server.AdminRoutes.assignBrowserSession
import com.github.readingbat.server.ServerUtils.fetchUser
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.routing.*

internal object Locations {
  private const val trueStr = true.toString()
  private const val falseStr = false.toString()

  fun Routing.locations(metrics: Metrics, content: () -> ReadingBatContent) {
    get<Language> { languageLoc ->
      metrics.languageGroupRequestCount.labels(agentLaunchId(), GET, languageLoc.languageTypeStr, falseStr).inc()
      language(content.invoke(), languageLoc, false)
    }
    get<Language.Group> { groupLoc ->
      metrics.challengeGroupRequestCount.labels(agentLaunchId(), GET, groupLoc.languageTypeStr, falseStr).inc()
      group(content.invoke(), groupLoc, false)
    }
    get<Language.Group.Challenge> { challengeLoc ->
      metrics.challengeRequestCount.labels(agentLaunchId(), GET, challengeLoc.languageTypeStr, falseStr).inc()
      challenge(content.invoke(), challengeLoc, false)
    }
    get<PlaygroundRequest> { request ->
      metrics.playgroundRequestCount.labels(agentLaunchId(), GET, falseStr).inc()
      playground(content.invoke(), request, false)
    }

    authenticate(FORM) {
      post<Language> { languageLoc ->
        metrics.languageGroupRequestCount.labels(agentLaunchId(), POST, languageLoc.languageTypeStr, trueStr).inc()
        language(content.invoke(), languageLoc, true)
      }
      post<Language.Group> { groupLoc ->
        metrics.challengeGroupRequestCount.labels(agentLaunchId(), POST, groupLoc.languageTypeStr, trueStr).inc()
        group(content.invoke(), groupLoc, true)
      }
      post<Language.Group.Challenge> { challengeLoc ->
        metrics.challengeRequestCount.labels(agentLaunchId(), POST, challengeLoc.languageTypeStr, trueStr).inc()
        challenge(content.invoke(), challengeLoc, true)
      }
      post<PlaygroundRequest> { request ->
        metrics.playgroundRequestCount.labels(agentLaunchId(), POST, trueStr).inc()
        playground(content.invoke(), request, true)
      }
    }
  }

  private suspend fun PipelineCall.language(content: ReadingBatContent,
                                            language: Language,
                                            loginAttempt: Boolean) =
    respondWith {
      assignBrowserSession()
      content.checkLanguage(language.languageType)
      val user = fetchUser(loginAttempt)
      languageGroupPage(content, user, language.languageType, loginAttempt)
    }

  private suspend fun PipelineCall.group(content: ReadingBatContent,
                                         groupLoc: Language.Group,
                                         loginAttempt: Boolean) =
    respondWith {
      assignBrowserSession()
      content.checkLanguage(groupLoc.languageType)
      val user = fetchUser(loginAttempt)
      challengeGroupPage(content, user, content.findGroup(groupLoc), loginAttempt)

    }

  private suspend fun PipelineCall.challenge(content: ReadingBatContent,
                                             challengeLoc: Language.Group.Challenge,
                                             loginAttempt: Boolean) =
    respondWith {
      assignBrowserSession()
      content.checkLanguage(challengeLoc.languageType)
      val user = fetchUser(loginAttempt)
      challengePage(content, user, content.findChallenge(challengeLoc), loginAttempt)
    }

  private suspend fun PipelineCall.playground(content: ReadingBatContent,
                                              request: PlaygroundRequest,
                                              loginAttempt: Boolean) =
    respondWith {
      assignBrowserSession()
      val user = fetchUser(loginAttempt)
      val languageGroup = content.findLanguage(Kotlin).findChallenge(request.groupName, request.challengeName)
      playgroundPage(content, user, languageGroup, loginAttempt)
    }
}

@Location("$CHALLENGE_ROOT/{lname}")
internal data class Language(val lname: String) {
  val languageName = LanguageName(lname)
  val languageType get() = languageName.toLanguageType()
  val languageTypeStr get() = languageType.toString()

  @Location("/{gname}")
  data class Group(val language: Language, val gname: String) {
    val groupName = GroupName(gname)
    val languageType get() = language.languageType
    val languageTypeStr get() = languageType.toString()

    @Location("/{cname}")
    data class Challenge(val group: Group, val cname: String) {
      val challengeName = ChallengeName(cname)
      val languageType get() = group.languageType
      val languageTypeStr get() = languageType.toString()
      val groupName get() = group.groupName
    }
  }
}

@Location("$PLAYGROUND_ROOT/{groupName}/{challengeName}")
internal class PlaygroundRequest(val groupName: String, val challengeName: String)

inline class LanguageName(val value: String) {
  val isJvm get() = this in jmvLanguages

  fun toLanguageType() =
    try {
      LanguageType.values().first { it.name.equals(value, ignoreCase = true) }
    } catch (e: NoSuchElementException) {
      throw InvalidRequestException("Invalid language: $this")
    }

  internal fun isValid() = try {
    toLanguageType(); true
  } catch (e: InvalidRequestException) {
    false
  }

  internal fun isNotValid() = !isValid()

  internal fun isDefined(content: ReadingBatContent) = isValid() && content.hasLanguage(toLanguageType())

  override fun toString() = value

  companion object {
    internal val EMPTY_LANGUAGE = LanguageName("")
    private val jmvLanguages by lazy { listOf(Java.languageName, Kotlin.languageName) }

    internal fun Parameters.getLanguageName(name: String) = this[name]?.let { LanguageName(it) } ?: EMPTY_LANGUAGE
  }
}

inline class GroupName(val value: String) {

  internal fun isValid() = this != EMPTY_GROUP

  internal fun isNotValid() = !isValid()

  internal fun encode() = value.encode()

  internal fun isDefined(content: ReadingBatContent, languageName: LanguageName) =
    languageName.isDefined(content) && isValid() && content.findLanguage(languageName).hasGroup(value)

  override fun toString() = value

  companion object {
    internal val EMPTY_GROUP = GroupName("")
    internal fun Parameters.getGroupName(name: String) = this[name]?.let { GroupName(it) } ?: EMPTY_GROUP
  }
}

inline class ChallengeName(val value: String) {
  internal fun isValid() = this != EMPTY_CHALLENGE

  internal fun isNotValid() = !isValid()

  internal fun encode() = value.encode()

  override fun toString() = value

  companion object {
    private val EMPTY_CHALLENGE = ChallengeName("")
    internal fun Parameters.getChallengeName(name: String) = this[name]?.let { ChallengeName(it) } ?: EMPTY_CHALLENGE
  }
}

class ChallengeMd5(languageName: LanguageName, groupName: GroupName, challengeName: ChallengeName) {
  val value = md5Of(languageName, groupName, challengeName)
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
    val UNKNOWN_FULLNAME = FullName(UNKNOWN)
    fun Parameters.getFullName(name: String) = this[name]?.let { FullName(it) } ?: EMPTY_FULLNAME
  }
}

inline class Password(val value: String) {
  val length get() = value.length
  fun isBlank() = value.isBlank()
  fun sha256(salt: String) = value.sha256(salt)

  override fun toString() = value

  companion object {
    private val EMPTY_PASSWORD = Password("")
    fun Parameters.getPassword(name: String) = this[name]?.let { Password(it) } ?: EMPTY_PASSWORD
  }
}

inline class Email(val value: String) {
  fun isBlank() = value.isBlank()
  fun isNotBlank() = value.isNotBlank()
  fun isNotValidEmail() = value.isNotValidEmail()

  override fun toString() = value

  companion object {
    val EMPTY_EMAIL = Email("")
    val UNKNOWN_EMAIL = Email(UNKNOWN)
    fun Parameters.getEmail(name: String) = this[name]?.let { Email(it) } ?: EMPTY_EMAIL
  }
}

inline class ResetId(val value: String) {
  fun isBlank() = value.isBlank()
  fun isNotBlank() = value.isNotBlank()

  override fun toString() = value

  companion object {
    val EMPTY_RESET_ID = ResetId("")
    fun newResetId() = ResetId(randomId(15))
    fun Parameters.getResetId(name: String) = this[name]?.let { ResetId(it) } ?: EMPTY_RESET_ID
  }
}