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

package com.github.readingbat

import com.github.readingbat.Constants.answer
import com.github.readingbat.Constants.checkAnswers
import com.github.readingbat.Constants.feedback
import com.github.readingbat.Constants.langSrc
import com.github.readingbat.Constants.processAnswers
import com.github.readingbat.Constants.sessionCounter
import com.github.readingbat.Constants.sessionid
import com.github.readingbat.Constants.solution
import com.github.readingbat.Constants.spinner
import com.github.readingbat.Constants.status
import com.github.readingbat.pages.rawHtml
import kotlinx.html.SCRIPT

fun SCRIPT.addScript(languageName: String) =
  rawHtml(
    """
    var re = new XMLHttpRequest();

    function $processAnswers(event , cnt) { 
    
      if (event != null && event.keyCode != 13) 
        return;

      var data = "$sessionid=${sessionCounter.incrementAndGet()}&$langSrc=$languageName";
      try {
        for (var i = 0; i < cnt; i++) {
          var x = document.getElementById("$feedback"+i);
          x.style.backgroundColor = "white";
          
          var a = document.getElementById("$answer"+i).value;
          data += "&$answer" + i + "="+encodeURIComponent(a);
          var s = document.getElementById("$solution"+i).value;
          console.log("Adding: " + s);
          data += "&$solution" + i + "="+encodeURIComponent(s);
        }
      }
      catch(err) {
        console.log(err.message);
        return 0;
      }
      
      re.onreadystatechange = handleDone;  
      re.open("POST", '/$checkAnswers', true);
      re.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
      re.send(data);
      return 1;
    }
    
    function handleDone(){
      if(re.readyState == 1) {  // starting
        document.getElementById('$spinner').innerHTML = '<i class="fa fa-spinner fa-spin" style="font-size:24px"></i>';
        document.getElementById('$status').innerHTML = 'Checking answers...';
      }
      else if(re.readyState == 4) {  // done
        document.getElementById('$spinner').innerHTML = "";
        document.getElementById('$status').innerHTML = "";
        var results = eval(re.responseText);
        for (var i = 0; i < results.length; i++) {
          var x = document.getElementById("$feedback"+i);
          if (results[i]) 
            x.style.backgroundColor = "green";
          else 
            x.style.backgroundColor = "red";
       }
      }
    }
  """
         )