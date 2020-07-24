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
import com.github.readingbat.misc.CSSNames.INDENT_1EM
import com.github.readingbat.misc.Constants.RETURN_PATH
import com.github.readingbat.pages.PageCommon.backLink
import com.github.readingbat.pages.PageCommon.bodyTitle
import com.github.readingbat.pages.PageCommon.headDefault
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.ServerUtils.queryParam
import kotlinx.html.*
import kotlinx.html.stream.createHTML

internal object ConfigPage {

  fun PipelineCall.configPage(content: ReadingBatContent) =
    createHTML()
      .html {
        head { headDefault(content) }

        body {
          bodyTitle()

          h2 { +"ReadingBat Configuration" }

          h3 { +"Server Configuration" }
          div(classes = INDENT_1EM) {
            table {
              tr {
                td { +"Ktor port:" }
                td { +"${content.ktorPort}" }
              }
              tr {
                td { +"Ktor watch:" }
                td { +content.ktorWatch }
              }
              tr {
                td { +"DSL filename:" }
                td { +content.dslFileName }
              }
              tr {
                td { +"DSL variable name:" }
                td { +content.dslVariableName }
              }
              tr {
                td { +"Production:" }
                td { +"${content.production}" }
              }
              tr {
                td { +"Cache challenges:" }
                td { +"${content.cacheChallenges}" }
              }
              tr {
                td { +"Max history length:" }
                td { +"${content.maxHistoryLength}" }
              }
              tr {
                td { +"Max class count" }
                td { +"${content.maxClassCount}" }
              }
            }
          }

          h3 { +"Python Configuration" }
          div(classes = INDENT_1EM) {
            table {
              tr {
                td { +"Repo:" }
                td { +"${content.python.repo}" }
              }
              tr {
                td { +"Source path:" }
                td { +content.python.srcPath }
              }
            }
          }

          h3 { +"Java Configuration" }
          div(classes = INDENT_1EM) {
            table {
              tr {
                td { +"Repo:" }
                td { +"${content.java.repo}" }
              }
              tr {
                td { +"Source path:" }
                td { +content.java.srcPath }
              }
            }
          }

          h3 { +"Kotlin Configuration" }
          div(classes = INDENT_1EM) {
            table {
              tr {
                td { +"Repo:" }
                td { +"${content.kotlin.repo}" }
              }
              tr {
                td { +"Source path:" }
                td { +content.kotlin.srcPath }
              }
            }
          }

          backLink(queryParam(RETURN_PATH))
        }
      }
}