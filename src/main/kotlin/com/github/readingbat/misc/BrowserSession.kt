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

package com.github.readingbat.misc

import com.github.readingbat.misc.KeyConstants.ANSWER_HISTORY_KEY
import com.github.readingbat.misc.KeyConstants.CHALLENGE_ANSWERS_KEY
import com.github.readingbat.misc.KeyConstants.CORRECT_ANSWERS_KEY
import com.github.readingbat.misc.KeyConstants.KEY_SEP
import com.github.readingbat.misc.KeyConstants.NO_AUTH_KEY
import com.github.readingbat.posts.ChallengeNames
import com.github.readingbat.server.ChallengeName
import com.github.readingbat.server.GroupName
import com.github.readingbat.server.Invocation
import com.github.readingbat.server.LanguageName
import java.time.Instant

internal data class BrowserSession(val id: String, val created: Long = Instant.now().toEpochMilli()) {

  fun correctAnswersKey(names: ChallengeNames) =
    correctAnswersKey(names.languageName, names.groupName, names.challengeName)

  fun correctAnswersKey(languageName: LanguageName, groupName: GroupName, challengeName: ChallengeName) =
    listOf(CORRECT_ANSWERS_KEY, NO_AUTH_KEY, id, languageName, groupName, challengeName).joinToString(KEY_SEP)

  fun challengeAnswerKey(names: ChallengeNames) =
    challengeAnswerKey(names.languageName, names.groupName, names.challengeName)

  fun challengeAnswerKey(languageName: LanguageName, groupName: GroupName, challengeName: ChallengeName) =
    listOf(CHALLENGE_ANSWERS_KEY, NO_AUTH_KEY, id, languageName, groupName, challengeName).joinToString(KEY_SEP)

  fun answerHistoryKey(names: ChallengeNames, invocation: Invocation) =
    answerHistoryKey(names.languageName, names.groupName, names.challengeName, invocation)

  private fun answerHistoryKey(languageName: LanguageName,
                               groupName: GroupName,
                               challengeName: ChallengeName,
                               invocation: Invocation) =
    listOf(ANSWER_HISTORY_KEY,
           NO_AUTH_KEY,
           id,
           languageName.value,
           groupName.value,
           challengeName.value,
           invocation.value)
      .joinToString(KEY_SEP)
}
