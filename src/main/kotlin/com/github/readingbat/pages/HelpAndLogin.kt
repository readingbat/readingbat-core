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

package com.github.readingbat.pages

import com.github.pambrose.common.redis.RedisUtils.withRedisPool
import com.github.readingbat.misc.AuthRoutes.LOGOUT
import com.github.readingbat.misc.Constants.RETURN_PATH
import com.github.readingbat.misc.Endpoints.ABOUT_ENDPOINT
import com.github.readingbat.misc.Endpoints.CREATE_ACCOUNT_ENDPOINT
import com.github.readingbat.misc.Endpoints.PASSWORD_RESET_ENDPOINT
import com.github.readingbat.misc.Endpoints.USER_PREFS_ENDPOINT
import com.github.readingbat.misc.FormFields.EMAIL
import com.github.readingbat.misc.FormFields.PASSWORD
import com.github.readingbat.misc.UserPrincipal
import kotlinx.html.*

internal object HelpAndLogin {

  fun BODY.helpAndLogin(principal: UserPrincipal?, loginPath: String) {

    div {
      style = "float:right; margin:0px; border: 1px solid lightgray; margin-left: 10px; padding: 5px;"
      table {
        if (principal != null) logout(principal, loginPath) else login(loginPath)
      }
    }

    div {
      style = "float:right"
      table {
        tr {
          td {
            //valign = "top"
            style = "text-align:right"
            colSpan = "1"
            a { href = "$ABOUT_ENDPOINT?$RETURN_PATH=$loginPath"; +"about" }
            +" | "
            //a { href = "/help.html"; +"help" }
            //+" | "
            a {
              //href = "/doc/code-help-videos.html"; +"code help+videos | "
              //a { href = "/done?user=pambrose@mac.com&tag=6621428513"; +"done" }
              //+" | "
              //a { href = "/report"; +"report" }
              //+" | "
              a { href = "$USER_PREFS_ENDPOINT?$RETURN_PATH=$loginPath"; +"prefs" }
            }
          }
        }
      }
    }
  }

  private fun TABLE.logout(principal: UserPrincipal, loginPath: String) {
    tr {
      val email = withRedisPool { redis -> principal.email(redis) }
      val elems = email.split("@")
      td {
        +elems[0]
        if (elems.size > 1) {
          br
          +"@${elems[1]}"
        }
      }
    }

    tr {
      td {
        /*
    a {
      href = "/doc/practice/code-badges.html"; img {
      width = "30"; style = "vertical-align: middle"; src = "$STATIC_ROOT/s5j.png"
    }
    }
     */
        +"["; a { href = "$LOGOUT?$RETURN_PATH=$loginPath"; +"log out" }; +"]"
      }
    }
  }

  private fun TABLE.login(loginPath: String) {
    form(method = FormMethod.post) {
      action = loginPath
      this@login.tr {
        td { +"id/email" }
        td { textInput { name = EMAIL; size = "20"; placeholder = "username" } }
      }
      this@login.tr {
        td { +"password" }
        td { passwordInput { name = PASSWORD; size = "20"; placeholder = "password" } }
      }
      this@login.tr {
        td {}
        td { submitInput { name = "dologin"; value = "log in" } }
      }
      hiddenInput { name = "fromurl"; value = loginPath }
    }
    tr {
      td {
        colSpan = "2"
        a { href = "$PASSWORD_RESET_ENDPOINT?$RETURN_PATH=$loginPath"; +"forgot password" }
        +" | "
        a { href = "$CREATE_ACCOUNT_ENDPOINT?$RETURN_PATH=$loginPath"; +"create account" }
      }
    }
  }
}