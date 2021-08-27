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
import com.github.readingbat.common.Constants.GROUP_SRC
import com.github.readingbat.common.Constants.LANG_SRC
import com.github.readingbat.common.Constants.LIKE_DESC
import com.github.readingbat.common.Constants.LIKE_DISLIKE_FUNC
import com.github.readingbat.common.Constants.SESSION_ID
import com.github.readingbat.common.Endpoints.LIKE_DISLIKE_ENDPOINT
import com.github.readingbat.common.ParameterIds.DISLIKE_CLEAR
import com.github.readingbat.common.ParameterIds.DISLIKE_COLOR
import com.github.readingbat.common.ParameterIds.LIKE_CLEAR
import com.github.readingbat.common.ParameterIds.LIKE_COLOR
import com.github.readingbat.common.ParameterIds.LIKE_SPINNER_ID
import com.github.readingbat.common.ParameterIds.LIKE_STATUS_ID
import com.github.readingbat.pages.PageUtils.rawHtml
import com.github.readingbat.server.ChallengeName
import com.github.readingbat.server.GroupName
import com.github.readingbat.server.LanguageName
import kotlinx.html.SCRIPT
import java.util.concurrent.atomic.AtomicInteger

internal object LikeDislikeJs {
  private val sessionCounter = AtomicInteger(0)

  fun SCRIPT.likeDislikeScript(languageName: LanguageName, groupName: GroupName, challengeName: ChallengeName) =
    rawHtml(
      """
    var re = new XMLHttpRequest();

    function $LIKE_DISLIKE_FUNC(desc) { 
      let data = "$SESSION_ID=${sessionCounter.incrementAndGet()}&$LANG_SRC=$languageName&$GROUP_SRC=$groupName&$CHALLENGE_SRC=$challengeName";
      data += "&$LIKE_DESC=" + encodeURIComponent(desc);
      
      re.onreadystatechange = likeDislikeHandleDone;  
      re.open("POST", '$LIKE_DISLIKE_ENDPOINT', true);
      re.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
      re.send(data);
      return 1;
    }
    
    function likeDislikeHandleDone() {
      if(re.readyState == 1) {       // starting
        document.getElementById('$LIKE_SPINNER_ID').innerHTML = '<i class="fa fa-spinner fa-spin" style="font-size:24px"></i>';
        document.getElementById('$LIKE_STATUS_ID').innerText = 'Setting like/dislike...';
      }
      else if(re.readyState == 4) {  // done
        let results = eval(re.responseText);
        
        document.getElementById('$LIKE_SPINNER_ID').innerText = '';
        document.getElementById('$LIKE_STATUS_ID').innerText = '';
        
        if (results == 0) {
          document.getElementById('$LIKE_CLEAR').style.display = "inline";
          document.getElementById('$LIKE_COLOR').style.display = "none";
          document.getElementById('$DISLIKE_CLEAR').style.display = "inline";
          document.getElementById('$DISLIKE_COLOR').style.display = "none";
        }
        else if (results == 1) {
          document.getElementById('$LIKE_CLEAR').style.display = "none";
          document.getElementById('$LIKE_COLOR').style.display = "inline";
          document.getElementById('$DISLIKE_CLEAR').style.display = "inline";
          document.getElementById('$DISLIKE_COLOR').style.display = "none";
        }
        else {
          document.getElementById('$LIKE_CLEAR').style.display = "inline";
          document.getElementById('$LIKE_COLOR').style.display = "none";
          document.getElementById('$DISLIKE_CLEAR').style.display = "none";
          document.getElementById('$DISLIKE_COLOR').style.display = "inline";
        }
      }
    }
  """)
}