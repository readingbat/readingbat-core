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

/**
 * Constants and utilities for building composite cache/storage keys.
 *
 * Keys are constructed by joining segments with [KEY_SEP] to create hierarchical identifiers
 * used for looking up challenge answers, source code, and content data.
 */
internal object KeyConstants {
  const val KEY_SEP = "|"
  const val AUTH_KEY = "auth"
  const val NO_AUTH_KEY = "noauth"
  const val CORRECT_ANSWERS_KEY = "correct-answers"
  const val CHALLENGE_ANSWERS_KEY = "challenge-answers"
  const val SOURCE_CODE_KEY = "source-code"
  const val CONTENT_DSL_KEY = "content-dsl"
  const val DIR_CONTENTS_KEY = "dir-contents"

  /** Joins the given key segments into a single composite key string separated by [KEY_SEP]. */
  fun keyOf(vararg keys: Any) = keys.joinToString(KEY_SEP) { it.toString() }
}

/** Builds the correct-answers cache key for the given user and challenge, or empty string if user is null. */
internal fun correctAnswersKey(user: User?, challenge: com.readingbat.dsl.challenge.Challenge) =
  user?.correctAnswersKey(challenge.languageName, challenge.groupName, challenge.challengeName) ?: ""

/** Builds the correct-answers cache key for the given user and challenge path components. */
internal fun correctAnswersKey(
  user: User?,
  languageName: com.readingbat.server.LanguageName,
  groupName: com.readingbat.server.GroupName,
  challengeName: com.readingbat.server.ChallengeName,
) =
  user?.correctAnswersKey(languageName, groupName, challengeName) ?: ""

/** Builds the challenge-answers cache key for the given user and challenge path components. */
internal fun challengeAnswersKey(
  user: User?,
  languageName: com.readingbat.server.LanguageName,
  groupName: com.readingbat.server.GroupName,
  challengeName: com.readingbat.server.ChallengeName,
) =
  user?.challengeAnswersKey(languageName, groupName, challengeName) ?: ""

internal fun challengeAnswersKey(user: User?, challenge: com.readingbat.dsl.challenge.Challenge) =
  challengeAnswersKey(
    user,
    challenge.languageType.languageName,
    challenge.groupName,
    challenge.challengeName,
  )
