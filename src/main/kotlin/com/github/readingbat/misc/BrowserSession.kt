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

import com.github.readingbat.misc.KeyPrefixes.ANSWER_HISTORY
import com.github.readingbat.misc.KeyPrefixes.CHALLENGE_ANSWERS
import com.github.readingbat.misc.KeyPrefixes.CORRECT_ANSWERS
import com.github.readingbat.misc.KeyPrefixes.NO_AUTH
import com.github.readingbat.posts.ChallengeNames
import java.time.Instant

internal data class BrowserSession(val id: String, val created: Long = Instant.now().toEpochMilli()) {

  fun correctAnswersKey(names: ChallengeNames) =
    correctAnswersKey(names.languageName, names.groupName, names.challengeName)

  fun correctAnswersKey(languageName: String, groupName: String, challengeName: String) =
    listOf(CORRECT_ANSWERS,
           NO_AUTH, id, languageName, groupName, challengeName).joinToString("|")

  fun challengeKey(names: ChallengeNames) =
    challengeKey(names.languageName, names.groupName, names.challengeName)

  fun challengeKey(languageName: String, groupName: String, challengeName: String) =
    listOf(CHALLENGE_ANSWERS,
           NO_AUTH, id, languageName, groupName, challengeName).joinToString("|")

  fun argumentKey(names: ChallengeNames, argument: String) =
    argumentKey(names.languageName, names.groupName, names.challengeName, argument)

  fun argumentKey(languageName: String, groupName: String, challengeName: String, argument: String) =
    listOf(ANSWER_HISTORY,
           NO_AUTH, id, languageName, groupName, challengeName, argument).joinToString("|")
}
