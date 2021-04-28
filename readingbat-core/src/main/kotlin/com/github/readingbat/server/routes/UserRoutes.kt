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

package com.github.readingbat.server.routes

import com.github.pambrose.common.response.redirectTo
import com.github.pambrose.common.response.respondWith
import com.github.pambrose.common.util.pathOf
import com.github.readingbat.common.Constants.ICONS
import com.github.readingbat.common.Endpoints.ABOUT_ENDPOINT
import com.github.readingbat.common.Endpoints.ADMIN_ENDPOINT
import com.github.readingbat.common.Endpoints.ADMIN_PREFS_ENDPOINT
import com.github.readingbat.common.Endpoints.CHALLENGE_ROOT
import com.github.readingbat.common.Endpoints.CHECK_ANSWERS_ENDPOINT
import com.github.readingbat.common.Endpoints.CLASS_SUMMARY_ENDPOINT
import com.github.readingbat.common.Endpoints.CLEAR_CHALLENGE_ANSWERS_ENDPOINT
import com.github.readingbat.common.Endpoints.CLEAR_GROUP_ANSWERS_ENDPOINT
import com.github.readingbat.common.Endpoints.CLOCK_ENDPOINT
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
import com.github.readingbat.pages.AdminPrefsPage.adminPrefsPage
import com.github.readingbat.pages.ClassSummaryPage.classSummaryPage
import com.github.readingbat.pages.ClockPage.clockPage
import com.github.readingbat.pages.CreateAccountPage.createAccountPage
import com.github.readingbat.pages.HelpPage.helpPage
import com.github.readingbat.pages.PasswordResetPage.passwordResetPage
import com.github.readingbat.pages.PrivacyPage.privacyPage
import com.github.readingbat.pages.SessionsPage.sessionsPage
import com.github.readingbat.pages.StudentSummaryPage.studentSummaryPage
import com.github.readingbat.pages.SystemAdminPage.systemAdminPage
import com.github.readingbat.pages.SystemConfigurationPage.systemConfigurationPage
import com.github.readingbat.pages.TeacherPrefsPage.teacherPrefsPage
import com.github.readingbat.pages.UserInfoPage.userInfoPage
import com.github.readingbat.pages.UserPrefsPage.userPrefsPage
import com.github.readingbat.posts.AdminPost.adminActions
import com.github.readingbat.posts.ChallengePost.checkAnswers
import com.github.readingbat.posts.ChallengePost.clearChallengeAnswers
import com.github.readingbat.posts.ChallengePost.clearGroupAnswers
import com.github.readingbat.posts.ChallengePost.likeDislike
import com.github.readingbat.posts.CreateAccountPost.createAccount
import com.github.readingbat.posts.PasswordResetPost.sendPasswordReset
import com.github.readingbat.posts.PasswordResetPost.updatePassword
import com.github.readingbat.posts.TeacherPrefsPost.enableStudentMode
import com.github.readingbat.posts.TeacherPrefsPost.enableTeacherMode
import com.github.readingbat.posts.TeacherPrefsPost.teacherPrefs
import com.github.readingbat.posts.UserPrefsPost.userPrefs
import com.github.readingbat.server.ResetId
import com.github.readingbat.server.ServerUtils.authenticateAdminUser
import com.github.readingbat.server.ServerUtils.defaultLanguageTab
import com.github.readingbat.server.ServerUtils.fetchUser
import com.github.readingbat.server.ServerUtils.get
import com.github.readingbat.server.ServerUtils.queryParam
import com.github.readingbat.server.ServerUtils.respondWithRedirect
import com.github.readingbat.server.ServerUtils.respondWithRedisCheck
import com.github.readingbat.server.ServerUtils.respondWithSuspendingRedirect
import com.github.readingbat.server.ServerUtils.respondWithSuspendingRedisCheck
import com.github.readingbat.server.routes.ResourceContent.getResourceAsText
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.ContentType.Text.CSS
import io.ktor.routing.*
import io.ktor.sessions.*
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration

@Suppress("unused")
fun Route.routeTimeout(time: Duration, callback: Route.() -> Unit): Route {
  // With createChild, we create a child node for this received Route
  val routeWithTimeout = this.createChild(object : RouteSelector(1.0) {
    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation =
      RouteSelectorEvaluation.Constant
  })

  // Intercepts calls from this route at the features step
  routeWithTimeout.intercept(ApplicationCallPipeline.Features) {
    withTimeout(time.toLongMilliseconds()) {
      proceed()
    }
  }

  // Configure this route with the block provided by the user
  callback(routeWithTimeout)

  return routeWithTimeout
}

