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

package com.github.readingbat.pages.js

import com.github.readingbat.common.Constants.ADMIN_FUNC
import com.github.readingbat.common.Constants.NO_ANSWER_COLOR
import com.github.readingbat.common.Constants.WRONG_COLOR
import com.github.readingbat.common.ParameterIds.FEEDBACK_ID
import com.github.readingbat.common.ParameterIds.HINT_ID
import com.github.readingbat.common.ParameterIds.SPINNER_ID
import com.github.readingbat.common.ParameterIds.STATUS_ID
import com.github.readingbat.common.ParameterIds.SUCCESS_ID
import com.github.readingbat.pages.PageUtils.rawHtml
import com.github.readingbat.server.ws.WsCommon.LOG_ID
import kotlinx.html.SCRIPT

internal object AdminCommandsJs {

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
        document.getElementById('$SPINNER_ID').innerHTML = '<i class="fa fa-spinner fa-spin" style="font-size:24px"></i>';
        document.getElementById('$STATUS_ID').innerText = 'Checking answers...';
        document.getElementById('$SUCCESS_ID').innerText = '';
      }
      else if(re.readyState == 4) {  // done
        let success = true;
        let results = eval(re.responseText);
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
  """)
}