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

package com.readingbat.server.routes

import com.pambrose.common.response.redirectTo
import com.pambrose.common.response.respondWith
import com.pambrose.common.util.pathOf
import com.readingbat.common.Constants.ICONS
import com.readingbat.common.Endpoints.ABOUT_ENDPOINT
import com.readingbat.common.Endpoints.ADMIN_ENDPOINT
import com.readingbat.common.Endpoints.ADMIN_PREFS_ENDPOINT
import com.readingbat.common.Endpoints.CHALLENGE_ROOT
import com.readingbat.common.Endpoints.CHECK_ANSWERS_ENDPOINT
import com.readingbat.common.Endpoints.CLASS_SUMMARY_ENDPOINT
import com.readingbat.common.Endpoints.CLEAR_CHALLENGE_ANSWERS_ENDPOINT
import com.readingbat.common.Endpoints.CLEAR_GROUP_ANSWERS_ENDPOINT
import com.readingbat.common.Endpoints.CLOCK_ENDPOINT
import com.readingbat.common.Endpoints.CONFIG_ENDPOINT
import com.readingbat.common.Endpoints.ENABLE_STUDENT_MODE_ENDPOINT
import com.readingbat.common.Endpoints.ENABLE_TEACHER_MODE_ENDPOINT
import com.readingbat.common.Endpoints.FAV_ICON_ENDPOINT
import com.readingbat.common.Endpoints.HELP_ENDPOINT
import com.readingbat.common.Endpoints.LIKE_DISLIKE_ENDPOINT
import com.readingbat.common.Endpoints.LOGOUT_ENDPOINT
import com.readingbat.common.Endpoints.PRIVACY_POLICY_ENDPOINT
import com.readingbat.common.Endpoints.ROBOTS_ENDPOINT
import com.readingbat.common.Endpoints.ROOT
import com.readingbat.common.Endpoints.SESSIONS_ENDPOINT
import com.readingbat.common.Endpoints.STATIC_ROOT
import com.readingbat.common.Endpoints.STUDENT_SUMMARY_ENDPOINT
import com.readingbat.common.Endpoints.SYSTEM_ADMIN_ENDPOINT
import com.readingbat.common.Endpoints.TAILWIND_CSS_ENDPOINT
import com.readingbat.common.Endpoints.TEACHER_PREFS_ENDPOINT
import com.readingbat.common.Endpoints.TOS_ENDPOINT
import com.readingbat.common.Endpoints.USER_INFO_ENDPOINT
import com.readingbat.common.Endpoints.USER_PREFS_ENDPOINT
import com.readingbat.common.FormFields.RETURN_PARAM
import com.readingbat.common.Metrics
import com.readingbat.common.UserPrincipal
import com.readingbat.dsl.ReadingBatContent
import com.readingbat.pages.AboutPage.aboutPage
import com.readingbat.pages.AdminPage.adminDataPage
import com.readingbat.pages.AdminPrefsPage.adminPrefsPage
import com.readingbat.pages.ClassSummaryPage.classSummaryPage
import com.readingbat.pages.ClockPage.clockPage
import com.readingbat.pages.HelpPage.helpPage
import com.readingbat.pages.PrivacyPolicyPage.privacyPolicyPage
import com.readingbat.pages.SessionsPage.sessionsPage
import com.readingbat.pages.StudentSummaryPage.studentSummaryPage
import com.readingbat.pages.SystemAdminPage.systemAdminPage
import com.readingbat.pages.SystemConfigurationPage.systemConfigurationPage
import com.readingbat.pages.TOSPage.tosPage
import com.readingbat.pages.TeacherPrefsPage.teacherPrefsPage
import com.readingbat.pages.UserInfoPage.userInfoPage
import com.readingbat.pages.UserPrefsPage.userPrefsPage
import com.readingbat.posts.AdminPost.adminActions
import com.readingbat.posts.ChallengePost.checkAnswers
import com.readingbat.posts.ChallengePost.clearChallengeAnswers
import com.readingbat.posts.ChallengePost.clearGroupAnswers
import com.readingbat.posts.ChallengePost.likeDislike
import com.readingbat.posts.TeacherPrefsPost.enableStudentMode
import com.readingbat.posts.TeacherPrefsPost.enableTeacherMode
import com.readingbat.posts.TeacherPrefsPost.teacherPrefs
import com.readingbat.posts.UserPrefsPost.userPrefs
import com.readingbat.server.ServerUtils.authenticateAdminUser
import com.readingbat.server.ServerUtils.defaultLanguageTab
import com.readingbat.server.ServerUtils.fetchUser
import com.readingbat.server.ServerUtils.get
import com.readingbat.server.ServerUtils.queryParam
import com.readingbat.server.ServerUtils.respondWithPageResult
import com.readingbat.server.ServerUtils.safeRedirectPath
import com.readingbat.server.routes.ResourceContent.getResourceAsText
import io.ktor.http.ContentType
import io.ktor.http.ContentType.Text.CSS
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.sessions

