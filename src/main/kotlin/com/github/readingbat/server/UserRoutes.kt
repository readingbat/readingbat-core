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
import com.github.readingbat.misc.Endpoints.CHECK_ANSWERS_ROOT
import com.github.readingbat.misc.Endpoints.CREATE_ACCOUNT_ENDPOINT
import com.github.readingbat.misc.Endpoints.CSS_ENDPOINT
import com.github.readingbat.misc.Endpoints.FAV_ICON
import com.github.readingbat.misc.Endpoints.PASSWORD_CHANGE_ENDPOINT
import com.github.readingbat.misc.Endpoints.PASSWORD_RESET_ENDPOINT
import com.github.readingbat.misc.Endpoints.PRIVACY_ENDPOINT
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
import com.github.readingbat.pages.UserPrefsPage.userPrefsPage
import com.github.readingbat.posts.Admin.adminActions
import com.github.readingbat.posts.CheckAnswers.checkAnswers
import com.github.readingbat.posts.CreateAccount.createAccount
import com.github.readingbat.posts.PasswordReset.changePassword
import com.github.readingbat.posts.PasswordReset.sendPasswordReset
import com.github.readingbat.posts.UserPrefs.userPrefs
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

  suspend fun PipelineCall.respondWithDbmsCheck(block: PipelineCall.(redis: Jedis) -> String) =
    respondWith {
      withRedisPool { redis ->
        if (redis == null)
          dbmsDownPage(content)
        else
          block(redis)
      }
    }

  suspend fun PipelineCall.respondWithSuspendingDbmsCheck(block: suspend PipelineCall.(redis: Jedis) -> String) =
    respondWith {
      withSuspendingRedisPool { redis ->
        if (redis == null)
          dbmsDownPage(content)
        else
          block(redis)
      }
    }

  get(ROOT) { redirectTo { defaultLanguageTab(content) } }

  get(CHALLENGE_ROOT) { redirectTo { defaultLanguageTab(content) } }

  get(PRIVACY_ENDPOINT) { respondWith { privacyPage(content) } }

  get(ABOUT_ENDPOINT) { respondWith { aboutPage(content) } }

  post(CHECK_ANSWERS_ROOT) { withSuspendingRedisPool { redis -> checkAnswers(content, redis) } }

  get(CREATE_ACCOUNT_ENDPOINT) { respondWith { createAccountPage(content) } }

  post(CREATE_ACCOUNT_ENDPOINT) {
    withSuspendingRedisPool { redis ->
      if (redis == null)
        dbmsDownPage(content)
      else
        createAccount(content, redis)
    }
  }

  get(USER_PREFS_ENDPOINT) { respondWithDbmsCheck { redis -> userPrefsPage(content, redis, "", false) } }

  post(USER_PREFS_ENDPOINT) { respondWithSuspendingDbmsCheck { redis -> userPrefs(content, redis) } }

  get(ADMIN_ENDPOINT) { respondWithDbmsCheck { redis -> adminDataPage(content, redis) } }

  post(ADMIN_ENDPOINT) { respondWithSuspendingDbmsCheck { redis -> adminActions(content, redis) } }

  // RESET_ID is passed here when user clicks on email URL
  get(PASSWORD_RESET_ENDPOINT) {
    respondWithDbmsCheck { redis -> passwordResetPage(content, redis, queryParam(RESET_ID) ?: "", "") }
  }

  post(PASSWORD_RESET_ENDPOINT) {
    withSuspendingRedisPool { redis ->
      if (redis == null)
        dbmsDownPage(content)
      else
        sendPasswordReset(content, redis)
    }
  }

  post(PASSWORD_CHANGE_ENDPOINT) {
    withSuspendingRedisPool { redis ->
      if (redis == null)
        dbmsDownPage(content)
      else
        changePassword(content, redis)
    }
  }

  get(LOGOUT) {
    // Purge UserPrincipal from cookie data
    call.sessions.clear<UserPrincipal>()
    redirectTo { queryParam(RETURN_PATH) ?: "/" }
  }

  get(CSS_ENDPOINT) { respondWith(CSS) { cssContent } }

  get(FAV_ICON) { redirectTo { "$STATIC_ROOT/$ICONS/favicon.ico" } }
}