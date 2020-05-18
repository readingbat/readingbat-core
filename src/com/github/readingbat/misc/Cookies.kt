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

import com.github.pambrose.common.util.randomId
import io.ktor.auth.Principal
import java.time.Instant

internal fun userIdKey(username: String) = "${KeyPrefixes.USER_ID}|$username"

internal class UserId(val id: String = randomId(25)) {
  fun saltKey() = "${KeyPrefixes.SALT}|$id"

  fun passwordKey() = "${KeyPrefixes.PASSWD}|$id"

  fun challengeKey(languageName: String, groupName: String, challengeName: String) =
    listOf(KeyPrefixes.CHALLENGE_ANSWERS,
           KeyPrefixes.AUTH, id, languageName, groupName, challengeName).joinToString("|")

  fun argumentKey(languageName: String, groupName: String, challengeName: String, argument: String) =
    listOf(KeyPrefixes.ANSWER_HISTORY,
           KeyPrefixes.AUTH, id, languageName, groupName, challengeName, argument).joinToString("|")
}

data class UserPrincipal(val userId: String, val created: Long = Instant.now().toEpochMilli()) : Principal

internal data class BrowserSession(val id: String, val created: Long = Instant.now().toEpochMilli()) {
  fun challengeKey(languageName: String, groupName: String, challengeName: String) =
    listOf(KeyPrefixes.CHALLENGE_ANSWERS,
           KeyPrefixes.NO_AUTH, id, languageName, groupName, challengeName).joinToString("|")

  fun argumentKey(languageName: String, groupName: String, challengeName: String, argument: String) =
    listOf(KeyPrefixes.ANSWER_HISTORY,
           KeyPrefixes.NO_AUTH, id, languageName, groupName, challengeName, argument).joinToString("|")
}

internal data class ChallengeAnswers(val id: String, val answers: MutableMap<String, String> = mutableMapOf())
