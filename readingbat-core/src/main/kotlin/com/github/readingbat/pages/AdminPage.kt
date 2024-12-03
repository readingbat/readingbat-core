/*
 * Copyright © 2023 Paul Ambrose (pambrose@mac.com)
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

import com.github.pambrose.common.redis.RedisUtils.scanKeys
import com.github.readingbat.common.CssNames.INDENT_1EM
import com.github.readingbat.common.Endpoints.ADMIN_ENDPOINT
import com.github.readingbat.common.FormFields.ADMIN_ACTION_PARAM
import com.github.readingbat.common.FormFields.DELETE_ALL_DATA
import com.github.readingbat.common.FormFields.RETURN_PARAM
import com.github.readingbat.common.Message
import com.github.readingbat.common.Message.Companion.EMPTY_MESSAGE
import com.github.readingbat.common.User
import com.github.readingbat.common.isNotAdminUser
import com.github.readingbat.common.isNotValidUser
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.dsl.isProduction
import com.github.readingbat.pages.HelpAndLogin.helpAndLogin
import com.github.readingbat.pages.PageUtils.backLink
import com.github.readingbat.pages.PageUtils.bodyTitle
import com.github.readingbat.pages.PageUtils.displayMessage
import com.github.readingbat.pages.PageUtils.headDefault
import com.github.readingbat.pages.PageUtils.loadPingdomScript
import com.github.readingbat.server.ServerUtils.queryParam
import io.ktor.server.routing.RoutingContext
import kotlinx.html.BODY
import kotlinx.html.FormMethod
import kotlinx.html.body
import kotlinx.html.br
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h3
import kotlinx.html.h4
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.onSubmit
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.stream.createHTML
import kotlinx.html.style
import kotlinx.html.submitInput
import kotlinx.html.table
import kotlinx.html.td
import kotlinx.html.tr
import redis.clients.jedis.Jedis
import redis.clients.jedis.exceptions.JedisDataException

internal object AdminPage {
  fun RoutingContext.adminDataPage(
    content: ReadingBatContent,
    user: User?,
    redis: Jedis,
    msg: Message = EMPTY_MESSAGE,
  ) =
    createHTML()
      .html {
        head { headDefault() }

        body {
          val returnPath = queryParam(RETURN_PARAM, "/")

          helpAndLogin(content, user, returnPath, false)
          bodyTitle()

          when {
            isProduction() && user.isNotValidUser() -> {
              br { +"Must be logged in for this function" }
            }

            isProduction() && user.isNotAdminUser() -> {
              br { +"Must be a system admin for this function" }
            }

            else -> {
              if (msg.isAssigned())
                p {
                  span {
                    style = "color:${msg.color}"
                    this@body.displayMessage(msg)
                  }
                }
              p { this@body.deleteData() }
              p { this@body.dumpData(redis) }
            }
          }

          backLink(returnPath)
          loadPingdomScript()
        }
      }

  private fun BODY.deleteData() {
    h3 { +"Delete All Data" }
    div(classes = INDENT_1EM) {
      p { +"Permanently delete all data -- this cannot be undone!" }
      form {
        action = ADMIN_ENDPOINT
        method = FormMethod.post
        onSubmit = "return confirm('Are you sure you want to permanently delete all data ?')"
        submitInput {
          name = ADMIN_ACTION_PARAM
          value = DELETE_ALL_DATA
        }
      }
    }
  }

  private fun BODY.dumpData(redis: Jedis) {
    h3 { +"All Data" }
    br
    div(classes = INDENT_1EM) {
      h4 { +"${redis.scanKeys("*").count()} items:" }
      table {
        redis.scanKeys("*")
          .sorted()
          .map {
            try {
              it to redis[it]
            } catch (e: JedisDataException) {
              try {
                it to redis.hgetAll(it).toString()
              } catch (e: JedisDataException) {
                it to redis.smembers(it).toString()
              }
            }
          }
          .forEach {
            tr {
              td { +it.first }
              td { +it.second }
            }
          }
      }
    }
  }
}
