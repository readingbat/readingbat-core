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
import com.github.readingbat.common.CommonUtils.pathOf
import com.github.readingbat.common.Constants.ICONS
import com.github.readingbat.common.Endpoints.ABOUT_ENDPOINT
import com.github.readingbat.common.Endpoints.ADMIN_ENDPOINT
import com.github.readingbat.common.Endpoints.CHALLENGE_ROOT
import com.github.readingbat.common.Endpoints.CHECK_ANSWERS_ENDPOINT
import com.github.readingbat.common.Endpoints.CLASS_SUMMARY_ENDPOINT
import com.github.readingbat.common.Endpoints.CLEAR_CHALLENGE_ANSWERS_ENDPOINT
import com.github.readingbat.common.Endpoints.CLEAR_GROUP_ANSWERS_ENDPOINT
import com.github.readingbat.common.Endpoints.CONFIG_ENDPOINT
import com.github.readingbat.common.Endpoints.CREATE_ACCOUNT_ENDPOINT
import com.github.readingbat.common.Endpoints.CSS_ENDPOINT
import com.github.readingbat.common.Endpoints.ENABLE_STUDENT_MODE_ENDPOINT
import com.github.readingbat.common.Endpoints.ENABLE_TEACHER_MODE_ENDPOINT
import com.github.readingbat.common.Endpoints.FAV_ICON_ENDPOINT
import com.github.readingbat.common.Endpoints.HELP_ENDPOINT
import com.github.readingbat.common.Endpoints.LIKE_DISLIKE_ENDPOINT
import com.github.readingbat.common.Endpoints.LOGOUT_ENDPOINT
import com.github.readingbat.common.Endpoints.PASSWORD_CHANGE_ENDPOINT
import com.github.readingbat.common.Endpoints.PASSWORD_RESET_ENDPOINT
import com.github.readingbat.common.Endpoints.PRIVACY_ENDPOINT
import com.github.readingbat.common.Endpoints.ROBOTS_ENDPOINT
import com.github.readingbat.common.Endpoints.ROOT
import com.github.readingbat.common.Endpoints.SESSIONS_ENDPOINT
import com.github.readingbat.common.Endpoints.STATIC_ROOT
import com.github.readingbat.common.Endpoints.STUDENT_SUMMARY_ENDPOINT
import com.github.readingbat.common.Endpoints.SYSTEM_ADMIN_ENDPOINT
import com.github.readingbat.common.Endpoints.TEACHER_PREFS_ENDPOINT
import com.github.readingbat.common.Endpoints.USER_INFO_ENDPOINT
import com.github.readingbat.common.Endpoints.USER_PREFS_ENDPOINT
import com.github.readingbat.common.FormFields.RESET_ID_PARAM
import com.github.readingbat.common.FormFields.RETURN_PARAM
import com.github.readingbat.common.Metrics
import com.github.readingbat.common.UserPrincipal
import com.github.readingbat.common.cssContent
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.pages.AboutPage.aboutPage
import com.github.readingbat.pages.AdminPage.adminDataPage
import com.github.readingbat.pages.ClassSummaryPage.classSummaryPage
import com.github.readingbat.pages.ConfigPage.configPage
import com.github.readingbat.pages.CreateAccountPage.createAccountPage
import com.github.readingbat.pages.HelpPage.helpPage
import com.github.readingbat.pages.PasswordResetPage.passwordResetPage
import com.github.readingbat.pages.PrivacyPage.privacyPage
import com.github.readingbat.pages.SessionsPage.sessionsPage
import com.github.readingbat.pages.StudentSummaryPage.studentSummaryPage
import com.github.readingbat.pages.SystemAdminPage.systemAdminPage
import com.github.readingbat.pages.TeacherPrefsPage.teacherPrefsPage
import com.github.readingbat.pages.UserInfoPage.userInfoPage
import com.github.readingbat.pages.UserPrefsPage.userPrefsPage
import com.github.readingbat.posts.AdminPost.adminActions
import com.github.readingbat.posts.ChallengePost.checkAnswers
import com.github.readingbat.posts.ChallengePost.clearChallengeAnswers
import com.github.readingbat.posts.ChallengePost.clearGroupAnswers
import com.github.readingbat.posts.ChallengePost.likeDislike
import com.github.readingbat.posts.CreateAccountPost.createAccount
import com.github.readingbat.posts.PasswordResetPost.changePassword
import com.github.readingbat.posts.PasswordResetPost.sendPasswordReset
import com.github.readingbat.posts.TeacherPrefsPost.enableStudentMode
import com.github.readingbat.posts.TeacherPrefsPost.enableTeacherMode
import com.github.readingbat.posts.TeacherPrefsPost.teacherPrefs
import com.github.readingbat.posts.UserPrefsPost.userPrefs
import com.github.readingbat.server.ReadingBatServer.redisPool
import com.github.readingbat.server.ResourceContent.getResourceAsText
import com.github.readingbat.server.ServerUtils.authenticatedPage
import com.github.readingbat.server.ServerUtils.defaultLanguageTab
import com.github.readingbat.server.ServerUtils.fetchUser
import com.github.readingbat.server.ServerUtils.get
import com.github.readingbat.server.ServerUtils.queryParam
import com.github.readingbat.server.ServerUtils.respondWithRedisCheck
import com.github.readingbat.server.ServerUtils.respondWithSuspendingRedisCheck
import com.github.readingbat.server.StaticVals.robotsTxt
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.ContentType.Text.CSS
import io.ktor.routing.*
import io.ktor.sessions.*

