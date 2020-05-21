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

import com.github.readingbat.misc.Answers.challengeSrc
import com.github.readingbat.misc.Answers.groupSrc
import com.github.readingbat.misc.Answers.langSrc
import com.github.readingbat.misc.Answers.processAnswers
import com.github.readingbat.misc.CSSNames.feedback
import com.github.readingbat.misc.CSSNames.spinnerId
import com.github.readingbat.misc.CSSNames.statusId
import com.github.readingbat.misc.CSSNames.successId
import com.github.readingbat.misc.CSSNames.userResp
import com.github.readingbat.misc.Constants.sessionid
import com.github.readingbat.misc.Endpoints.CHECK_ANSWERS_ROOT
import com.github.readingbat.pages.rawHtml
import kotlinx.html.SCRIPT
import java.util.concurrent.atomic.AtomicInteger

internal object Answers {
  const val langSrc = "lang"
  const val groupSrc = "groupName"
  const val challengeSrc = "challengeName"
  const val processAnswers = "processAnswers"
}

private val sessionCounter = AtomicInteger(0)

internal fun SCRIPT.checkAnswersScript(languageName: String, groupName: String, challengeName: String) =
  rawHtml(
    """
    var re = new XMLHttpRequest();

    function $processAnswers(event, cnt) { 
    
      if (event != null && event.keyCode != 13) 
        return;

      var data = "$sessionid=${sessionCounter.incrementAndGet()}&$langSrc=$languageName&$groupSrc=$groupName&$challengeSrc=$challengeName";
      try {
        for (var i = 0; i < cnt; i++) {
          var x = document.getElementById("$feedback"+i);
          x.style.backgroundColor = "white";
          
          var ur = document.getElementById("$userResp"+i).value;
          data += "&$userResp" + i + "="+encodeURIComponent(ur);
        }
      }
      catch(err) {
        console.log(err.message);
        return 0;
      }
      
      re.onreadystatechange = handleDone;  
      re.open("POST", '$CHECK_ANSWERS_ROOT', true);
      re.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
      re.send(data);
      return 1;
    }
    
    function handleDone(){
      if(re.readyState == 1) {  // starting
        document.getElementById('$spinnerId').innerHTML = '<i class="fa fa-spinner fa-spin" style="font-size:24px"></i>';
        document.getElementById('$statusId').innerHTML = 'Checking answers...';
        document.getElementById('$successId').innerHTML = '';
      }
      else if(re.readyState == 4) {  // done
        var success = true;
        var results = eval(re.responseText);
        for (var i = 0; i < results.length; i++) {
          var x = document.getElementById("$feedback"+i);
          if (results[i]) 
            x.style.backgroundColor = "green";
          else {
            x.style.backgroundColor = "red";
            success = false
          }
        }
        
        document.getElementById('$spinnerId').innerHTML = "";
        document.getElementById('$statusId').innerHTML = "";
        if (success) 
          document.getElementById('$successId').innerHTML = "Success! Congratulations!";
        else 
          document.getElementById('$successId').innerHTML = "";
      }
    }
  """
         )