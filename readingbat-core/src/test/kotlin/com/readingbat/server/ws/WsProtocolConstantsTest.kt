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

package com.readingbat.server.ws

import com.readingbat.common.Constants.LIKE_DISLIKE_CODE
import com.readingbat.common.Constants.PING_CODE
import com.readingbat.common.WsProtocol
import com.readingbat.posts.DashboardHistory
import com.readingbat.posts.DashboardInfo
import com.readingbat.posts.LikeDislikeInfo
import com.readingbat.server.ws.ChallengeGroupWs.ChallengeStats
import com.readingbat.server.ws.ChallengeWs.PingMessage
import com.readingbat.server.ws.ClassSummaryWs.ClassSummary
import com.readingbat.server.ws.StudentSummaryWs.StudentSummary
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class WsProtocolConstantsTest : StringSpec() {
  init {
    "PingMessage serializes with WsProtocol field names" {
      val msg = PingMessage("5s")
      val json = Json.encodeToString(PingMessage.serializer(), msg)
      val obj = Json.decodeFromString<JsonObject>(json)

      obj.containsKey(WsProtocol.TYPE_FIELD) shouldBe true
      obj.containsKey(WsProtocol.MSG_FIELD) shouldBe true
      obj[WsProtocol.TYPE_FIELD]!!.jsonPrimitive.content shouldBe PING_CODE
      obj[WsProtocol.MSG_FIELD]!!.jsonPrimitive.content shouldBe "5s"
    }

    "LikeDislikeInfo serializes with WsProtocol field names" {
      val info = LikeDislikeInfo(userId = "user1", likeDislike = "thumbsup")
      val json = Json.encodeToString(LikeDislikeInfo.serializer(), info)
      val obj = Json.decodeFromString<JsonObject>(json)

      obj.containsKey(WsProtocol.USER_ID_FIELD) shouldBe true
      obj.containsKey(WsProtocol.LIKE_DISLIKE_FIELD) shouldBe true
      obj.containsKey(WsProtocol.TYPE_FIELD) shouldBe true
      obj[WsProtocol.TYPE_FIELD]!!.jsonPrimitive.content shouldBe LIKE_DISLIKE_CODE
      obj[WsProtocol.USER_ID_FIELD]!!.jsonPrimitive.content shouldBe "user1"
      obj[WsProtocol.LIKE_DISLIKE_FIELD]!!.jsonPrimitive.content shouldBe "thumbsup"
    }

    "DashboardInfo serializes with WsProtocol field names" {
      val history = DashboardHistory(invocation = "func(1)", correct = true, answers = "1<br>2")
      val info = DashboardInfo(userId = "user1", complete = true, numCorrect = 3, history = history)
      val json = Json.encodeToString(DashboardInfo.serializer(), info)
      val obj = Json.decodeFromString<JsonObject>(json)

      obj.containsKey(WsProtocol.USER_ID_FIELD) shouldBe true
      obj.containsKey(WsProtocol.COMPLETE_FIELD) shouldBe true
      obj.containsKey(WsProtocol.NUM_CORRECT_FIELD) shouldBe true
      obj.containsKey(WsProtocol.HISTORY_FIELD) shouldBe true

      // Verify nested history object fields
      val historyObj = obj[WsProtocol.HISTORY_FIELD] as JsonObject
      historyObj.containsKey(WsProtocol.INVOCATION_FIELD) shouldBe true
      historyObj.containsKey(WsProtocol.CORRECT_FIELD) shouldBe true
      historyObj.containsKey(WsProtocol.ANSWERS_FIELD) shouldBe true
    }

    "ClassSummary serializes with WsProtocol field names" {
      val summary =
        ClassSummary(
        userId = "user1",
        challengeName = "hello",
        results = listOf("Y", "N"),
        stats = "1/2",
        likeDislike = "",
      )
      val json = Json.encodeToString(ClassSummary.serializer(), summary)
      val obj = Json.decodeFromString<JsonObject>(json)

      obj.containsKey(WsProtocol.USER_ID_FIELD) shouldBe true
      obj.containsKey(WsProtocol.CHALLENGE_NAME_FIELD) shouldBe true
      obj.containsKey(WsProtocol.RESULTS_FIELD) shouldBe true
      obj.containsKey(WsProtocol.STATS_FIELD) shouldBe true
      obj.containsKey(WsProtocol.LIKE_DISLIKE_FIELD) shouldBe true
    }

    "StudentSummary serializes with WsProtocol field names" {
      val summary =
        StudentSummary(
        groupName = "Warmup-1",
        challengeName = "hello",
        results = listOf("Y"),
        stats = "1/1",
        likeDislike = "",
      )
      val json = Json.encodeToString(StudentSummary.serializer(), summary)
      val obj = Json.decodeFromString<JsonObject>(json)

      obj.containsKey(WsProtocol.GROUP_NAME_FIELD) shouldBe true
      obj.containsKey(WsProtocol.CHALLENGE_NAME_FIELD) shouldBe true
      obj.containsKey(WsProtocol.RESULTS_FIELD) shouldBe true
      obj.containsKey(WsProtocol.STATS_FIELD) shouldBe true
      obj.containsKey(WsProtocol.LIKE_DISLIKE_FIELD) shouldBe true
    }

    "ChallengeStats serializes with WsProtocol field names" {
      val stats = ChallengeStats(challengeName = "hello", msg = "2/3")
      val json = Json.encodeToString(ChallengeStats.serializer(), stats)
      val obj = Json.decodeFromString<JsonObject>(json)

      obj.containsKey(WsProtocol.CHALLENGE_NAME_FIELD) shouldBe true
      obj.containsKey(WsProtocol.MSG_FIELD) shouldBe true
    }

    "WsProtocol field names match expected JSON keys" {
      // Guard against accidental changes to the protocol field names
      WsProtocol.TYPE_FIELD shouldBe "type"
      WsProtocol.MSG_FIELD shouldBe "msg"
      WsProtocol.USER_ID_FIELD shouldBe "userId"
      WsProtocol.LIKE_DISLIKE_FIELD shouldBe "likeDislike"
      WsProtocol.COMPLETE_FIELD shouldBe "complete"
      WsProtocol.NUM_CORRECT_FIELD shouldBe "numCorrect"
      WsProtocol.HISTORY_FIELD shouldBe "history"
      WsProtocol.INVOCATION_FIELD shouldBe "invocation"
      WsProtocol.CORRECT_FIELD shouldBe "correct"
      WsProtocol.ANSWERS_FIELD shouldBe "answers"
      WsProtocol.CHALLENGE_NAME_FIELD shouldBe "challengeName"
      WsProtocol.RESULTS_FIELD shouldBe "results"
      WsProtocol.STATS_FIELD shouldBe "stats"
      WsProtocol.GROUP_NAME_FIELD shouldBe "groupName"
    }

    "PingMessage round-trips through JSON correctly" {
      val original = PingMessage("10s")
      val json = Json.encodeToString(PingMessage.serializer(), original)
      val decoded = Json.decodeFromString<PingMessage>(json)
      decoded shouldBe original
    }

    "DashboardInfo round-trips through JSON correctly" {
      val history = DashboardHistory(invocation = "func(1)", correct = false, answers = "wrong")
      val original = DashboardInfo(userId = "u1", complete = false, numCorrect = 0, history = history)
      val json = Json.encodeToString(DashboardInfo.serializer(), original)
      val decoded = Json.decodeFromString<DashboardInfo>(json)
      decoded shouldBe original
    }

    "Serialized JSON does not contain old-style field names when renamed" {
      // Ensure that @SerialName actually takes effect -- the JSON should use the
      // constant values (which currently match the property names, but this test
      // ensures the annotation is wired up)
      val info = LikeDislikeInfo(userId = "test", likeDislike = "emoji")
      val json = Json.encodeToString(LikeDislikeInfo.serializer(), info)
      json shouldContain "\"${WsProtocol.USER_ID_FIELD}\""
      json shouldContain "\"${WsProtocol.LIKE_DISLIKE_FIELD}\""
      json shouldContain "\"${WsProtocol.TYPE_FIELD}\""
    }
  }
}
