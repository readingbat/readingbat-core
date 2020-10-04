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
import com.github.readingbat.common.CSSNames.INDENT_1EM
import com.github.readingbat.common.CSSNames.TD_PADDING
import com.github.readingbat.common.Constants.UNASSIGNED
import com.github.readingbat.common.Endpoints.ADMIN_PREFS_ENDPOINT
import com.github.readingbat.common.EnvVar
import com.github.readingbat.common.FormFields.RETURN_PARAM
import com.github.readingbat.common.Property
import com.github.readingbat.common.Property.DSL_FILE_NAME
import com.github.readingbat.common.Property.DSL_VARIABLE_NAME
import com.github.readingbat.common.Property.KTOR_PORT
import com.github.readingbat.common.Property.KTOR_WATCH
import com.github.readingbat.common.Property.PROXY_HOSTNAME
import com.github.readingbat.common.SessionActivites
import com.github.readingbat.common.SessionActivites.geoInfoMap
import com.github.readingbat.common.SessionActivites.sessionsMap
import com.github.readingbat.common.User.Companion.emailCache
import com.github.readingbat.common.User.Companion.userIdCache
import com.github.readingbat.dsl.LanguageType.Java
import com.github.readingbat.dsl.LanguageType.Kotlin
import com.github.readingbat.dsl.LanguageType.Python
import com.github.readingbat.dsl.ReadingBatContent
import com.github.readingbat.dsl.agentLaunchId
import com.github.readingbat.dsl.cacheContentInRedis
import com.github.readingbat.dsl.isAgentEnabled
import com.github.readingbat.dsl.isProduction
import com.github.readingbat.pages.PageUtils.backLink
import com.github.readingbat.pages.PageUtils.bodyTitle
import com.github.readingbat.pages.PageUtils.headDefault
import com.github.readingbat.pages.PageUtils.loadStatusPageDisplay
import com.github.readingbat.server.InterceptContext.requestTimingMap
import com.github.readingbat.server.PipelineCall
import com.github.readingbat.server.ReadingBatServer
import com.github.readingbat.server.ServerUtils.queryParam
import com.github.readingbat.server.ws.ChallengeWs.maxWsConnections
import com.github.readingbat.server.ws.ChallengeWs.wsConnections
import io.prometheus.Agent
import kotlinx.html.*
import kotlinx.html.stream.createHTML
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

          div(classes = TD_PADDING) {
            h3 { +"Server Configuration" }
            div(classes = INDENT_1EM) {
              table {
                tr {
                  td { +"Version:" }
                  td { +ReadingBatServer::class.versionDesc() }
                }
                tr {
                  td { +"Server started:" }
                  td { +ReadingBatServer.timeStamp }
                }
                tr {
                  td { +"Server uptime:" }
                  td { +ReadingBatServer.upTime.format(true) }
                }
                tr {
                  td { +"Ktor port:" }
                  td { +KTOR_PORT.getProperty(UNASSIGNED) }
                }
                tr {
                  td { +"Ktor watch:" }
                  td { +KTOR_WATCH.getProperty(UNASSIGNED) }
                }
                tr {
                  td { +"DSL filename:" }
                  td { +DSL_FILE_NAME.getRequiredProperty() }
                }
                tr {
                  td { +"DSL variable name:" }
                  td { +DSL_VARIABLE_NAME.getRequiredProperty() }
                }
                tr {
                  td { +"Production:" }
                  td { +isProduction().toString() }
                }
                tr {
                  td { +"Cache content in Redis:" }
                  td { +cacheContentInRedis().toString() }
                }
                tr {
                  td { +"Cache challenges:" }
                  td { +"${content.cacheChallenges}" }
                }
                tr {
                  td { +"Max history length:" }
                  td { +content.maxHistoryLength.toString() }
                }
                tr {
                  td { +"Max class count:" }
                  td { +content.maxClassCount.toString() }
                }
                tr {
                  td { +"Admin Users:" }
                  td { +ReadingBatServer.adminUsers.joinToString(", ") }
                }
              }
            }

            h3 { +"System Maps" }
            div(classes = INDENT_1EM) {
              table {
                listOf(
                  "Active sessions map size" to sessionsMap,
                  "Request timing map size" to requestTimingMap,
                  "IP Geo map size" to geoInfoMap,
                  "Content map size" to content.contentMap,
                  "User ID cache size" to userIdCache,
                  "User email cache size" to emailCache,
                      )
                  .forEach {
                    tr {
                      td { +it.first }
                      td { +it.second.size.toString() }
                    }
                  }
                tr {
                  td { +"WS connections size/max:" }
                  td { +"${wsConnections.size}/${maxWsConnections}" }
                }
                tr {
                  td { +"Challenge cache size:" }
                  val map = content.functionInfoMap
                  val javaCnt = map.filter { it.value.languageType == Java }.count()
                  val pythonCnt = map.filter { it.value.languageType == Python }.count()
                  val kotlinCnt = map.filter { it.value.languageType == Kotlin }.count()
                  td { +"${map.size} (Java: $javaCnt Python: $pythonCnt Kotlin: $kotlinCnt)" }
                }
              }
            }

            h3 { +"Application Properties" }
            div(classes = INDENT_1EM) {
              table {
                Property.values()
                  .filter { it.isDefined() }
                  .forEach {
                    tr {
                      td { +it.propertyValue }
                      td { +it.maskFunc.invoke(it) }
                    }
                  }
              }
            }

            h3 { +"System Properties" }
            div(classes = INDENT_1EM) {
              table {
                tr {
                  td { +"java.runtime.name" }
                  td { +(System.getProperty("java.runtime.name", UNASSIGNED)) }
                }
                tr {
                  td { +"java.runtime.version" }
                  td { +(System.getProperty("java.runtime.version", UNASSIGNED)) }
                }
                tr {
                  td { +"java.vm.name" }
                  td { +(System.getProperty("java.vm.name", UNASSIGNED)) }
                }
                tr {
                  td { +"java.vm.vendor" }
                  td { +(System.getProperty("java.vm.vendor", UNASSIGNED)) }
                }
              }
            }

            h3 { +"Env Vars" }
            div(classes = INDENT_1EM) {
              table {
                EnvVar.values()
                  .forEach {
                    tr {
                      td { +it.name }
                      td { +it.maskFunc.invoke(it) }
                    }
                  }
              }
            }

            if (isAgentEnabled()) {
              h3 { +"Prometheus Agent" }
              div(classes = INDENT_1EM) {
                table {
                  tr {
                    td { +"Agent Id:" }
                    td { +agentLaunchId() }
                  }
                  tr {
                    td { +"Agent Version:" }
                    td { +Agent::class.versionDesc() }
                  }
                  tr {
                    td { +"Proxy Hostname:" }
                    td { +PROXY_HOSTNAME.getProperty(UNASSIGNED) }
                  }
                }
              }
            }

            h3 { +"Active Users" }
            div(classes = INDENT_1EM) {
              table {
                tr {
                  td { +"Active users in the last minute:" }
                  td { +SessionActivites.activeSessions(1.minutes).toString() }
                }
                tr {
                  td { +"Active users in the last 15 minutes:" }
                  td { +SessionActivites.activeSessions(15.minutes).toString() }
                }
                tr {
                  td { +"Active users in the last hour:" }
                  td { +SessionActivites.activeSessions(1.hours).toString() }
                }
              }
            }

            h3 { +"Content Configuration" }
            div(classes = INDENT_1EM) {
              table {
                tr {
                  td { +"Content last read:" }
                  td { +content.timeStamp }
                }
                tr {
                  td { +"Content read count:" }
                  td { +ReadingBatServer.contentReadCount.get().toString() }
                }
              }
            }

            h3 { +"Script Configuration" }
            div(classes = INDENT_1EM) {
              table {
                tr {
                  th { +"Language" }
                  th { +"Source path" }
                  th { +"Repo" }
                }
                tr {
                  td { +"Python" }
                  td { +content.python.srcPath }
                  td { +content.python.repo.toString() }
                }
                tr {
                  td { +"Java" }
                  td { +content.java.srcPath }
                  td { +content.java.repo.toString() }
                }
                tr {
                  td { +"Kotlin" }
                  td { +content.kotlin.srcPath }
                  td { +content.kotlin.repo.toString() }
                }
              }
            }
          }

          backLink("$ADMIN_PREFS_ENDPOINT?$RETURN_PARAM=${queryParam(RETURN_PARAM, "/")}")

          loadStatusPageDisplay()
        }
      }
}
