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
import com.github.pambrose.common.time.format
import com.github.pambrose.common.util.isNull
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.dsl.isProduction
import com.github.readingbat.misc.Constants.ADMIN_USERS
import com.github.readingbat.misc.Constants.CHALLENGE_ROOT
import com.github.readingbat.misc.Constants.DBMS_DOWN
import com.github.readingbat.misc.Constants.ICONS
import com.github.readingbat.misc.Constants.MSG
import com.github.readingbat.misc.Constants.RESET_ID
import com.github.readingbat.misc.Constants.RETURN_PATH
import com.github.readingbat.misc.Constants.ROOT
import com.github.readingbat.misc.Constants.STATIC_ROOT
import com.github.readingbat.misc.Endpoints.ABOUT_ENDPOINT
import com.github.readingbat.misc.Endpoints.ADMIN_ENDPOINT
import com.github.readingbat.misc.Endpoints.ADMIN_POST_ENDPOINT
import com.github.readingbat.misc.Endpoints.CHECK_ANSWERS_ENDPOINT
import com.github.readingbat.misc.Endpoints.CLEAR_CHALLENGE_ANSWERS_ENDPOINT
import com.github.readingbat.misc.Endpoints.CLEAR_GROUP_ANSWERS_ENDPOINT
import com.github.readingbat.misc.Endpoints.CONFIG_ENDPOINT
import com.github.readingbat.misc.Endpoints.CREATE_ACCOUNT_ENDPOINT
import com.github.readingbat.misc.Endpoints.CREATE_ACCOUNT_POST_ENDPOINT
import com.github.readingbat.misc.Endpoints.CSS_ENDPOINT
import com.github.readingbat.misc.Endpoints.ENABLE_STUDENT_MODE_ENDPOINT
import com.github.readingbat.misc.Endpoints.ENABLE_TEACHER_MODE_ENDPOINT
import com.github.readingbat.misc.Endpoints.FAV_ICON_ENDPOINT
import com.github.readingbat.misc.Endpoints.LIKE_DISLIKE_ENDPOINT
import com.github.readingbat.misc.Endpoints.LOGOUT_ENDPOINT
import com.github.readingbat.misc.Endpoints.MESSAGE_ENDPOINT
import com.github.readingbat.misc.Endpoints.PASSWORD_CHANGE_POST_ENDPOINT
import com.github.readingbat.misc.Endpoints.PASSWORD_RESET_ENDPOINT
import com.github.readingbat.misc.Endpoints.PASSWORD_RESET_POST_ENDPOINT
import com.github.readingbat.misc.Endpoints.PRIVACY_ENDPOINT
import com.github.readingbat.misc.Endpoints.RESET_CONTENT_ENDPOINT
import com.github.readingbat.misc.Endpoints.RESET_MAPS_ENDPOINT
import com.github.readingbat.misc.Endpoints.TEACHER_PREFS_ENDPOINT
import com.github.readingbat.misc.Endpoints.TEACHER_PREFS_POST_ENDPOINT
import com.github.readingbat.misc.Endpoints.USER_INFO_ENDPOINT
import com.github.readingbat.misc.Endpoints.USER_PREFS_ENDPOINT
import com.github.readingbat.misc.Endpoints.USER_PREFS_POST_ENDPOINT
import com.github.readingbat.misc.UserPrincipal
import com.github.readingbat.misc.cssContent
import com.github.readingbat.pages.AboutPage.aboutPage
import com.github.readingbat.pages.AdminPage.adminDataPage
import com.github.readingbat.pages.ConfigPage.configPage
import com.github.readingbat.pages.CreateAccountPage.createAccountPage
import com.github.readingbat.pages.DbmsDownPage.dbmsDownPage
import com.github.readingbat.pages.MessagePage.messagePage
import com.github.readingbat.pages.PageCommon.defaultLanguageTab
import com.github.readingbat.pages.PasswordResetPage.passwordResetPage
import com.github.readingbat.pages.PrivacyPage.privacyPage
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
import com.github.readingbat.server.ServerUtils.fetchUser
import com.github.readingbat.server.ServerUtils.queryParam
import io.ktor.application.*
import io.ktor.http.ContentType.Text.CSS
import io.ktor.routing.*
import io.ktor.sessions.*
import io.ktor.util.pipeline.*
import redis.clients.jedis.Jedis
import kotlin.time.measureTime

