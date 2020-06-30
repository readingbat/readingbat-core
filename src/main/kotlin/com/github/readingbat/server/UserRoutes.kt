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

package com.github.readingbat.server

import com.github.pambrose.common.redis.RedisUtils.withRedisPool
import com.github.pambrose.common.redis.RedisUtils.withSuspendingRedisPool
import com.github.pambrose.common.response.redirectTo
import com.github.pambrose.common.response.respondWith
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.AuthRoutes.LOGOUT
import com.github.readingbat.misc.Constants.CHALLENGE_ROOT
import com.github.readingbat.misc.Constants.ICONS
import com.github.readingbat.misc.Constants.RESET_ID
import com.github.readingbat.misc.Constants.RETURN_PATH
import com.github.readingbat.misc.Constants.ROOT
import com.github.readingbat.misc.Constants.STATIC_ROOT
import com.github.readingbat.misc.Endpoints.ABOUT_ENDPOINT
import com.github.readingbat.misc.Endpoints.ADMIN_ENDPOINT
import com.github.readingbat.misc.Endpoints.CHECK_ANSWERS_ENDPOINT
import com.github.readingbat.misc.Endpoints.CLEAR_CHALLENGE_ANSWERS_ENDPOINT
import com.github.readingbat.misc.Endpoints.CLEAR_GROUP_ANSWERS_ENDPOINT
import com.github.readingbat.misc.Endpoints.CREATE_ACCOUNT_ENDPOINT
import com.github.readingbat.misc.Endpoints.CSS_ENDPOINT
import com.github.readingbat.misc.Endpoints.ENABLE_STUDENT_MODE_ENDPOINT
import com.github.readingbat.misc.Endpoints.ENABLE_TEACHER_MODE_ENDPOINT
import com.github.readingbat.misc.Endpoints.FAV_ICON_ENDPOINT
import com.github.readingbat.misc.Endpoints.PASSWORD_CHANGE_ENDPOINT
import com.github.readingbat.misc.Endpoints.PASSWORD_RESET_ENDPOINT
import com.github.readingbat.misc.Endpoints.PRIVACY_ENDPOINT
import com.github.readingbat.misc.Endpoints.TEACHER_PREFS_ENDPOINT
import com.github.readingbat.misc.Endpoints.USER_PREFS_ENDPOINT
import com.github.readingbat.misc.UserPrincipal
import com.github.readingbat.misc.cssContent
import com.github.readingbat.pages.AboutPage.aboutPage
import com.github.readingbat.pages.AdminPage.adminDataPage
import com.github.readingbat.pages.CreateAccountPage.createAccountPage
import com.github.readingbat.pages.DbmsDownPage.dbmsDownPage
import com.github.readingbat.pages.PageCommon.defaultLanguageTab
import com.github.readingbat.pages.PasswordResetPage.passwordResetPage
import com.github.readingbat.pages.PrivacyPage.privacyPage
import com.github.readingbat.pages.TeacherPrefsPage.teacherPrefsPage
import com.github.readingbat.pages.UserPrefsPage.userPrefsPage
import com.github.readingbat.posts.AdminPost.adminActions
import com.github.readingbat.posts.ChallengePost.checkAnswers
import com.github.readingbat.posts.ChallengePost.clearChallengeAnswers
import com.github.readingbat.posts.ChallengePost.clearGroupAnswers
import com.github.readingbat.posts.CreateAccountPost.createAccount
import com.github.readingbat.posts.PasswordResetPost.changePassword
import com.github.readingbat.posts.PasswordResetPost.sendPasswordReset
import com.github.readingbat.posts.TeacherPrefsPost.enableStudentMode
import com.github.readingbat.posts.TeacherPrefsPost.enableTeacherMode
import com.github.readingbat.posts.TeacherPrefsPost.teacherPrefs
import com.github.readingbat.posts.UserPrefsPost.userPrefs
import com.github.readingbat.server.ServerUtils.fetchUser
import com.github.readingbat.server.ServerUtils.queryParam
import io.ktor.application.call
import io.ktor.http.ContentType.Text.CSS
import io.ktor.routing.Routing
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.sessions.clear
import io.ktor.sessions.sessions
import redis.clients.jedis.Jedis