// @Suppress("unused")
// fun Route.routeTimeout(time: Duration, callback: Route.() -> Unit): Route {
//  // With createChild, we create a child node for this received Route
//  val routeWithTimeout =
//    this.createChild(
//      object : RouteSelector() {
//        override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int) =
//          RouteSelectorEvaluation.Constant
//      },
//    )
//
//  // Intercepts calls from this route at the features step
//  routeWithTimeout.intercept(ApplicationCallPipeline.Plugins) {
//    withTimeout(time.inWholeMilliseconds) {
//      proceed()
//    }
//  }
//
//  // Configure this route with the block provided by the user
//  callback(routeWithTimeout)
//
//  return routeWithTimeout
// }

/**
 * Registers all user-facing HTTP routes.
 *
 * Includes the root redirect, content root redirect, public pages (about, help, privacy, TOS, clock),
 * challenge answer submission and clearing, user/teacher/admin preference pages, class and student
 * summary pages, logout, favicon, robots.txt, and Tailwind CSS serving.
 */
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
    respondWith { authenticateAdminUser(fetchUser()) { systemConfigurationPage(contentSrc()) } }
  }

  get(SESSIONS_ENDPOINT) {
    respondWith { authenticateAdminUser(fetchUser()) { sessionsPage() } }
  }

  get(PRIVACY_POLICY_ENDPOINT) {
    respondWith { privacyPolicyPage() }
  }

  get(TOS_ENDPOINT) {
    respondWith { tosPage() }
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
    respondWithPageResult { clearGroupAnswers(contentSrc(), fetchUser()) }
  }

  post(CLEAR_CHALLENGE_ANSWERS_ENDPOINT) {
    respondWithPageResult { clearChallengeAnswers(contentSrc(), fetchUser()) }
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
    respondWithPageResult { enableStudentMode(fetchUser()) }
  }

  get(ENABLE_TEACHER_MODE_ENDPOINT, metrics) {
    respondWithPageResult { enableTeacherMode(fetchUser()) }
  }

  get(USER_INFO_ENDPOINT, metrics) {
    respondWith { userInfoPage(contentSrc(), fetchUser()) }
  }

  get(ADMIN_ENDPOINT, metrics) {
    respondWith { adminDataPage(contentSrc(), fetchUser()) }
  }

  post(ADMIN_ENDPOINT) {
    metrics.measureEndpointRequest(ADMIN_ENDPOINT) {
      respondWith { adminActions(contentSrc(), fetchUser()) }
    }
  }

  get(LOGOUT_ENDPOINT, metrics) {
    // Purge UserPrincipal from cookie data
    call.sessions.clear<UserPrincipal>()
    redirectTo { safeRedirectPath(queryParam(RETURN_PARAM, "/")) }
  }

  get(TAILWIND_CSS_ENDPOINT) {
    respondWith(CSS) { getResourceAsText("/static/tailwind.css") }
  }

  get(FAV_ICON_ENDPOINT) {
    redirectTo { pathOf(STATIC_ROOT, ICONS, "favicon.ico") }
  }

  get(ROBOTS_ENDPOINT) {
    respondWith(ContentType.Text.Plain) { getResourceAsText("/static$ROBOTS_ENDPOINT") }
  }
}

/** Helper for reading classpath resources as text strings. */
object ResourceContent {
  fun getResourceAsText(path: String) = ResourceContent::class.java.getResource(path).readText()
}
