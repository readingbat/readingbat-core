/*
 * Copyright © 2023 Paul Ambrose (pambrose@mac.com)
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
import com.github.pambrose.common.util.md5Of
import com.github.pambrose.common.util.randomId
import com.github.pambrose.common.util.sha256
import com.github.readingbat.common.AuthName.FORM
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
import com.github.readingbat.server.ServerUtils.fetchUser
import com.github.readingbat.server.routes.AdminRoutes.assignBrowserSession
import io.ktor.http.Parameters
import io.ktor.resources.Resource
import io.ktor.server.auth.authenticate
import io.ktor.server.resources.get
import io.ktor.server.routing.Routing
import io.ktor.server.routing.RoutingContext
import kotlinx.serialization.Serializable
import io.ktor.server.resources.post as locationsPost

object Locations {
  private const val TRUE_STR = true.toString()
  private const val FALSE_STR = false.toString()

  fun Routing.locations(metrics: Metrics, content: () -> ReadingBatContent) {
    get<Language> { languageLoc ->
      metrics.languageGroupRequestCount.labels(agentLaunchId(), GET, languageLoc.languageTypeStr, FALSE_STR).inc()
      language(content.invoke(), languageLoc, false)
    }
    get<Language.Group> { groupLoc ->
      metrics.challengeGroupRequestCount.labels(agentLaunchId(), GET, groupLoc.languageTypeStr, FALSE_STR).inc()
      group(content.invoke(), groupLoc, false)
    }
    get<Language.Group.Challenge> { challengeLoc ->
      metrics.challengeRequestCount.labels(agentLaunchId(), GET, challengeLoc.languageTypeStr, FALSE_STR).inc()
      challenge(content.invoke(), challengeLoc, false)
    }
    get<PlaygroundRequest> { request ->
      metrics.playgroundRequestCount.labels(agentLaunchId(), GET, FALSE_STR).inc()
      playground(content.invoke(), request, false)
    }

    authenticate(FORM) {
      locationsPost<Language> { languageLoc ->
        metrics.languageGroupRequestCount.labels(agentLaunchId(), POST, languageLoc.languageTypeStr, TRUE_STR).inc()
        language(content.invoke(), languageLoc, true)
      }
      locationsPost<Language.Group> { groupLoc ->
        metrics.challengeGroupRequestCount.labels(agentLaunchId(), POST, groupLoc.languageTypeStr, TRUE_STR).inc()
        group(content.invoke(), groupLoc, true)
      }
      locationsPost<Language.Group.Challenge> { challengeLoc ->
        metrics.challengeRequestCount.labels(agentLaunchId(), POST, challengeLoc.languageTypeStr, TRUE_STR).inc()
        challenge(content.invoke(), challengeLoc, true)
      }
      locationsPost<PlaygroundRequest> { request ->
        metrics.playgroundRequestCount.labels(agentLaunchId(), POST, TRUE_STR).inc()
        playground(content.invoke(), request, true)
      }
    }
  }

  private suspend fun RoutingContext.language(
    content: ReadingBatContent,
    language: Language,
    loginAttempt: Boolean,
  ) =
    respondWith {
      assignBrowserSession()
      content.checkLanguage(language.languageType)
      val user = fetchUser(loginAttempt)
      languageGroupPage(content, user, language.languageType, loginAttempt)
    }

  private suspend fun RoutingContext.group(
    content: ReadingBatContent,
    groupLoc: Language.Group,
    loginAttempt: Boolean,
  ) =
    respondWith {
      assignBrowserSession()
      content.checkLanguage(groupLoc.languageType)
      val user = fetchUser(loginAttempt)
      challengeGroupPage(content, user, content.findGroup(groupLoc), loginAttempt)
    }

  private suspend fun RoutingContext.challenge(
    content: ReadingBatContent,
    challengeLoc: Language.Group.Challenge,
    loginAttempt: Boolean,
  ) =
    respondWith {
      assignBrowserSession()
      content.checkLanguage(challengeLoc.languageType)
      val user = fetchUser(loginAttempt)
      challengePage(content, user, content.findChallenge(challengeLoc), loginAttempt)
    }

  private suspend fun RoutingContext.playground(
    content: ReadingBatContent,
    request: PlaygroundRequest,
    loginAttempt: Boolean,
  ) =
    respondWith {
      assignBrowserSession()
      val user = fetchUser(loginAttempt)
      val languageGroup = content.findLanguage(Kotlin).findChallenge(request.groupName, request.challengeName)
      playgroundPage(content, user, languageGroup, loginAttempt)
    }
}

@Resource("$CHALLENGE_ROOT/{lname}")
class Language(val lname: String) {
  val languageName get() = LanguageName(lname)
  val languageType get() = languageName.toLanguageType()
  val languageTypeStr get() = languageType.toString()

  @Resource("{gname}")
  class Group(val language: Language, val gname: String) {
    val groupName get() = GroupName(gname)
    val languageType get() = language.languageType
    val languageTypeStr get() = languageType.toString()

    @Resource("{cname}")
    class Challenge(val group: Group, val cname: String) {
      val challengeName get() = ChallengeName(cname)
      val languageType get() = group.languageType
      val languageTypeStr get() = languageType.toString()
      val groupName get() = group.groupName
    }
  }
}

@Resource("$PLAYGROUND_ROOT/{groupName}/{challengeName}")
class PlaygroundRequest(val groupName: String, val challengeName: String)

@JvmInline
value class LanguageName(val value: String) {
  val isJvm get() = value in listOf("kotlin", "java") // jmvLanguages

  fun toLanguageType() =
    try {
      LanguageType.entries.first { it.name.equals(value, ignoreCase = true) }
    } catch (e: NoSuchElementException) {
      throw InvalidRequestException("Invalid language: $this")
    }

  internal fun isValid() =
    try {
      toLanguageType()
      true
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

@JvmInline
value class GroupName(val value: String) {
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

@JvmInline
value class ChallengeName(val value: String) {
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

@Serializable
class Invocation(val value: String) {
  override fun toString() = value
}

@JvmInline
value class FullName(val value: String) {
  fun isBlank() = value.isBlank()

  override fun toString() = value

  companion object {
    val EMPTY_FULLNAME = FullName("")
    val UNKNOWN_FULLNAME = FullName(UNKNOWN)

    fun Parameters.getFullName(name: String) = this[name]?.let { FullName(it) } ?: EMPTY_FULLNAME
  }
}

@JvmInline
value class Password(val value: String) {
  val length get() = value.length

  fun isBlank() = value.isBlank()

  fun sha256(salt: String) = value.sha256(salt)

  override fun toString() = value

  companion object {
    private val EMPTY_PASSWORD = Password("")

    fun Parameters.getPassword(name: String) = this[name]?.let { Password(it) } ?: EMPTY_PASSWORD
  }
}

@JvmInline
value class Email(val value: String) {
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

@JvmInline
value class ResetId(val value: String) {
  fun isBlank() = value.isBlank()

  fun isNotBlank() = value.isNotBlank()

  override fun toString() = value

  companion object {
    val EMPTY_RESET_ID = ResetId("")

    fun newResetId() = ResetId(randomId(15))

    fun Parameters.getResetId(name: String) = this[name]?.let { ResetId(it) } ?: EMPTY_RESET_ID
  }
}
