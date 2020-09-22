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

package com.github.readingbat.pages

import com.github.readingbat.common.Endpoints.ADMIN_PREFS_ENDPOINT
import com.github.readingbat.common.Endpoints.DELETE_CONTENT_IN_REDIS_ENDPOINT
import com.github.readingbat.common.Endpoints.GARBAGE_COLLECTOR_ENDPOINT
import com.github.readingbat.common.Endpoints.LOAD_ALL_ENDPOINT
import com.github.readingbat.common.Endpoints.LOAD_JAVA_ENDPOINT
import com.github.readingbat.common.Endpoints.LOAD_KOTLIN_ENDPOINT
import com.github.readingbat.common.Endpoints.LOAD_PYTHON_ENDPOINT
import com.github.readingbat.common.Endpoints.RESET_CACHE_ENDPOINT
import com.github.readingbat.common.Endpoints.RESET_CONTENT_DSL_ENDPOINT
import com.github.readingbat.common.FormFields.RETURN_PARAM
import com.github.readingbat.common.Message
import com.github.readingbat.common.Property.GRAFANA_URL
import com.github.readingbat.common.Property.PROMETHEUS_URL
import com.github.readingbat.common.User
import com.github.readingbat.common.User.Companion.fetchActiveClassCode
import com.github.readingbat.common.isAdminUser
import com.github.readingbat.common.isValidUser
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.dsl.isProduction
import com.github.readingbat.pages.HelpAndLogin.helpAndLogin
import com.github.readingbat.pages.PageUtils.backLink
import com.github.readingbat.pages.PageUtils.bodyTitle
import com.github.readingbat.pages.PageUtils.confirmingButton
import com.github.readingbat.pages.PageUtils.displayMessage
import com.github.readingbat.pages.PageUtils.headDefault
import com.github.readingbat.pages.PageUtils.loadStatusPageDisplay
import com.github.readingbat.pages.UserPrefsPage.requestLogInPage
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.ServerUtils.queryParam
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import mu.KLogging
import redis.clients.jedis.Jedis

internal object SystemAdminPage : KLogging() {

  fun PipelineCall.systemAdminPage(content: ReadingBatContent,
                                   user: User?,
                                   redis: Jedis,
                                   msg: String = "") =
    if (user.isValidUser(redis))
      systemAdminLoginPage(content, user, Message(msg), redis)
    else
      requestLogInPage(content, redis)

  private fun PipelineCall.systemAdminLoginPage(content: ReadingBatContent,
                                                user: User,
                                                msg: Message,
                                                redis: Jedis) =
    createHTML()
      .html {
        head { headDefault(content) }

        body {
          val activeClassCode = user.fetchActiveClassCode(redis)
          val returnPath = queryParam(RETURN_PARAM, "/")

          helpAndLogin(content, user, returnPath, activeClassCode.isEnabled, redis)
          bodyTitle()

          h2 { +"System Admin" }

          if (msg.isAssigned())
            p { span { style = "color:${msg.color}"; this@body.displayMessage(msg) } }

          if (!isProduction() || user.isAdminUser(redis)) {
            p {
              this@body.confirmingButton("Reset ReadingBat Content",
                                         RESET_CONTENT_DSL_ENDPOINT,
                                         "Are you sure you want to reset the content? (This can take a while)")
            }

            p {
              this@body.confirmingButton("Reset Challenges Cache",
                                         RESET_CACHE_ENDPOINT,
                                         "Are you sure you want to reset the challenges cache?")
            }

            p {
              this@body.confirmingButton("Delete all content cached in Redis",
                                         DELETE_CONTENT_IN_REDIS_ENDPOINT,
                                         "Are you sure you want to delete all content cached in Redis?")
            }

            p {
              this@body.confirmingButton("Load Java Challenges",
                                         LOAD_JAVA_ENDPOINT,
                                         "Are you sure you want to load all the java challenges? (This can take a while)")
            }

            p {
              this@body.confirmingButton("Load Python Challenges",
                                         LOAD_PYTHON_ENDPOINT,
                                         "Are you sure you want to load all the python challenges? (This can take a while)")
            }

            p {
              this@body.confirmingButton("Load Kotlin Challenges",
                                         LOAD_KOTLIN_ENDPOINT,
                                         "Are you sure you want to load all the kotlin challenges? (This can take a while)")
            }

            p {
              this@body.confirmingButton("Load all Challenges",
                                         LOAD_ALL_ENDPOINT,
                                         "Are you sure you want to load all the challenges? (This can take a while)")
            }

            p {
              this@body.confirmingButton("Run Garbage Collector",
                                         GARBAGE_COLLECTOR_ENDPOINT,
                                         "Are you sure you want to run the garbage collector?")
            }

            GRAFANA_URL.getPropertyOrNull()
              ?.also {
                if (it.isNotBlank()) p { +"Grafana Dashboard is "; a { href = it; target = "_blank"; +"here" } }
              }

            PROMETHEUS_URL.getPropertyOrNull()
              ?.also {
                if (it.isNotBlank()) p { +"Prometheus Dashboard is "; a { href = it; target = "_blank"; +"here" } }
              }
          }
          else {
            p { +"Not authorized" }
          }

          backLink("$ADMIN_PREFS_ENDPOINT?$RETURN_PARAM=${queryParam(RETURN_PARAM, "/")}")

          loadStatusPageDisplay()
        }
      }
}