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

import com.github.readingbat.config.production
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.Endpoints.CLASSROOM
import kotlinx.html.*
import kotlinx.html.stream.createHTML

internal fun classroomPage(content: ReadingBatContent) =
  createHTML()
    .html {

      head {
        headDefault(content)
      }

      body {
        bodyTitle()

        div {
          h2 {
            +"About ReadingBat"
          }

          val wsid = "ws-output"
          script {
            rawHtml(
              """
                //var ws = new WebSocket('ws://0.0.0.0:8080/$CLASSROOM');
                var HOST = location.href.replace(${if (production) "/^https:/, 'wss:'" else "/^http:/, 'ws:'"})
                var ws = new WebSocket(HOST);
                var el;
                ws.onopen = function (event) {
                  ws.send("Hello!"); 
                };
                ws.onmessage = function (event) {
                  el = document.getElementById('$wsid');
                  el.innerHTML = 'Server time: ' + event.data;
                };
              """.trimIndent())
          }

          p {
            id = wsid
          }
        }
      }
    }