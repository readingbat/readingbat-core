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
import com.github.readingbat.common.Property
import com.github.readingbat.dsl.Challenge
import com.github.readingbat.dsl.ChallengeGroup
import com.github.readingbat.dsl.LanguageGroup
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.posts.AnswerStatus
import com.github.readingbat.posts.AnswerStatus.Companion.toAnswerStatus
import com.github.readingbat.posts.ChallengeResults
import com.github.readingbat.server.GeoInfo.Companion.gson
import com.github.readingbat.server.Installs.installs
import com.github.readingbat.server.Locations.locations
import com.github.readingbat.server.ReadingBatServer
import com.github.readingbat.server.routes.AdminRoutes.adminRoutes
import com.github.readingbat.server.routes.sysAdminRoutes
import com.github.readingbat.server.routes.userRoutes
import com.github.readingbat.server.ws.WsCommon.wsRoutes
import io.kotest.inspectors.forAll
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.ContentType.Application.FormUrlEncoded
import io.ktor.http.HttpHeaders.ContentType
import io.ktor.http.content.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking

class ChallengeAnswer(val funcInfo: FunctionInfo, val index: Int)

class ChallengeResult(val answerStatus: AnswerStatus,
                      val hint: String,
                      val index: Int,
                      val correctAnswer: String)

object TestSupport {

  inline infix fun ReadingBatContent.forEachLanguage(block: LanguageGroup<*>.() -> Unit) =
    languages.forEach { it.block() }

  inline fun <T : Challenge> LanguageGroup<T>.forEachGroup(block: ChallengeGroup<T>.() -> Unit) =
    challengeGroups.forEach { it.block() }

  fun <T : Challenge> ChallengeGroup<T>.forEachChallenge(block: Challenge.() -> Unit) =
    challenges.forAll { it.block() }

  fun ChallengeGroup<*>.forEachFuncInfo(block: FunctionInfo.() -> Unit) =
    forEachChallenge {
      functionInfo().block()
    }

  private fun Challenge.formData() = mutableListOf(LANG_SRC to challengeGroup.languageGroup.languageName.value,
                                                   GROUP_SRC to challengeGroup.groupName.value,
                                                   CHALLENGE_SRC to challengeName.value)

  fun Challenge.answerAllWith(engine: TestApplicationEngine,
                              userResponse: String,
                              block: ChallengeResult.() -> Unit) {
    val content =
      engine.postUrl(CHECK_ANSWERS_ENDPOINT) {
        addHeader(ContentType, FormUrlEncoded.toString())
        val data = formData()
        functionInfo().invocations.indices.forEach { data += "$RESP$it" to userResponse }
        setBody(data.formUrlEncode())
      }.response.content

    var cnt = 0
    gson.fromJson(content, List::class.java)
      .map { v ->
        (v as List<Any?>).let {
          ChallengeResult((it[0] as Double).toInt().toAnswerStatus(),
                          (it[1] as String),
                          cnt,
                          functionInfo().correctAnswers[cnt]).also { cnt++ }
        }
      }
      .forAll { it.block() }
  }

  fun Challenge.answerAllWithCorrectAnswer(engine: TestApplicationEngine, block: ChallengeResult.() -> Unit) {
    val content =
      engine.postUrl(CHECK_ANSWERS_ENDPOINT) {
        addHeader(ContentType, FormUrlEncoded.toString())
        val data = formData()
        functionInfo().invocations.indices.forEach { data += "$RESP$it" to functionInfo().correctAnswers[it] }
        setBody(data.formUrlEncode())
      }.response.content

    var cnt = 0
    gson.fromJson(content, List::class.java)
      .map { v ->
        (v as List<Any?>).let {
          ChallengeResult((it[0] as Double).toInt().toAnswerStatus(),
                          (it[1] as String),
                          cnt,
                          functionInfo().correctAnswers[cnt]).also { cnt++ }
        }
      }
      .forAll { it.block() }
  }

  fun ReadingBatContent.pythonChallenge(groupName: String, challengeName: String, block: FunctionInfo.() -> Unit) =
    pythonGroup(groupName).functionInfo(challengeName).apply(block)

  fun ReadingBatContent.javaChallenge(groupName: String, challengeName: String, block: FunctionInfo.() -> Unit) =
    javaGroup(groupName).functionInfo(challengeName).apply(block)

  fun ReadingBatContent.kotlinChallenge(groupName: String, challengeName: String, block: FunctionInfo.() -> Unit) =
    kotlinGroup(groupName).functionInfo(challengeName).apply(block)

  fun ReadingBatContent.pythonGroup(name: String) = python.get(name)
  fun ReadingBatContent.javaGroup(name: String) = java.get(name)
  fun ReadingBatContent.kotlinGroup(name: String) = kotlin.get(name)

  fun <T : Challenge> ChallengeGroup<T>.challengeByName(name: String) =
    challenges.firstOrNull { it.challengeName.value == name } ?: error("Missing challenge $name")

  fun <T : Challenge> ChallengeGroup<T>.functionInfo(name: String) = challengeByName(name).functionInfo()

  fun FunctionInfo.checkAnswer(index: Int, userResponse: String) =
    runBlocking {
      checkResponse(index, userResponse)
    }

  fun FunctionInfo.answerFor(index: Int) = ChallengeAnswer(this, index)

  fun Challenge.forEachAnswer(block: (ChallengeAnswer) -> Unit) =
    functionInfo().apply {
      (0 until questionCount).toList().forAll { i -> block(ChallengeAnswer(this, i)) }
    }

  infix fun ChallengeAnswer.shouldHaveAnswer(answer: Any) =
    funcInfo.checkAnswer(index, answer.toString()).shouldBeCorrect()

  infix fun ChallengeAnswer.shouldNotHaveAnswer(answer: Any) =
    funcInfo.checkAnswer(index, answer.toString()).shouldBeIncorrect()

  fun ChallengeResults.shouldBeCorrect() = correct.shouldBeTrue()
  fun ChallengeResults.shouldBeIncorrect() = correct.shouldBeFalse()

  fun TestApplicationEngine.getUrl(uri: String, block: TestApplicationCall.() -> Unit) =
    handleRequest(HttpMethod.Get, uri).apply { block() }

  fun TestApplicationEngine.postUrl(uri: String, block: TestApplicationRequest.() -> Unit) =
    handleRequest(HttpMethod.Post, uri, block)

  fun Application.testModule(content: ReadingBatContent) {

    Property.IS_TESTING.setProperty("true")

    installs(false)

    routing {
      adminRoutes(ReadingBatServer.metrics)
      locations(ReadingBatServer.metrics) { content }
      userRoutes(ReadingBatServer.metrics) { content }
      sysAdminRoutes(ReadingBatServer.metrics) { s: String -> }
      wsRoutes(ReadingBatServer.metrics) { content }
      static(Endpoints.STATIC_ROOT) { resources("static") }
    }
  }
}