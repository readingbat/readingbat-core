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

import com.github.pambrose.common.redis.RedisUtils.withRedisPool
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.Constants.DBMS_DOWN
import com.github.readingbat.misc.Endpoints.ADMIN_ACTIONS
import com.github.readingbat.misc.FormFields.ADMIN_ACTION
import com.github.readingbat.misc.FormFields.DELETE_ALL_DATA
import com.github.readingbat.pages.PageCommon.backLink
import com.github.readingbat.pages.PageCommon.bodyTitle
import com.github.readingbat.pages.PageCommon.displayMessage
import com.github.readingbat.pages.PageCommon.headDefault
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.fetchPrincipal
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import redis.clients.jedis.exceptions.JedisDataException

internal object AdminPage {

  fun PipelineCall.adminDataPage(content: ReadingBatContent, msg: String = "", isErrorMsg: Boolean = true) =
    createHTML()
      .html {
        val principal = fetchPrincipal()
        head { headDefault(content) }
        body {
          bodyTitle()
          if (principal == null) {
            br
            +"Must be logged in for this function"
          }
          else {
            p {
              span {
                style = "color:${if (isErrorMsg) "red" else "green"};"
                this@body.displayMessage(msg)
              }
            }

            deleteData()
            br
            dumpData()
          }
          br
          backLink("/")
        }
      }

  private fun BODY.deleteData() {
    h3 { +"Delete All Data" }
    div {
      style = "margin-left: 1em;"
      p { +"Permanently delete all data -- cannot be undone!" }
      form {
        action = ADMIN_ACTIONS
        method = FormMethod.post
        onSubmit = "return confirm('Are you sure you want to permanently delete all data ?');"
        input { type = InputType.submit; name = ADMIN_ACTION; value = DELETE_ALL_DATA }
      }
    }
  }

  private fun BODY.dumpData() {
    h3 { +"All Data" }
    br
    div {
      style = "margin-left: 1em;"

      withRedisPool { redis ->
        if (redis == null) {
          +DBMS_DOWN
        }
        else {
          val keys = redis.keys("*")
          h4 { +"${keys.size} Items:" }
          table {
            keys
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


  }
}