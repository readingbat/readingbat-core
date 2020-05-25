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

import com.github.pambrose.common.util.join
import kotlinx.html.FlowOrInteractiveOrPhrasingContent
import kotlinx.html.button
import kotlinx.html.onClick
import kotlinx.html.style

object PageUtils {

  fun hideShowJs(formName: String, fieldName: String) =
    """
      var pw=document.$formName.$fieldName.type=="password"; 
      document.$formName.$fieldName.type=pw?"text":"password"; 
      return false;
    """.trimIndent()

  fun FlowOrInteractiveOrPhrasingContent.hideShowButton(formName: String, fieldName: String, sizePct: Int = 85) {
    button {
      style = "font-size:$sizePct%;"
      onClick = hideShowJs(formName, fieldName)
      +"show/hide"
    }

  }

  fun pathOf(vararg elems: String): String = elems.toList().join()
}