internal fun Routing.userRoutes(metrics: Metrics,
                                content: () -> ReadingBatContent,
                                resetContentFunc: () -> Unit) {

  suspend fun PipelineCall.respondWithDbmsCheck(block: (redis: Jedis) -> String) =
    try {
      val html =
        withRedisPool { redis ->
          if (redis.isNull())
            dbmsDownPage(content.invoke())
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
          if (redis.isNull())
            dbmsDownPage(content.invoke())
          else
            block.invoke(redis)
        }
      respondWith { html }
    } catch (e: RedirectException) {
      redirectTo { e.redirectUrl }
    }

  get(ROOT) {
    metrics.measureEndpointRequest(ROOT) { redirectTo { defaultLanguageTab(content.invoke()) } }
  }

  get(MESSAGE_ENDPOINT) {
    metrics.measureEndpointRequest(MESSAGE_ENDPOINT) { respondWith { messagePage(content.invoke()) } }
  }

  suspend fun PipelineCall.protectedAction(msg: String, block: () -> String) =
    when {
      isProduction() ->
        withSuspendingRedisPool { redis ->
          val user = fetchUser()
          when {
            redis.isNull() -> DBMS_DOWN.value
            user.isNull() -> "Must be logged in to call $msg"
            user.email(redis).value !in ADMIN_USERS -> "Not authorized to call $msg"
            else -> block.invoke()
          }
        }
      else -> block.invoke()
    }

  fun Route.getAndPost(path: String, body: PipelineInterceptor<Unit, ApplicationCall>) {
    get(path, body)
    post(path, body)
  }

  get(RESET_CONTENT_ENDPOINT) {
    metrics.measureEndpointRequest(RESET_CONTENT_ENDPOINT) {
      val msg =
        protectedAction(RESET_CONTENT_ENDPOINT) {
          measureTime { resetContentFunc.invoke() }.let {
            "Content reset in ${it.format()}".also { ReadingBatServer.logger.info { it } }
          }
        }
      redirectTo { "$MESSAGE_ENDPOINT?$MSG=$msg" }
    }
  }

  get(RESET_MAPS_ENDPOINT) {
    metrics.measureEndpointRequest(RESET_MAPS_ENDPOINT) {
      val msg =
        protectedAction(RESET_MAPS_ENDPOINT) {
          content.invoke().clearSourcesMap().let {
            "Content maps reset".also { ReadingBatServer.logger.info { it } }
          }
        }
      redirectTo { "$MESSAGE_ENDPOINT?$MSG=$msg" }
    }
  }

  get(CHALLENGE_ROOT) {
    metrics.measureEndpointRequest(CHALLENGE_ROOT) {
      redirectTo { defaultLanguageTab(content.invoke()) }
    }
  }

  get(PRIVACY_ENDPOINT) {
    metrics.measureEndpointRequest(PRIVACY_ENDPOINT) {
      respondWith { privacyPage(content.invoke()) }
    }
  }

  get(ABOUT_ENDPOINT) {
    metrics.measureEndpointRequest(ABOUT_ENDPOINT) {
      respondWith { aboutPage(content.invoke()) }
    }
  }

  get(CONFIG_ENDPOINT) {
    metrics.measureEndpointRequest(CONFIG_ENDPOINT) {
      respondWith { configPage(content.invoke()) }
    }
  }

  post(CHECK_ANSWERS_ENDPOINT) {
    metrics.measureEndpointRequest(CHECK_ANSWERS_ENDPOINT) {
      withSuspendingRedisPool { redis -> checkAnswers(content.invoke(), fetchUser(), redis) }
    }
  }

  post(LIKE_DISLIKE_ENDPOINT) {
    metrics.measureEndpointRequest(LIKE_DISLIKE_ENDPOINT) {
      withSuspendingRedisPool { redis -> likeDislike(content.invoke(), fetchUser(), redis) }
    }
  }

  post(CLEAR_GROUP_ANSWERS_ENDPOINT) {
    metrics.measureEndpointRequest(CLEAR_GROUP_ANSWERS_ENDPOINT) {
      respondWithSuspendingDbmsCheck { redis -> clearGroupAnswers(content.invoke(), fetchUser(), redis) }
    }
  }

  post(CLEAR_CHALLENGE_ANSWERS_ENDPOINT) {
    metrics.measureEndpointRequest(CLEAR_CHALLENGE_ANSWERS_ENDPOINT) {
      respondWithSuspendingDbmsCheck { redis -> clearChallengeAnswers(content.invoke(), fetchUser(), redis) }
    }
  }

  get(CREATE_ACCOUNT_ENDPOINT) {
    metrics.measureEndpointRequest(CREATE_ACCOUNT_ENDPOINT) { respondWithDbmsCheck { createAccountPage(content.invoke()) } }
  }

  post(CREATE_ACCOUNT_POST_ENDPOINT) {
    metrics.measureEndpointRequest(CREATE_ACCOUNT_POST_ENDPOINT) {
      respondWithSuspendingDbmsCheck { redis -> createAccount(content.invoke(), redis) }
    }
  }

  get(USER_PREFS_ENDPOINT) {
    metrics.measureEndpointRequest(USER_PREFS_ENDPOINT) {
      respondWithDbmsCheck { redis -> userPrefsPage(content.invoke(), fetchUser(), redis) }
    }
  }

  post(USER_PREFS_POST_ENDPOINT) {
    metrics.measureEndpointRequest(USER_PREFS_POST_ENDPOINT) {
      respondWithSuspendingDbmsCheck { redis -> userPrefs(content.invoke(), fetchUser(), redis) }
    }
  }

  get(TEACHER_PREFS_ENDPOINT) {
    metrics.measureEndpointRequest(TEACHER_PREFS_ENDPOINT) {
      respondWithDbmsCheck { redis -> teacherPrefsPage(content.invoke(), fetchUser(), redis) }
    }
  }

  post(TEACHER_PREFS_POST_ENDPOINT) {
    metrics.measureEndpointRequest(TEACHER_PREFS_POST_ENDPOINT) {
      respondWithSuspendingDbmsCheck { redis -> teacherPrefs(content.invoke(), fetchUser(), redis) }
    }
  }

  get(ENABLE_STUDENT_MODE_ENDPOINT) {
    metrics.measureEndpointRequest(ENABLE_STUDENT_MODE_ENDPOINT) {
      respondWithSuspendingDbmsCheck { redis -> enableStudentMode(fetchUser(), redis) }
    }
  }

  get(ENABLE_TEACHER_MODE_ENDPOINT) {
    metrics.measureEndpointRequest(ENABLE_TEACHER_MODE_ENDPOINT) {
      respondWithSuspendingDbmsCheck { redis -> enableTeacherMode(fetchUser(), redis) }
    }
  }

  get(ADMIN_ENDPOINT) {
    metrics.measureEndpointRequest(ADMIN_ENDPOINT) {
      respondWithDbmsCheck { redis -> adminDataPage(content.invoke(), fetchUser(), redis = redis) }
    }
  }

  get(USER_INFO_ENDPOINT) {
    metrics.measureEndpointRequest(USER_INFO_ENDPOINT) {
      respondWithDbmsCheck { redis -> userInfoPage(content.invoke(), fetchUser(), redis = redis) }
    }
  }

  post(ADMIN_POST_ENDPOINT) {
    metrics.measureEndpointRequest(ADMIN_POST_ENDPOINT) {
      respondWithSuspendingDbmsCheck { redis -> adminActions(content.invoke(), fetchUser(), redis) }
    }
  }

  // RESET_ID is passed here when user clicks on email URL
  get(PASSWORD_RESET_ENDPOINT) {
    metrics.measureEndpointRequest(PASSWORD_RESET_ENDPOINT) {
      respondWithDbmsCheck { redis -> passwordResetPage(content.invoke(), ResetId(queryParam(RESET_ID)), redis) }
    }
  }

  post(PASSWORD_RESET_POST_ENDPOINT) {
    metrics.measureEndpointRequest(PASSWORD_RESET_POST_ENDPOINT) {
      respondWithSuspendingDbmsCheck { redis -> sendPasswordReset(content.invoke(), fetchUser(), redis) }
    }
  }

  post(PASSWORD_CHANGE_POST_ENDPOINT) {
    metrics.measureEndpointRequest(PASSWORD_CHANGE_POST_ENDPOINT) {
      respondWithSuspendingDbmsCheck { redis -> changePassword(content.invoke(), redis) }
    }
  }

  get(LOGOUT_ENDPOINT) {
    metrics.measureEndpointRequest(LOGOUT_ENDPOINT) {
      // Purge UserPrincipal from cookie data
      call.sessions.clear<UserPrincipal>()
      redirectTo { queryParam(RETURN_PATH, "/") }
    }
  }

  get(CSS_ENDPOINT) {
    respondWith(CSS) { cssContent }
  }

  get(FAV_ICON_ENDPOINT) {
    redirectTo { "$STATIC_ROOT/$ICONS/favicon.ico" }
  }
}