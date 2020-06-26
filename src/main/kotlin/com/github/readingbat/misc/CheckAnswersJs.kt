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

package com.github.readingbat.misc

import com.github.readingbat.misc.Constants.CORRECT_COLOR
import com.github.readingbat.misc.Constants.RESP
import com.github.readingbat.misc.Constants.SESSION_ID
import com.github.readingbat.misc.Endpoints.CHECK_ANSWERS_ENDPOINT
import com.github.readingbat.misc.ParameterIds.FEEDBACK_ID
import com.github.readingbat.misc.ParameterIds.SPINNER_ID
import com.github.readingbat.misc.ParameterIds.STATUS_ID
import com.github.readingbat.misc.ParameterIds.SUCCESS_ID
import com.github.readingbat.pages.PageCommon.rawHtml
import com.github.readingbat.server.ChallengeName
import com.github.readingbat.server.GroupName
import com.github.readingbat.server.LanguageName
import kotlinx.html.SCRIPT
import java.util.concurrent.atomic.AtomicInteger

internal object CheckAnswersJs {
  const val langSrc = "lang"
  const val groupSrc = "groupName"
  const val challengeSrc = "challengeName"
  const val processUserAnswers = "processUserAnswers"

  private val sessionCounter = AtomicInteger(0)

  fun SCRIPT.checkAnswersScript(languageName: LanguageName, groupName: GroupName, challengeName: ChallengeName) =
    rawHtml(
      """
    var re = new XMLHttpRequest();

    function $processUserAnswers(event, cnt) { 
    
      //if (event != null)
      //  console.log(event.keyCode);
      
      // event will equal null on button press
      if (event != null && (event.keyCode != 13 && event.keyCode != 9)) 
        return 1;

      var data = "$SESSION_ID=${sessionCounter.incrementAndGet()}&$langSrc=$languageName&$groupSrc=$groupName&$challengeSrc=$challengeName";
      try {
        for (var i = 0; i < cnt; i++) {
          var x = document.getElementById("$FEEDBACK_ID"+i);
          x.style.backgroundColor = "white";
          
          var ur = document.getElementById("$RESP"+i).value;
          data += "&$RESP" + i + "="+encodeURIComponent(ur);
        }
      }
      catch(err) {
        console.log(err.message);
        return 0;
      }
      
      re.onreadystatechange = handleDone;  
      re.open("POST", '$CHECK_ANSWERS_ENDPOINT', true);
      re.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
      re.send(data);
      return 1;
    }
    
    function handleDone(){
      if(re.readyState == 1) {  // starting
        document.getElementById('$SPINNER_ID').innerHTML = '<i class="fa fa-spinner fa-spin" style="font-size:24px"></i>';
        document.getElementById('$STATUS_ID').innerHTML = 'Checking answers...';
        document.getElementById('$SUCCESS_ID').innerHTML = '';
      }
      else if(re.readyState == 4) {  // done
        var success = true;
        var results = eval(re.responseText);
        for (var i = 0; i < results.length; i++) {
          var x = document.getElementById("$FEEDBACK_ID"+i);
          if (results[i]) 
            x.style.backgroundColor = '$CORRECT_COLOR';
          else {
            x.style.backgroundColor = "red";
            success = false
          }
        }
        
        document.getElementById('$SPINNER_ID').innerHTML = "";
        document.getElementById('$STATUS_ID').innerHTML = "";
        document.getElementById('$SUCCESS_ID').innerHTML = success ? "Success! Congratulations!" : "";
      }
    }
  """
           )
}
