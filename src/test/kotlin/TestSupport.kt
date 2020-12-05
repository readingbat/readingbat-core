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

package com.github.readingbat

import com.github.readingbat.common.Constants.CHALLENGE_SRC
import com.github.readingbat.common.Constants.GROUP_SRC
import com.github.readingbat.common.Constants.LANG_SRC
import com.github.readingbat.common.Constants.RESP
import com.github.readingbat.common.Endpoints
import com.github.readingbat.common.Endpoints.CHECK_ANSWERS_ENDPOINT
import com.github.readingbat.common.FunctionInfo
import com.github.readingbat.dsl.Challenge
import com.github.readingbat.dsl.ChallengeGroup
import com.github.readingbat.dsl.LanguageGroup
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.posts.AnswerStatus.Companion.toAnswerStatus
import com.github.readingbat.server.GeoInfo.Companion.gson
import com.github.readingbat.server.Installs.installs
import com.github.readingbat.server.Locations.locations
import com.github.readingbat.server.ReadingBatServer.metrics
import com.github.readingbat.server.routes.AdminRoutes.adminRoutes
import com.github.readingbat.server.routes.sysAdminRoutes
import com.github.readingbat.server.routes.userRoutes
import com.github.readingbat.server.ws.WsCommon.wsRoutes
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.ContentType.Application.FormUrlEncoded
import io.ktor.http.HttpHeaders.ContentType
import io.ktor.http.content.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking

object TestSupport {
  internal fun ReadingBatContent.pythonGroup(name: String) = python.get(name)
  internal fun ReadingBatContent.javaGroup(name: String) = java.get(name)
  internal fun ReadingBatContent.kotlinGroup(name: String) = kotlin.get(name)

  internal fun <T : Challenge> ChallengeGroup<T>.challengeByName(name: String) =
    challenges.firstOrNull { it.challengeName.value == name } ?: error("Missing challenge $name")

  internal fun <T : Challenge> ChallengeGroup<T>.functionInfo(name: String) =
    challengeByName(name).functionInfo()

  internal fun FunctionInfo.checkUserResponse(index: Int, userResponse: String) =
    runBlocking {
      checkResponse(index, userResponse)
    }

  fun TestApplicationEngine.getUrl(uri: String, block: TestApplicationCall.() -> Unit) =
    handleRequest(HttpMethod.Get, uri).apply { block() }

  fun TestApplicationEngine.postUrl(uri: String, block: TestApplicationRequest.() -> Unit) =
    handleRequest(HttpMethod.Post, uri, block)

  internal fun <T : Challenge> TestApplicationEngine.testAllChallenges(lang: LanguageGroup<T>, answer: String) =
    buildList {
      lang.challengeGroups.forEach { challengeGroup ->
        challengeGroup.challenges.forEach { challenge ->
          val content =
            postUrl(CHECK_ANSWERS_ENDPOINT) {
              addHeader(ContentType, FormUrlEncoded.toString())
              val data =
                mutableListOf(LANG_SRC to lang.languageName.value,
                              GROUP_SRC to challengeGroup.groupName.value,
                              CHALLENGE_SRC to challenge.challengeName.value)
              challenge.functionInfo().invocations.indices.forEach { data += "$RESP$it" to answer }
              setBody(data.formUrlEncode())
            }.response.content

          gson.fromJson(content, List::class.java)
            .map { v ->
              (v as List<Any?>).let {
                (it[0] as Double).toInt().toAnswerStatus() to (it[1] as String)
              }
            }
            .forEach { this += it }
        }
      }
    }

  fun Application.module(testing: Boolean = false, testContent: ReadingBatContent) {
    installs(false)

    routing {
      adminRoutes(metrics)
      locations(metrics) { testContent }
      userRoutes(metrics) { testContent }
      sysAdminRoutes(metrics) { s: String -> }
      wsRoutes(metrics) { testContent }
      static(Endpoints.STATIC_ROOT) { resources("static") }
    }
  }
}