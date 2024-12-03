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

import com.github.readingbat.common.Constants.PING_CODE
import com.github.readingbat.common.Endpoints.CLOCK_ENDPOINT
import com.github.readingbat.common.Endpoints.WS_ROOT
import com.github.readingbat.pages.PageUtils.rawHtml
import kotlinx.html.body
import kotlinx.html.html
import kotlinx.html.id
import kotlinx.html.p
import kotlinx.html.script
import kotlinx.html.span
import kotlinx.html.stream.createHTML

internal object ClockPage {
  private const val PING_MSG = "pingMsg"

  fun clockPage() =
    createHTML()
      .html {
        body {
          p {
            +"Connection time: "
            span { id = PING_MSG }
          }

          script {
            rawHtml(
              """
                var wshost = location.origin;
                if (wshost.startsWith('https:'))
                  wshost = wshost.replace(/^https:/, 'wss:');
                else
                  wshost = wshost.replace(/^http:/, 'ws:');

                var wsurl = wshost + '$WS_ROOT$CLOCK_ENDPOINT';
                var ws = new WebSocket(wsurl);

                ws.onopen = function (event) {
                  ws.send("start");
                };

                ws.onmessage = function (event) {
                  var obj = JSON.parse(event.data);
                  if (obj.hasOwnProperty("type") && obj.type == "$PING_CODE") {
                    document.getElementById('$PING_MSG').innerText = obj.msg;
                  }
                };
              """.trimIndent(),
            )
          }
        }
      }
}
