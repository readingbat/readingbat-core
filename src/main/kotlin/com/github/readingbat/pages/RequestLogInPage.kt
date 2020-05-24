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

import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.misc.Constants.BACK_PATH
import com.github.readingbat.misc.Constants.RETURN_PATH
import com.github.readingbat.misc.Endpoints.PREFS
import com.github.readingbat.misc.Endpoints.PRIVACY
import com.github.readingbat.misc.UserId.UserPrincipal
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.queryParam
import kotlinx.html.*
import kotlinx.html.stream.createHTML

internal fun PipelineCall.requestLogInPage(content: ReadingBatContent,
                                           principal: UserPrincipal?) =
  createHTML()
    .html {
      val returnPath = queryParam(RETURN_PATH) ?: "/"

      head {
        headDefault(content)
      }

      body {
        //val path = "$CHALLENGE_ROOT/$returnPath"
        helpAndLogin(principal, returnPath)
        bodyTitle()

        h2 { +"Log in" }
        p { +"Please create an account or log in to an existing account to edit preferences." }
        p { a { href = "$PRIVACY?$BACK_PATH=$PREFS&$RETURN_PATH=$returnPath"; +"privacy statement" } }

        backLink(returnPath)
      }
    }