/*
 * Copyright © 2023 Paul Ambrose (pambrose@mac.com)
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

import com.github.readingbat.common.Endpoints.OAUTH_LOGIN_GITHUB_ENDPOINT
import com.github.readingbat.common.Endpoints.OAUTH_LOGIN_GOOGLE_ENDPOINT
import com.github.readingbat.pages.PageUtils.bodyTitle
import com.github.readingbat.pages.PageUtils.headDefault
import com.github.readingbat.pages.PageUtils.loadPingdomScript
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.p
import kotlinx.html.stream.createHTML
import kotlinx.html.style

internal object OAuthLoginPage {
  fun oauthLoginPage() =
    createHTML()
      .html {
        head { headDefault() }

        body {
          bodyTitle()

          h2 { +"Sign In" }

          div {
            style = "margin-left: 1em"

            p { +"Sign in to ReadingBat using one of the following providers:" }

            div {
              style = "margin: 20px 0"
              a {
                href = OAUTH_LOGIN_GITHUB_ENDPOINT
                style =
                  """
                  display: inline-block;
                  padding: 10px 20px;
                  margin: 5px;
                  background-color: #333;
                  color: white;
                  text-decoration: none;
                  border-radius: 4px;
                  font-size: 14px;
                  """.trimIndent()
                +"Sign in with GitHub"
              }

              a {
                href = OAUTH_LOGIN_GOOGLE_ENDPOINT
                style =
                  """
                  display: inline-block;
                  padding: 10px 20px;
                  margin: 5px;
                  background-color: #4285f4;
                  color: white;
                  text-decoration: none;
                  border-radius: 4px;
                  font-size: 14px;
                  """.trimIndent()
                +"Sign in with Google"
              }
            }
          }

          loadPingdomScript()
        }
      }
}
