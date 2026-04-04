/*
 * Copyright © 2026 Paul Ambrose (pambrose@mac.com)
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

package com.readingbat.server

import com.pambrose.common.response.respondWith
import com.pambrose.common.util.encode
import com.pambrose.common.util.md5Of
import com.readingbat.common.Constants.UNKNOWN
import com.readingbat.common.Endpoints.CHALLENGE_ROOT
import com.readingbat.common.Endpoints.PLAYGROUND_ROOT
import com.readingbat.common.Metrics
import com.readingbat.common.Metrics.Companion.GET
import com.readingbat.common.Metrics.Companion.POST
import com.readingbat.dsl.InvalidRequestException
import com.readingbat.dsl.LanguageType
import com.readingbat.dsl.LanguageType.Java
import com.readingbat.dsl.LanguageType.Kotlin
import com.readingbat.dsl.ReadingBatContent
import com.readingbat.dsl.agentLaunchId
import com.readingbat.pages.ChallengeGroupPage.challengeGroupPage
import com.readingbat.pages.ChallengePage.challengePage
import com.readingbat.pages.LanguageGroupPage.languageGroupPage
import com.readingbat.pages.PlaygroundPage.playgroundPage
import com.readingbat.server.ServerUtils.fetchUser
import com.readingbat.server.routes.AdminRoutes.assignBrowserSession
import io.ktor.http.Parameters
import io.ktor.resources.Resource
import io.ktor.server.resources.get
import io.ktor.server.routing.Routing
import io.ktor.server.routing.RoutingContext
import kotlinx.serialization.Serializable
import io.ktor.server.resources.post as locationsPost

/**
 * Defines type-safe routing for content pages using Ktor's [Resource] annotations.
 *
 * Registers GET and POST handlers for [Language], [Language.Group], and [Language.Group.Challenge]
 * resources, mapping URLs like `/content/java/Warmup-1/hello` to the corresponding page renderers.
 * Also handles playground requests for the Kotlin playground.
 */
object Locations {
  private const val TRUE_STR = true.toString()
  private const val FALSE_STR = false.toString()

  fun Routing.locations(metrics: Metrics, content: () -> ReadingBatContent) {
    get<Language> { languageLoc ->
      metrics.languageGroupRequestCount.labels(agentLaunchId(), GET, languageLoc.languageTypeStr, FALSE_STR).inc()
      language(content(), languageLoc)
    }
    get<Language.Group> { groupLoc ->
      metrics.challengeGroupRequestCount.labels(agentLaunchId(), GET, groupLoc.languageTypeStr, FALSE_STR).inc()
      group(content(), groupLoc)
    }
    get<Language.Group.Challenge> { challengeLoc ->
      metrics.challengeRequestCount.labels(agentLaunchId(), GET, challengeLoc.languageTypeStr, FALSE_STR).inc()
      challenge(content(), challengeLoc)
    }
    get<PlaygroundRequest> { request ->
      metrics.playgroundRequestCount.labels(agentLaunchId(), GET, FALSE_STR).inc()
      playground(content(), request)
    }

    locationsPost<Language> { languageLoc ->
      metrics.languageGroupRequestCount.labels(agentLaunchId(), POST, languageLoc.languageTypeStr, TRUE_STR).inc()
      language(content(), languageLoc)
    }
    locationsPost<Language.Group> { groupLoc ->
      metrics.challengeGroupRequestCount.labels(agentLaunchId(), POST, groupLoc.languageTypeStr, TRUE_STR).inc()
      group(content(), groupLoc)
    }
    locationsPost<Language.Group.Challenge> { challengeLoc ->
      metrics.challengeRequestCount.labels(agentLaunchId(), POST, challengeLoc.languageTypeStr, TRUE_STR).inc()
      challenge(content(), challengeLoc)
    }
    locationsPost<PlaygroundRequest> { request ->
      metrics.playgroundRequestCount.labels(agentLaunchId(), POST, TRUE_STR).inc()
      playground(content(), request)
    }
  }

  private suspend fun RoutingContext.language(
    content: ReadingBatContent,
    language: Language,
  ) =
    respondWith {
      assignBrowserSession()
      content.checkLanguage(language.languageType)
      val user = fetchUser()
      languageGroupPage(content, user, language.languageType)
    }

  private suspend fun RoutingContext.group(
    content: ReadingBatContent,
    groupLoc: Language.Group,
  ) =
    respondWith {
      assignBrowserSession()
      content.checkLanguage(groupLoc.languageType)
      val user = fetchUser()
      challengeGroupPage(content, user, content.findGroup(groupLoc))
    }

  private suspend fun RoutingContext.challenge(
    content: ReadingBatContent,
    challengeLoc: Language.Group.Challenge,
  ) =
    respondWith {
      assignBrowserSession()
      content.checkLanguage(challengeLoc.languageType)
      val user = fetchUser()
      challengePage(content, user, content.findChallenge(challengeLoc))
    }

  private suspend fun RoutingContext.playground(
    content: ReadingBatContent,
    request: PlaygroundRequest,
  ) =
    respondWith {
      assignBrowserSession()
      val user = fetchUser()
      val languageGroup = content.findLanguage(Kotlin).findChallenge(request.groupName, request.challengeName)
      playgroundPage(content, user, languageGroup)
    }
}

/**
 * Type-safe Ktor resource representing a programming language at the URL path `/content/{lname}`.
 *
 * Contains nested [Group] and [Group.Challenge] resources that form the hierarchical
 * URL structure: `/content/{language}/{group}/{challenge}`.
 */
@Resource("$CHALLENGE_ROOT/{lname}")
class Language(val lname: String) {
  val languageName get() = LanguageName(lname)
  val languageType get() = languageName.toLanguageType()
  val languageTypeStr get() = languageType.toString()

  /** Type-safe resource for a challenge group within a language (e.g., `/content/java/Warmup-1`). */
  @Resource("{gname}")
  class Group(val language: Language, val gname: String) {
    val groupName get() = GroupName(gname)
    val languageType get() = language.languageType
    val languageTypeStr get() = languageType.toString()

    /** Type-safe resource for an individual challenge (e.g., `/content/java/Warmup-1/hello`). */
    @Resource("{cname}")
    class Challenge(val group: Group, val cname: String) {
      val challengeName get() = ChallengeName(cname)
      val languageType get() = group.languageType
      val languageTypeStr get() = languageType.toString()
      val groupName get() = group.groupName
    }
  }
}

/** Type-safe resource for Kotlin playground requests at `/playground/{groupName}/{challengeName}`. */
@Resource("$PLAYGROUND_ROOT/{groupName}/{challengeName}")
class PlaygroundRequest(val groupName: String, val challengeName: String)

/** Inline value class wrapping a language name string (e.g., "java", "python", "kotlin"). */
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

/** Inline value class wrapping a challenge group name string (e.g., "Warmup-1"). */
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

/** Inline value class wrapping an individual challenge name string (e.g., "hello"). */
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

/** MD5 hash derived from the combination of language, group, and challenge names. Used as a stable identifier for challenge state in the database. */
class ChallengeMd5(languageName: LanguageName, groupName: GroupName, challengeName: ChallengeName) {
  val value = md5Of(languageName, groupName, challengeName)

  override fun toString() = value
}

/** Represents a single function invocation (i.e., a test case) for a challenge. */
@Serializable
class Invocation(val value: String) {
  override fun toString() = value
}

/** Inline value class wrapping a user's full display name. */
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
