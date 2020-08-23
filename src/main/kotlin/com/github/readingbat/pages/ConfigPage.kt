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

import com.github.pambrose.common.time.format
import com.github.pambrose.common.util.Version.Companion.versionDesc
import com.github.readingbat.dsl.LanguageType.Java
import com.github.readingbat.dsl.LanguageType.Kotlin
import com.github.readingbat.dsl.LanguageType.Python
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.dsl.agentLaunchId
import com.github.readingbat.dsl.isAgentEnabled
import com.github.readingbat.dsl.isProduction
import com.github.readingbat.misc.CSSNames.INDENT_1EM
import com.github.readingbat.misc.CSSNames.TD_ITEM
import com.github.readingbat.misc.Constants.RETURN_PATH
import com.github.readingbat.pages.PageCommon.backLink
import com.github.readingbat.pages.PageCommon.bodyTitle
import com.github.readingbat.pages.PageCommon.headDefault
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.ReadingBatServer
import com.github.readingbat.server.ServerUtils.queryParam
import com.github.readingbat.server.SessionActivity
import io.prometheus.Agent
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.stream.createHTML
import kotlinx.html.table
import kotlinx.html.td
import kotlinx.html.tr
import kotlin.time.hours
import kotlin.time.minutes

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
                td(classes = TD_ITEM) { +"Version:" }
                td(classes = TD_ITEM) { +ReadingBatServer::class.versionDesc() }
              }
              tr {
                td(classes = TD_ITEM) { +"Server started:" }
                td(classes = TD_ITEM) { +ReadingBatServer.timeStamp }
              }
              tr {
                td(classes = TD_ITEM) { +"Server uptime:" }
                td(classes = TD_ITEM) { +ReadingBatServer.upTime.format(true) }
              }
              tr {
                td(classes = TD_ITEM) { +"Ktor port:" }
                td(classes = TD_ITEM) { +"${content.ktorPort}" }
              }
              tr {
                td(classes = TD_ITEM) { +"Ktor watch:" }
                td(classes = TD_ITEM) { +content.ktorWatch }
              }
              tr {
                td(classes = TD_ITEM) { +"DSL filename:" }
                td(classes = TD_ITEM) { +content.dslFileName }
              }
              tr {
                td(classes = TD_ITEM) { +"DSL variable name:" }
                td(classes = TD_ITEM) { +content.dslVariableName }
              }
              tr {
                td(classes = TD_ITEM) { +"Production:" }
                td(classes = TD_ITEM) { +"${isProduction()}" }
              }
              tr {
                td(classes = TD_ITEM) { +"Cache challenges:" }
                td(classes = TD_ITEM) { +"${content.cacheChallenges}" }
              }
              tr {
                td(classes = TD_ITEM) { +"Max history length:" }
                td(classes = TD_ITEM) { +"${content.maxHistoryLength}" }
              }
              tr {
                td(classes = TD_ITEM) { +"Max class count" }
                td(classes = TD_ITEM) { +"${content.maxClassCount}" }
              }
              tr {
                td(classes = TD_ITEM) { +"Challenge cache size" }
                val map = content.sourcesMap
                val javaCnt = map.filter { it.value.languageType == Java }.count()
                val pythonCnt = map.filter { it.value.languageType == Python }.count()
                val kotlinCnt = map.filter { it.value.languageType == Kotlin }.count()
                td { +"Total: ${map.size} (Java: $javaCnt Python: $pythonCnt Kotlin: $kotlinCnt)" }
              }
              tr {
                td(classes = TD_ITEM) { +"Session map size" }
                td(classes = TD_ITEM) { +"${SessionActivity.sessionsMapSize}" }
              }
            }
          }

          h3 { +"Env Vars" }
          div(classes = INDENT_1EM) {
            table {
              tr {
                td(classes = TD_ITEM) { +"JAVA_TOOL_OPTIONS" }
                td(classes = TD_ITEM) { +(System.getenv("JAVA_TOOL_OPTIONS") ?: "unassigned") }
              }
              tr {
                td(classes = TD_ITEM) { +"AGENT_CONFIG" }
                td(classes = TD_ITEM) { +(System.getenv("AGENT_CONFIG") ?: "unassigned") }
              }
              tr {
                td(classes = TD_ITEM) { +"REDIS_URL" }
                td(classes = TD_ITEM) { +(System.getenv("REDIS_URL") ?: "unassigned") }
              }
            }
          }

          h3 { +"Prometheus Agent Configuration" }
          div(classes = INDENT_1EM) {
            table {
              tr {
                td(classes = TD_ITEM) { +"Agent Id:" }
                td(classes = TD_ITEM) { +if (isAgentEnabled()) agentLaunchId() else "disabled" }
              }
              tr {
                td(classes = TD_ITEM) { +"Agent Version:" }
                td(classes = TD_ITEM) { +if (isAgentEnabled()) Agent::class.versionDesc() else "disabled" }
              }
            }
          }

          h3 { +"Active Users" }
          div(classes = INDENT_1EM) {
            table {
              tr {
                td(classes = TD_ITEM) { +"Active users in the last minute:" }
                td(classes = TD_ITEM) { +SessionActivity.activeSessions(1.minutes).toString() }
              }
              tr {
                td(classes = TD_ITEM) { +"Active users in the last 15 minutes:" }
                td(classes = TD_ITEM) { +SessionActivity.activeSessions(15.minutes).toString() }
              }
              tr {
                td(classes = TD_ITEM) { +"Active users in the last hour:" }
                td(classes = TD_ITEM) { +SessionActivity.activeSessions(1.hours).toString() }
              }
            }
          }

          h3 { +"Content Configuration" }
          div(classes = INDENT_1EM) {
            table {
              tr {
                td(classes = TD_ITEM) { +"Content last read:" }
                td(classes = TD_ITEM) { +content.timeStamp }
              }
              tr {
                td(classes = TD_ITEM) { +"Content read count:" }
                td(classes = TD_ITEM) { +ReadingBatServer.contentReadCount.get().toString() }
              }
            }
          }

          h3 { +"Python Configuration" }
          div(classes = INDENT_1EM) {
            table {
              tr {
                td(classes = TD_ITEM) { +"Repo:" }
                td(classes = TD_ITEM) { +"${content.python.repo}" }
              }
              tr {
                td(classes = TD_ITEM) { +"Source path:" }
                td(classes = TD_ITEM) { +content.python.srcPath }
              }
            }
          }

          h3 { +"Java Configuration" }
          div(classes = INDENT_1EM) {
            table {
              tr {
                td(classes = TD_ITEM) { +"Repo:" }
                td(classes = TD_ITEM) { +"${content.java.repo}" }
              }
              tr {
                td(classes = TD_ITEM) { +"Source path:" }
                td(classes = TD_ITEM) { +content.java.srcPath }
              }
            }
          }

          h3 { +"Kotlin Configuration" }
          div(classes = INDENT_1EM) {
            table {
              tr {
                td(classes = TD_ITEM) { +"Repo:" }
                td(classes = TD_ITEM) { +"${content.kotlin.repo}" }
              }
              tr {
                td(classes = TD_ITEM) { +"Source path:" }
                td(classes = TD_ITEM) { +content.kotlin.srcPath }
              }
            }
          }

          backLink(queryParam(RETURN_PATH))
        }
      }
}
