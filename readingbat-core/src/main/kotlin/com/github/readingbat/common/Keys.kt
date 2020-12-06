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

package com.github.readingbat.common

import com.github.readingbat.dsl.Challenge
import com.github.readingbat.server.ChallengeName
import com.github.readingbat.server.GroupName
import com.github.readingbat.server.LanguageName

internal object KeyConstants {
  const val KEY_SEP = "|"
  const val AUTH_KEY = "auth"
  const val NO_AUTH_KEY = "noauth"
  const val CORRECT_ANSWERS_KEY = "correct-answers"
  const val CHALLENGE_ANSWERS_KEY = "challenge-answers"
  const val SOURCE_CODE_KEY = "source-code"
  const val CONTENT_DSL_KEY = "content-dsl"
  const val DIR_CONTENTS_KEY = "dir-contents"

  fun keyOf(vararg keys: Any) = keys.joinToString(KEY_SEP) { it.toString() }
}

internal fun correctAnswersKey(user: User?, browserSession: BrowserSession?, challenge: Challenge) =
  user?.correctAnswersKey(challenge.languageName, challenge.groupName, challenge.challengeName)
    ?: browserSession?.correctAnswersKey(challenge.languageName, challenge.groupName, challenge.challengeName)
    ?: ""

internal fun correctAnswersKey(user: User?,
                               browserSession: BrowserSession?,
                               languageName: LanguageName,
                               groupName: GroupName,
                               challengeName: ChallengeName) =
  user?.correctAnswersKey(languageName, groupName, challengeName)
    ?: browserSession?.correctAnswersKey(languageName, groupName, challengeName)
    ?: ""

internal fun challengeAnswersKey(user: User?,
                                 browserSession: BrowserSession?,
                                 languageName: LanguageName,
                                 groupName: GroupName,
                                 challengeName: ChallengeName) =
  user?.challengeAnswersKey(languageName, groupName, challengeName)
    ?: browserSession?.challengeAnswerKey(languageName, groupName, challengeName)
    ?: ""

internal fun challengeAnswersKey(user: User?, browserSession: BrowserSession?, challenge: Challenge) =
  challengeAnswersKey(user,
                      browserSession,
                      challenge.languageType.languageName,
                      challenge.groupName,
                      challenge.challengeName)