internal fun Routing.userRoutes(content: ReadingBatContent) {

  suspend fun PipelineCall.respondWithDbmsCheck(block: (redis: Jedis) -> String) =
    try {
      val html =
        withRedisPool { redis ->
          if (redis == null)
            dbmsDownPage(content)
          else
            block.invoke(redis)
        }
      respondWith { html }
    } catch (e: RedirectException) {
      redirectTo { e.redirectUrl }
    }

  suspend fun PipelineCall.respondWithSuspendingDbmsCheck(block: suspend (redis: Jedis) -> String) =
    try {
      val html =
        withSuspendingRedisPool { redis ->
          if (redis == null)
            dbmsDownPage(content)
          else
            block.invoke(redis)
        }
      respondWith { html }
    } catch (e: RedirectException) {
      redirectTo { e.redirectUrl }
    }

  get(ROOT) { redirectTo { defaultLanguageTab(content) } }

  get(CHALLENGE_ROOT) { redirectTo { defaultLanguageTab(content) } }

  get(PRIVACY_ENDPOINT) { respondWith { privacyPage(content) } }

  get(ABOUT_ENDPOINT) { respondWith { aboutPage(content) } }

  post(CHECK_ANSWERS_ENDPOINT) { withSuspendingRedisPool { redis -> checkAnswers(content, fetchUser(), redis) } }

  post(CLEAR_GROUP_ANSWERS_ENDPOINT) {
    respondWithSuspendingDbmsCheck { redis -> clearGroupAnswers(content, fetchUser(), redis) }
  }

  post(CLEAR_CHALLENGE_ANSWERS_ENDPOINT) {
    respondWithSuspendingDbmsCheck { redis -> clearChallengeAnswers(content, fetchUser(), redis) }
  }

  get(CREATE_ACCOUNT_ENDPOINT) { respondWith { createAccountPage(content) } }

  post(CREATE_ACCOUNT_ENDPOINT) { respondWithSuspendingDbmsCheck { redis -> createAccount(content, redis) } }

  get(USER_PREFS_ENDPOINT) { respondWithDbmsCheck { redis -> userPrefsPage(content, fetchUser(), redis) } }

  post(USER_PREFS_ENDPOINT) { respondWithSuspendingDbmsCheck { redis -> userPrefs(content, fetchUser(), redis) } }

  get(TEACHER_PREFS_ENDPOINT) { respondWithDbmsCheck { redis -> teacherPrefsPage(content, fetchUser(), redis) } }

  post(TEACHER_PREFS_ENDPOINT) { respondWithSuspendingDbmsCheck { redis -> teacherPrefs(content, fetchUser(), redis) } }

  get(ENABLE_STUDENT_MODE_ENDPOINT) {
    respondWithSuspendingDbmsCheck { redis -> enableStudentMode(fetchUser(), redis) }
  }

  get(ENABLE_TEACHER_MODE_ENDPOINT) {
    respondWithSuspendingDbmsCheck { redis -> enableTeacherMode(fetchUser(), redis) }
  }

  get(ADMIN_ENDPOINT) { respondWithDbmsCheck { redis -> adminDataPage(content, fetchUser(), redis = redis) } }

  post(ADMIN_ENDPOINT) { respondWithSuspendingDbmsCheck { redis -> adminActions(content, fetchUser(), redis) } }

  // RESET_ID is passed here when user clicks on email URL
  get(PASSWORD_RESET_ENDPOINT) {
    respondWithDbmsCheck { redis -> passwordResetPage(content, ResetId(queryParam(RESET_ID)), redis) }
  }

  post(PASSWORD_RESET_ENDPOINT) {
    respondWithSuspendingDbmsCheck { redis -> sendPasswordReset(content, fetchUser(), redis) }
  }

  post(PASSWORD_CHANGE_ENDPOINT) { respondWithSuspendingDbmsCheck { redis -> changePassword(content, redis) } }

  get(LOGOUT) {
    // Purge UserPrincipal from cookie data
    call.sessions.clear<UserPrincipal>()
    redirectTo { queryParam(RETURN_PATH, "/") }
  }

  get(CSS_ENDPOINT) { respondWith(CSS) { cssContent } }

  get(FAV_ICON_ENDPOINT) { redirectTo { "$STATIC_ROOT/$ICONS/favicon.ico" } }
}