fun Routing.userRoutes(metrics: Metrics, contentSrc: () -> ReadingBatContent) {

  route(ROOT) {
    get {
      redirectTo { defaultLanguageTab(contentSrc(), fetchUser()) }
    }

    post {
      redirectTo { defaultLanguageTab(contentSrc(), fetchUser()) }
    }
  }

  route(CHALLENGE_ROOT) {
    get {
      redirectTo { defaultLanguageTab(contentSrc(), fetchUser()) }
    }

    post {
      redirectTo { defaultLanguageTab(contentSrc(), fetchUser()) }
    }
  }

  get(CONFIG_ENDPOINT) {
    respondWith {
      authenticateAdminUser(fetchUser()) { systemConfigurationPage(contentSrc()) }
    }
  }

  get(SESSIONS_ENDPOINT) {
    respondWith {
      authenticateAdminUser(fetchUser()) { sessionsPage() }
    }
  }

  get(PRIVACY_ENDPOINT) {
    respondWith { privacyPage() }
  }

  get(ABOUT_ENDPOINT) {
    respondWith { aboutPage(contentSrc(), fetchUser()) }
  }

  get(HELP_ENDPOINT) {
    respondWith { helpPage(contentSrc(), fetchUser()) }
  }

  get(CLOCK_ENDPOINT) {
    respondWith { clockPage() }
  }

  post(CHECK_ANSWERS_ENDPOINT) {
    metrics.measureEndpointRequest(CHECK_ANSWERS_ENDPOINT) { checkAnswers(contentSrc(), fetchUser()) }
  }

  post(LIKE_DISLIKE_ENDPOINT) {
    metrics.measureEndpointRequest(LIKE_DISLIKE_ENDPOINT) { likeDislike(fetchUser()) }
  }

  post(CLEAR_GROUP_ANSWERS_ENDPOINT) {
    respondWithSuspendingRedirect { clearGroupAnswers(contentSrc(), fetchUser()) }
  }

  post(CLEAR_CHALLENGE_ANSWERS_ENDPOINT) {
    respondWithSuspendingRedirect { clearChallengeAnswers(contentSrc(), fetchUser()) }
  }

  get(CREATE_ACCOUNT_ENDPOINT, metrics) {
    respondWith { createAccountPage() }
  }

  post(CREATE_ACCOUNT_ENDPOINT) {
    respondWithSuspendingRedirect { createAccount() }
  }

  get(ADMIN_PREFS_ENDPOINT) {
    respondWith { fetchUser().let { authenticateAdminUser(it) { adminPrefsPage(contentSrc(), it) } } }
  }

  get(USER_PREFS_ENDPOINT, metrics) {
    respondWith { userPrefsPage(contentSrc(), fetchUser()) }
  }

  post(USER_PREFS_ENDPOINT) {
    respondWith { userPrefs(contentSrc(), fetchUser()) }
  }

  get(TEACHER_PREFS_ENDPOINT, metrics) {
    respondWith { teacherPrefsPage(contentSrc(), fetchUser()) }
  }

  post(TEACHER_PREFS_ENDPOINT) {
    respondWith { teacherPrefs(contentSrc(), fetchUser()) }
  }

  get(SYSTEM_ADMIN_ENDPOINT, metrics) {
    respondWith { fetchUser().let { authenticateAdminUser(it) { systemAdminPage(contentSrc(), it) } } }
  }

  get(CLASS_SUMMARY_ENDPOINT, metrics) {
    metrics.measureEndpointRequest(CLASS_SUMMARY_ENDPOINT) {
      respondWith { classSummaryPage(contentSrc(), fetchUser()) }
    }
  }

  post(CLASS_SUMMARY_ENDPOINT) {
    respondWith { teacherPrefs(contentSrc(), fetchUser()) }
  }

  get(STUDENT_SUMMARY_ENDPOINT, metrics) {
    metrics.measureEndpointRequest(STUDENT_SUMMARY_ENDPOINT) {
      respondWith { studentSummaryPage(contentSrc(), fetchUser()) }
    }
  }

  get(ENABLE_STUDENT_MODE_ENDPOINT, metrics) {
    respondWithRedirect { enableStudentMode(fetchUser()) }
  }

  get(ENABLE_TEACHER_MODE_ENDPOINT, metrics) {
    respondWithRedirect { enableTeacherMode(fetchUser()) }
  }

  get(USER_INFO_ENDPOINT, metrics) {
    respondWith { userInfoPage(contentSrc(), fetchUser()) }
  }

  get(ADMIN_ENDPOINT, metrics) {
    respondWithRedisCheck { redis -> adminDataPage(contentSrc(), fetchUser(), redis = redis) }
  }

  post(ADMIN_ENDPOINT) {
    metrics.measureEndpointRequest(ADMIN_ENDPOINT) {
      respondWithSuspendingRedisCheck { redis -> adminActions(contentSrc(), fetchUser(), redis) }
    }
  }

  post(PASSWORD_CHANGE_ENDPOINT) {
    respondWithSuspendingRedirect { updatePassword() }
  }

  post(PASSWORD_RESET_ENDPOINT) {
    respondWithSuspendingRedirect { sendPasswordReset() }
  }

  // RESET_ID is passed here when user clicks on email URL
  get(PASSWORD_RESET_ENDPOINT, metrics) {
    respondWith { passwordResetPage(ResetId(queryParam(RESET_ID_PARAM))) }
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
    respondWith(ContentType.Text.Plain) { getResourceAsText("/static$ROBOTS_ENDPOINT") }
  }
}

object ResourceContent {
  fun getResourceAsText(path: String) = ResourceContent::class.java.getResource(path).readText()
}

