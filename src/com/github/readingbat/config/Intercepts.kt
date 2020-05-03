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

package com.github.readingbat.config

import com.github.readingbat.InvalidPathException
import com.github.readingbat.Module.readingBatContent
import com.github.readingbat.dsl.LanguageType.*
import com.github.readingbat.dsl.LanguageType.Companion.toLanguageType
import com.github.readingbat.pages.challengeGroupPage
import com.github.readingbat.pages.challengePage
import com.github.readingbat.pages.languageGroupPage
import io.ktor.application.Application
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.call
import io.ktor.html.respondHtml
import io.ktor.request.uri

internal fun Application.intercepts() {
  intercept(ApplicationCallPipeline.Call) {
    val req = call.request.uri
    val items = req.split("/").filter { it.isNotEmpty() }

    if (items.isNotEmpty() && items[0] in listOf(Java.lowerName, Python.lowerName, Kotlin.lowerName)) {
      val languageType = items[0].toLanguageType()
      val groupName = items.elementAtOrNull(1) ?: ""
      val challengeName = items.elementAtOrNull(2) ?: ""
      when (items.size) {
        1 -> {
          // This lookup has to take place outside of the lambda for proper exception handling
          val groups = readingBatContent.findLanguage(languageType).challengeGroups
          call.respondHtml { languageGroupPage(languageType, groups) }
        }
        2 -> {
          val challengeGroup = readingBatContent.findLanguage(languageType).findGroup(groupName)
          call.respondHtml { challengeGroupPage(challengeGroup) }
        }
        3 -> {
          val challenge = readingBatContent.findLanguage(languageType).findChallenge(groupName, challengeName)
          call.respondHtml { challengePage(challenge) }
        }
        else -> throw InvalidPathException("Invalid path: $req")
      }
    }
  }

  intercept(ApplicationCallPipeline.Monitoring) {
    // Set up metrics here
  }

  intercept(ApplicationCallPipeline.Fallback) {
    // Count not found pages here
  }

}