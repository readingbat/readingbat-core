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

package com.readingbat.pages.js

import com.readingbat.common.Constants.ADMIN_FUNC
import com.readingbat.common.Constants.NO_ANSWER_COLOR
import com.readingbat.common.Constants.WRONG_COLOR
import com.readingbat.common.ParameterIds.FEEDBACK_ID
import com.readingbat.common.ParameterIds.HINT_ID
import com.readingbat.common.ParameterIds.SPINNER_ID
import com.readingbat.common.ParameterIds.STATUS_ID
import com.readingbat.common.ParameterIds.SUCCESS_ID
import com.readingbat.pages.PageUtils.rawHtml
import com.readingbat.server.ws.WsCommon.LOG_ID
import kotlinx.html.SCRIPT

/**
 * Generates the client-side JavaScript for system admin command buttons.
 *
 * Provides the confirmation-and-post function used by admin action buttons on the
 * system admin page, sending commands to server endpoints with a log ID for
 * WebSocket-based progress reporting.
 */
internal object AdminCommandsJs {
  /** Emits the JavaScript function that sends admin commands to the server with confirmation dialogs. */
  fun SCRIPT.loadCommandsScript(logId: String) =
    rawHtml(
      """
    var re = new XMLHttpRequest();

    function $ADMIN_FUNC(msg, endPoint) {
      if (confirm(msg)) {
        let data = "$LOG_ID=$logId";
        //re.onreadystatechange = checkLogHandleDone;
        re.open("POST", endPoint, true);
        re.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
        re.send(data);
      }

      return 1;
    }

    function checkLogHandleDone(){
      if(re.readyState == 1) {  // starting
        document.getElementById('$SPINNER_ID').innerHTML = '<i class="fa fa-spinner fa-spin text-2xl" style="font-size:24px"></i>';
        document.getElementById('$STATUS_ID').innerText = 'Checking answers...';
        document.getElementById('$SUCCESS_ID').innerText = '';
      }
      else if(re.readyState == 4) {  // done
        let success = true;
        let results = JSON.parse(re.responseText);
        for (let i = 0; i < results.length; i++) {
          let x = document.getElementById("$FEEDBACK_ID"+i);
          if (results[i][0] == 0) {
            x.style.backgroundColor = '$NO_ANSWER_COLOR';
            success = false;
          }
          else {
            x.style.backgroundColor = '$WRONG_COLOR';
            success = false;
            document.getElementById("$HINT_ID"+i).innerText = results[i][1];
          }
        }

        document.getElementById('$SPINNER_ID').innerText = '';
        document.getElementById('$STATUS_ID').innerText = '';
        document.getElementById('$SUCCESS_ID').innerText = success ? "Success! Congratulations!" : "";
      }
    }
  """,
    )
}
