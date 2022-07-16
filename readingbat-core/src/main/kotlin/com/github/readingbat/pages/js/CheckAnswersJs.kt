/*
 * Copyright © 2021 Paul Ambrose (pambrose@mac.com)
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

import com.github.readingbat.common.Constants.CHALLENGE_SRC
import com.github.readingbat.common.Constants.CORRECT_COLOR
import com.github.readingbat.common.Constants.GROUP_SRC
import com.github.readingbat.common.Constants.LANG_SRC
import com.github.readingbat.common.Constants.NO_ANSWER_COLOR
import com.github.readingbat.common.Constants.PROCESS_USER_ANSWERS_FUNC
import com.github.readingbat.common.Constants.RESP
import com.github.readingbat.common.Constants.SESSION_ID
import com.github.readingbat.common.Constants.WRONG_COLOR
import com.github.readingbat.common.Endpoints.CHECK_ANSWERS_ENDPOINT
import com.github.readingbat.common.ParameterIds.FEEDBACK_ID
import com.github.readingbat.common.ParameterIds.HINT_ID
import com.github.readingbat.common.ParameterIds.NEXTPREVCHANCE_ID
import com.github.readingbat.common.ParameterIds.SPINNER_ID
import com.github.readingbat.common.ParameterIds.STATUS_ID
import com.github.readingbat.common.ParameterIds.SUCCESS_ID
import com.github.readingbat.pages.PageUtils.rawHtml
import com.github.readingbat.posts.AnswerStatus.CORRECT
import com.github.readingbat.posts.AnswerStatus.NOT_ANSWERED
import com.github.readingbat.server.ChallengeName
import com.github.readingbat.server.GroupName
import com.github.readingbat.server.LanguageName
import kotlinx.html.SCRIPT
import java.util.concurrent.atomic.AtomicInteger

internal object CheckAnswersJs {
  private val sessionCounter = AtomicInteger(0)

  fun SCRIPT.checkAnswersScript(languageName: LanguageName, groupName: GroupName, challengeName: ChallengeName) =
    rawHtml(
      """
    var re = new XMLHttpRequest();

    function $PROCESS_USER_ANSWERS_FUNC(event, cnt) { 
      // event will equal null on button press
      if (event != null && (event.keyCode != 13 && event.keyCode != 9)) 
        return 1;

      let data = "$SESSION_ID=${sessionCounter.incrementAndGet()}&$LANG_SRC=$languageName&$GROUP_SRC=$groupName&$CHALLENGE_SRC=$challengeName";
      try {
        for (let i = 0; i < cnt; i++) {
          let x = document.getElementById("$FEEDBACK_ID"+i);
          x.style.backgroundColor = "white";
          
          document.getElementById("$HINT_ID"+i).innerText = '';
          
          let ur = document.getElementById("$RESP"+i).value;
          data += "&$RESP" + i + "=" + encodeURIComponent(ur);
        }
      }
      catch(err) {
        console.log(err.message);
        return 0;
      }
      
      re.onreadystatechange = checkAnswerHandleDone;  
      re.open("POST", '$CHECK_ANSWERS_ENDPOINT', true);
      re.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
      re.send(data);
      return 1;
    }
    
    function checkAnswerHandleDone(){
      if(re.readyState == 1) {  // starting
        document.getElementById('$SPINNER_ID').innerHTML = '<i class="fa fa-spinner fa-spin" style="font-size:24px"></i>';
        document.getElementById('$STATUS_ID').innerText = 'Checking answers...';
        document.getElementById('$SUCCESS_ID').innerText = '';
        document.getElementById('$NEXTPREVCHANCE_ID').style.display = "none";
      }
      else if(re.readyState == 4) {  // done
        let success = true;
        let results = eval(re.responseText);
        for (let i = 0; i < results.length; i++) {
          let x = document.getElementById("$FEEDBACK_ID"+i);
          if (results[i][0] == ${NOT_ANSWERED.value}) {
            x.style.backgroundColor = '$NO_ANSWER_COLOR';
            success = false;
          }
          else if (results[i][0] == ${CORRECT.value}) {
            x.style.backgroundColor = '$CORRECT_COLOR';
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
        document.getElementById('$NEXTPREVCHANCE_ID').style.display = success ? "inline" : "none";
      }
    }
  """
    )
}