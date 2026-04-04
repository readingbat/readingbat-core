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

package com.readingbat.pages

import com.pambrose.common.util.randomId
import com.readingbat.common.Endpoints.ADMIN_PREFS_ENDPOINT
import com.readingbat.common.Endpoints.DELETE_CONTENT_IN_CONTENT_CACHE_ENDPOINT
import com.readingbat.common.Endpoints.GARBAGE_COLLECTOR_ENDPOINT
import com.readingbat.common.Endpoints.LOAD_ALL_ENDPOINT
import com.readingbat.common.Endpoints.LOGGING_ENDPOINT
import com.readingbat.common.Endpoints.RESET_CACHE_ENDPOINT
import com.readingbat.common.Endpoints.RESET_CONTENT_DSL_ENDPOINT
import com.readingbat.common.Endpoints.WS_ROOT
import com.readingbat.common.FormFields.RETURN_PARAM
import com.readingbat.common.Message
import com.readingbat.common.Property.GRAFANA_URL
import com.readingbat.common.Property.PROMETHEUS_URL
import com.readingbat.common.User
import com.readingbat.common.User.Companion.queryActiveTeachingClassCode
import com.readingbat.common.isAdminUser
import com.readingbat.common.isValidUser
import com.readingbat.dsl.ReadingBatContent
import com.readingbat.dsl.isProduction
import com.readingbat.pages.HelpAndLogin.helpAndLogin
import com.readingbat.pages.PageUtils.adminButton
import com.readingbat.pages.PageUtils.backLink
import com.readingbat.pages.PageUtils.bodyTitle
import com.readingbat.pages.PageUtils.displayMessage
import com.readingbat.pages.PageUtils.headDefault
import com.readingbat.pages.PageUtils.loadStatusPageDisplay
import com.readingbat.pages.PageUtils.rawHtml
import com.readingbat.pages.UserPrefsPage.requestLogInPage
import com.readingbat.pages.js.AdminCommandsJs.loadCommandsScript
import com.readingbat.server.ServerUtils.queryParam
import io.ktor.server.routing.RoutingContext
import kotlinx.html.BODY
import kotlinx.html.ScriptType
import kotlinx.html.body
import kotlinx.html.h2
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.id
import kotlinx.html.p
import kotlinx.html.script
import kotlinx.html.span
import kotlinx.html.stream.createHTML
import kotlinx.html.style
import kotlinx.html.textArea

/**
 * Generates the system admin page at `/systemadmin`.
 *
 * Provides buttons for administrative operations such as resetting content, clearing caches,
 * running the garbage collector, and loading all challenges. Includes a real-time WebSocket
 * log viewer for operation output. Requires admin privileges.
 */
internal object SystemAdminPage {
  private const val MSGS = "msgs"
  private const val STATUS = "status"

  fun RoutingContext.systemAdminPage(
    content: ReadingBatContent,
    user: User?,
    msg: String = "",
  ) =
    if (user.isValidUser())
      systemAdminLoginPage(content, user, Message(msg))
    else
      requestLogInPage(content)

