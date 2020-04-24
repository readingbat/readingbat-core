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
import kotlinx.html.SCRIPT

fun SCRIPT.getScript(languageName: String) {
  rawHtml(
    """
      var re = new XMLHttpRequest();

      function $processAnswers(cnt) { 
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
}