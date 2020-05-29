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

import com.github.pambrose.common.redis.RedisUtils
import com.github.pambrose.common.util.randomId
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.Dashboards.classCodeEnrollmentKey
import com.github.readingbat.misc.RedisDownException
import com.github.readingbat.pages.PageCommon.backLink
import com.github.readingbat.pages.PageCommon.bodyTitle
import com.github.readingbat.pages.PageCommon.headDefault
import com.github.readingbat.pages.PageCommon.rawHtml
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.ServerUtils.fetchPrincipal
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import redis.clients.jedis.exceptions.JedisException

internal object ClassroomPage {

  fun classroomPage(content: ReadingBatContent) =
    createHTML()
      .html {

        head { headDefault(content) }

        body {
          bodyTitle()

          div {
            h2 { +"ReadingBat Challenge Dashboard" }

            val wsid = "ws-output"
            script {
              rawHtml(
                """
                var HOST = location.href.replace(${if (content.production) "/^https:/, 'wss:'" else "/^http:/, 'ws:'"})
                var ws = new WebSocket(HOST);
                var el;
                ws.onopen = function (event) {
                  ws.send("abcde"); 
                };
                ws.onmessage = function (event) {
                  el = document.getElementById('$wsid');
                  el.innerHTML = 'Server time: ' + event.data;
                };
              """.trimIndent())
            }

            p { id = wsid }
          }
        }
      }

  fun PipelineCall.createClass(content: ReadingBatContent) =
    createHTML()
      .html {
        head { headDefault(content) }
        body {
          bodyTitle()
          h2 { +"Create Class Code" }

          br

          div {
            style = "margin-left: 1em;"

            val principal = fetchPrincipal()
            if (principal == null) {
              +"Must be logged in to create a class code"
            }
            else {
              +try {
                "The new class code is: ${createClass()}"
              } catch (e: JedisException) {
                "Unable to create class: ${e.message ?: ""}"
              }
            }
          }

          br
          backLink("/")
        }
      }

  private fun createClass() =
    RedisUtils.withRedisPool { redis ->
      if (redis == null)
        throw RedisDownException()
      val classCode = randomId(15)
      val classCodeEnrollmentKey = classCodeEnrollmentKey(classCode)
      redis.sadd(classCodeEnrollmentKey, "")
      classCode
    }

}