  private fun RoutingContext.systemAdminLoginPage(content: ReadingBatContent, user: User, msg: Message) =
    createHTML()
      .html {
        val logId = randomId(10)

        head {
          headDefault()
          script(type = ScriptType.textJavaScript) { loadCommandsScript(logId) }
        }

        body {
          val returnPath = queryParam(RETURN_PARAM, "/")
          val activeTeachingClassCode = queryActiveTeachingClassCode(user)

          helpAndLogin(content, user, returnPath, activeTeachingClassCode.isEnabled)
          bodyTitle()

          h2 { +"System Admin" }

          if (msg.isAssigned())
            p {
              span {
                style = "color:${msg.color}"
                this@body.displayMessage(msg)
              }
            }

          if (!isProduction() || user.isAdminUser()) {
            p {
              this@body.adminButton(
                text = "Reset ReadingBat Content",
                endpoint = RESET_CONTENT_DSL_ENDPOINT,
                confirm = "Are you sure you want to reset the content? (This can take a while)",
              )
            }

            p {
              this@body.adminButton(
                text = "Reset Challenges Cache",
                endpoint = RESET_CACHE_ENDPOINT,
                confirm = "Are you sure you want to reset the challenges cache?",
              )
            }

            p {
              this@body.adminButton(
                text = "Delete all content in content cache",
                endpoint = DELETE_CONTENT_IN_CONTENT_CACHE_ENDPOINT,
                confirm = "Are you sure you want to delete all content cached in Content Cache?",
              )
            }

            p {
              this@body.adminButton(
                text = "Run Garbage Collector",
                endpoint = GARBAGE_COLLECTOR_ENDPOINT,
                confirm = "Are you sure you want to run the garbage collector?",
              )
            }

//            p {
//              this@body.adminButton("Load Java Challenges",
//                                    LOAD_JAVA_ENDPOINT,
//                                    "Are you sure you want to load all the Java challenges? (This can take a while)")
//            }
//
//            p {
//              this@body.adminButton("Load Python Challenges",
//                                    LOAD_PYTHON_ENDPOINT,
//                                    "Are you sure you want to load all the Python challenges? (This can take a while)")
//            }
//
//            p {
//              this@body.adminButton("Load Kotlin Challenges",
//                                    LOAD_KOTLIN_ENDPOINT,
//                                    "Are you sure you want to load all the Kotlin challenges? (This can take a while)")
//            }

            p {
              this@body.adminButton(
                text = "Load All Challenges",
                endpoint = LOAD_ALL_ENDPOINT,
                confirm = "Are you sure you want to load all the challenges? (This can take a while)",
              )
            }

            GRAFANA_URL.getPropertyOrNull()
//              ?.also {
//                //if (it.isNotBlank()) p { +"Grafana Dashboard is "; a { href = it; target = "_blank"; +"here" } }
//              }

            PROMETHEUS_URL.getPropertyOrNull()
//              ?.also {
//                //if (it.isNotBlank()) p { +"Prometheus Dashboard is "; a { href = it; target = "_blank"; +"here" } }
//              }
          } else {
            p { +"Not authorized" }
          }

          p {
            textArea {
              style = "font-size:9px"
              id = MSGS
              readonly = true
              rows = "25"
              cols = "150"
              +""
            }
          }

          p { span { id = STATUS } }

          backLink("$ADMIN_PREFS_ENDPOINT?$RETURN_PARAM=${queryParam(RETURN_PARAM, "/")}")
          enableWebSockets(logId)
          loadStatusPageDisplay()
        }
      }

  private fun BODY.enableWebSockets(logId: String) {
    script {
      rawHtml(
        """
        function sleep(ms) {
          return new Promise(resolve => setTimeout(resolve, ms));
        }

        var cnt = 0;
        var firstTime = true;
        var connected = false;
        function connect() {
          var wshost = location.origin;
          if (wshost.startsWith('https:'))
            wshost = wshost.replace(/^https:/, 'wss:');
          else
            wshost = wshost.replace(/^http:/, 'ws:');

          var wsurl = wshost + '$WS_ROOT$LOGGING_ENDPOINT/$logId';
          var ws = new WebSocket(wsurl);

          ws.onopen = function (event) {
            //console.log("WebSocket connected.");
            firstTime = false;
            document.getElementById('$STATUS').innerText = 'Connected';
            document.getElementById('$MSGS').value = '';
            ws.send("ready");
          };

          ws.onclose = function (event) {
            //console.log('WebSocket closed. Reconnect will be attempted in 1 second.', event.reason);
            var msg = 'Connecting';
            if (!firstTime)
              msg = 'Reconnecting';
            for (i = 0; i < cnt%4; i++)
              msg += '.'
            document.getElementById('$STATUS').innerText = msg;
            setTimeout(function() {
              cnt+=1;
              connect();
            }, 1000);
          }

          ws.onerror = function(err) {
            //console.error(err)
            ws.close();
          };

          ws.onmessage = function (event) {
            var obj = JSON.parse(event.data);
            var elem = document.getElementById('$MSGS');
            elem.value = obj + '\n' + elem.value;
          };
        }
        connect();
        """.trimIndent(),
      )
    }
  }
}
