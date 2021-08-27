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

package com.github.readingbat.dsl

import com.github.pambrose.common.util.isNull
import com.github.pambrose.common.util.pathOf
import com.github.readingbat.common.Endpoints.CHALLENGE_ROOT
import com.github.readingbat.server.LanguageName
import io.ktor.http.*
import java.util.*

enum class LanguageType(val useDoubleQuotes: Boolean, val suffix: String, val srcPrefix: String) {
  Java(true, "java", "src/main/java"),
  Python(false, "py", "python"),
  Kotlin(true, "kt", "src/main/kotlin");

  internal val languageName = LanguageName(name.lowercase(Locale.getDefault()))
  internal val contentRoot = pathOf(CHALLENGE_ROOT, languageName)

  val isJava by lazy { this == Java }
  val isPython by lazy { this == Python }
  val isKotlin by lazy { this == Kotlin }

  companion object {
    val defaultLanguageType = Java
    val languageTypeList = listOf(Java, Python, Kotlin)

    fun languageTypes(defaultLanguage: LanguageType? = null) =
      if (defaultLanguage.isNull())
        languageTypeList
      else
        mutableListOf(defaultLanguage)
          .also { list ->
            languageTypeList
              .filterNot { it == defaultLanguage }
              .forEach {
                list += it
              }
          }

    fun String.toLanguageType() = values().firstOrNull { it.name.equals(this, ignoreCase = true) }

    internal fun Parameters.getLanguageType(parameterName: String) =
      this[parameterName]?.let { it.toLanguageType() ?: Java } ?: Java
  }
}