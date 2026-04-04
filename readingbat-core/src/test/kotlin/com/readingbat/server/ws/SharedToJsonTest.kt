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

import com.readingbat.server.ws.ChallengeGroupWs.ChallengeStats
import com.readingbat.server.ws.ChallengeWs.PingMessage
import com.readingbat.server.ws.PubSubCommandsWs.AdminCommand
import com.readingbat.server.ws.PubSubCommandsWs.AdminCommandData
import com.readingbat.server.ws.PubSubCommandsWs.ChallengeAnswerData
import com.readingbat.server.ws.PubSubCommandsWs.LogData
import com.readingbat.server.ws.PubSubCommandsWs.PubSubTopic
import com.readingbat.utils.toJson
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.Json

class SharedToJsonTest : StringSpec() {
  init {
    "toJson should serialize PingMessage with type field" {
      val json = PingMessage("hello").toJson()
      json shouldContain "\"msg\":\"hello\""
      json shouldContain "\"type\""
    }

    "toJson should produce valid JSON that can be round-tripped" {
      val original = PingMessage("test message")
      val json = original.toJson()
      val deserialized = Json.decodeFromString<PingMessage>(json)
      deserialized shouldBe original
    }

    "toJson should serialize ChallengeStats" {
      val json = ChallengeStats("myChallenge", "3 correct").toJson()
      json shouldContain "\"challengeName\":\"myChallenge\""
      json shouldContain "\"msg\":\"3 correct\""
    }

    "toJson should serialize AdminCommandData" {
      val json = AdminCommandData(AdminCommand.RESET_CACHE, "{}", "log123").toJson()
      json shouldContain "\"command\""
      json shouldContain "\"logId\":\"log123\""
    }

    "toJson should serialize ChallengeAnswerData" {
      val json = ChallengeAnswerData(PubSubTopic.USER_ANSWERS, "target1", "{}").toJson()
      json shouldContain "\"pubSubTopic\""
      json shouldContain "\"target\":\"target1\""
    }

    "toJson should serialize LogData" {
      val json = LogData("some log text", "logABC").toJson()
      json shouldContain "\"text\":\"some log text\""
      json shouldContain "\"logId\":\"logABC\""
    }

    "data classes should support equality" {
      PingMessage("hello") shouldBe PingMessage("hello")
      ChallengeStats("name", "msg") shouldBe ChallengeStats("name", "msg")
      LogData("text", "id") shouldBe LogData("text", "id")
    }

    "data classes should support copy" {
      val original = PingMessage("original")
      val copied = original.copy(msg = "copied")
      copied.msg shouldBe "copied"
    }
  }
}