internal fun Routing.userRoutes(metrics: Metrics, contentSrc: () -> ReadingBatContent) {

  get(ROOT) {
    redirectTo { defaultLanguageTab(contentSrc()) }
  }

  post(ROOT) {
    redirectTo { defaultLanguageTab(contentSrc()) }
  }

  get(CHALLENGE_ROOT) {
    redirectTo { defaultLanguageTab(contentSrc()) }
  }

  post(CHALLENGE_ROOT) {
    redirectTo { defaultLanguageTab(contentSrc()) }
  }

  get(CONFIG_ENDPOINT) {
    respondWith { authenticatedPage { configPage(contentSrc()) } }
  }

  get(SESSIONS_ENDPOINT) {
    respondWithSuspendingRedisCheck { redis -> authenticatedPage { sessionsPage(contentSrc(), redis) } }
  }

  get(PRIVACY_ENDPOINT) {
    respondWith { privacyPage(contentSrc()) }
  }

  get(ABOUT_ENDPOINT) {
    respondWith {
      redisPool?.withRedisPool { redis ->
        aboutPage(contentSrc(), fetchUser(), redis)
      } ?: aboutPage(contentSrc(), fetchUser(), null)
    }
  }

  get(HELP_ENDPOINT) {
    respondWith {
      redisPool?.withRedisPool { redis ->
        helpPage(contentSrc(), fetchUser(), redis)
      } ?: helpPage(contentSrc(), fetchUser(), null)
    }
  }

  post(CHECK_ANSWERS_ENDPOINT) {
    metrics.measureEndpointRequest(CHECK_ANSWERS_ENDPOINT) {
      redisPool?.withSuspendingRedisPool { redis ->
        checkAnswers(contentSrc(), fetchUser(), redis)
      } ?: checkAnswers(contentSrc(), fetchUser(), null)
    }
  }

  post(LIKE_DISLIKE_ENDPOINT) {
    metrics.measureEndpointRequest(LIKE_DISLIKE_ENDPOINT) {
      redisPool?.withSuspendingRedisPool { redis ->
        likeDislike(contentSrc(), fetchUser(), redis)
      } ?: likeDislike(contentSrc(), fetchUser(), null)
    }
  }

  post(CLEAR_GROUP_ANSWERS_ENDPOINT) {
    respondWithSuspendingRedisCheck { redis ->
      clearGroupAnswers(contentSrc(), fetchUser(), redis)
    }
  }

  post(CLEAR_CHALLENGE_ANSWERS_ENDPOINT) {
    respondWithSuspendingRedisCheck { redis ->
      clearChallengeAnswers(contentSrc(), fetchUser(), redis)
    }
  }

  get(CREATE_ACCOUNT_ENDPOINT, metrics) {
    respondWithRedisCheck { createAccountPage(contentSrc()) }
  }

  post(CREATE_ACCOUNT_ENDPOINT) {
    respondWithSuspendingRedisCheck { redis -> createAccount(contentSrc(), redis) }
  }

  get(USER_PREFS_ENDPOINT, metrics) {
    respondWithRedisCheck { redis -> userPrefsPage(contentSrc(), fetchUser(), redis) }
  }

  post(USER_PREFS_ENDPOINT) {
    respondWithSuspendingRedisCheck { redis -> userPrefs(contentSrc(), fetchUser(), redis) }
  }

  get(TEACHER_PREFS_ENDPOINT, metrics) {
    respondWithRedisCheck { redis -> teacherPrefsPage(contentSrc(), fetchUser(), redis) }
  }

  post(TEACHER_PREFS_ENDPOINT) {
    respondWithSuspendingRedisCheck { redis -> teacherPrefs(contentSrc(), fetchUser(), redis) }
  }

  get(SYSTEM_ADMIN_ENDPOINT, metrics) {
    respondWithRedisCheck { redis -> systemAdminPage(contentSrc(), fetchUser(), redis) }
  }

  get(CLASS_SUMMARY_ENDPOINT, metrics) {
    metrics.measureEndpointRequest(CLASS_SUMMARY_ENDPOINT) {
      respondWithRedisCheck { redis -> classSummaryPage(contentSrc(), fetchUser(), redis) }
    }
  }

  post(CLASS_SUMMARY_ENDPOINT) {
    respondWithSuspendingRedisCheck { redis -> teacherPrefs(contentSrc(), fetchUser(), redis) }
  }

  get(STUDENT_SUMMARY_ENDPOINT, metrics) {
    metrics.measureEndpointRequest(STUDENT_SUMMARY_ENDPOINT) {
      respondWithRedisCheck { redis -> studentSummaryPage(contentSrc(), fetchUser(), redis) }
    }
  }

  get(ENABLE_STUDENT_MODE_ENDPOINT, metrics) {
    respondWithSuspendingRedisCheck { redis -> enableStudentMode(fetchUser(), redis) }
  }

  get(ENABLE_TEACHER_MODE_ENDPOINT, metrics) {
    respondWithSuspendingRedisCheck { redis -> enableTeacherMode(fetchUser(), redis) }
  }

  get(USER_INFO_ENDPOINT, metrics) {
    respondWithRedisCheck { redis -> userInfoPage(contentSrc(), fetchUser(), redis = redis) }
  }

  get(ADMIN_ENDPOINT, metrics) {
    respondWithRedisCheck { redis -> adminDataPage(contentSrc(), fetchUser(), redis = redis) }
  }

  post(ADMIN_ENDPOINT) {
    metrics.measureEndpointRequest(ADMIN_ENDPOINT) {
      respondWithSuspendingRedisCheck { redis -> adminActions(contentSrc(), fetchUser(), redis) }
    }
  }

  // RESET_ID is passed here when user clicks on email URL
  get(PASSWORD_RESET_ENDPOINT, metrics) {
    respondWithRedisCheck { redis -> passwordResetPage(contentSrc(), ResetId(queryParam(RESET_ID_PARAM)), redis) }
  }

  post(PASSWORD_RESET_ENDPOINT) {
    respondWithSuspendingRedisCheck { redis -> sendPasswordReset(contentSrc(), redis) }
  }

  post(PASSWORD_CHANGE_ENDPOINT) {
    respondWithSuspendingRedisCheck { redis -> changePassword(contentSrc(), redis) }
  }

  get(LOGOUT_ENDPOINT, metrics) {
    // Purge UserPrincipal from cookie data
    call.sessions.clear<UserPrincipal>()
    redirectTo { queryParam(RETURN_PARAM, "/") }
  }

  get(CSS_ENDPOINT) {
    respondWith(CSS) { cssContent }
  }

  get(FAV_ICON_ENDPOINT) {
    redirectTo { pathOf(STATIC_ROOT, ICONS, "favicon.ico") }
  }

  get(ROBOTS_ENDPOINT) {
    respondWith(ContentType.Text.Plain) { robotsTxt }
  }
}

private object StaticVals {
  val robotsTxt by lazy { getResourceAsText("/static$ROBOTS_ENDPOINT") }
}

object ResourceContent {
  fun getResourceAsText(path: String) = ResourceContent::class.java.getResource(path).readText()
}

