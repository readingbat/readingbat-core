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

package com.readingbat.common

import com.readingbat.dsl.isMultiServerEnabled
import com.readingbat.posts.DashboardHistory
import com.readingbat.posts.DashboardInfo
import com.readingbat.posts.LikeDislikeInfo
import com.readingbat.server.ws.ChallengeWs.classTargetName
import com.readingbat.server.ws.ChallengeWs.multiServerWsWriteFlow
import com.readingbat.server.ws.ChallengeWs.singleServerWsFlow
import com.readingbat.server.ws.PubSubCommandsWs.ChallengeAnswerData
import com.readingbat.server.ws.PubSubCommandsWs.PubSubTopic.LIKE_DISLIKE
import com.readingbat.server.ws.PubSubCommandsWs.PubSubTopic.USER_ANSWERS
import com.readingbat.utils.toJson
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Publishes real-time answer updates and like/dislike events to teacher dashboards via WebSocket.
 *
 * When a student submits an answer or changes a like/dislike rating, this publisher emits the
 * update to the appropriate WebSocket flow (single-server or multi-server pub/sub), which is
 * then delivered to any teacher actively monitoring the student's enrolled class.
 */
internal object AnswerPublisher {
  private val logger = KotlinLogging.logger {}

  private val wsFlow get() = if (isMultiServerEnabled()) multiServerWsWriteFlow else singleServerWsFlow

  /** Publishes a student's answer update for a specific challenge invocation to the teacher's dashboard. */
  suspend fun publishAnswers(
    user: User,
    challengeMd5: String,
    maxHistoryLength: Int,
    complete: Boolean,
    numCorrect: Int,
    history: com.readingbat.posts.ChallengeHistory,
  ) {
    logger.debug { "Publishing user answers to ${user.enrolledClassCode} on $challengeMd5 for $user" }
    val dashboardHistory =
      DashboardHistory(
        history.invocation.value,
        history.correct,
        history.answers.asReversed().take(maxHistoryLength).joinToString("<br>"),
      )
    val targetName = classTargetName(user.enrolledClassCode, challengeMd5)
    val dashboardInfo = DashboardInfo(user.userId, complete, numCorrect, dashboardHistory)
    wsFlow.emit(ChallengeAnswerData(USER_ANSWERS, targetName, dashboardInfo.toJson()))
  }

  /** Publishes a student's like/dislike change for a challenge to the teacher's dashboard. */
  suspend fun publishLikeDislike(user: User, challengeMd5: String, likeDislike: Int) {
    logger.debug { "Publishing user likeDislike to ${user.enrolledClassCode} on $challengeMd5 for $user" }
    val targetName = classTargetName(user.enrolledClassCode, challengeMd5)
    val emoji = user.likeDislikeEmoji(likeDislike)
    val likeDislikeInfo = LikeDislikeInfo(user.userId, emoji)
    wsFlow.emit(ChallengeAnswerData(LIKE_DISLIKE, targetName, likeDislikeInfo.toJson()))
  }
}
