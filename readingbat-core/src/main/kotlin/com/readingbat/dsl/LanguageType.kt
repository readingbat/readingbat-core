/*
 * Copyright © 2026 Paul Ambrose (pambrose@mac.com)
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

package com.readingbat.dsl

import com.pambrose.common.util.pathOf
import com.readingbat.common.Endpoints.CHALLENGE_ROOT
import com.readingbat.server.LanguageName
import io.ktor.http.Parameters

/**
 * Enumerates the supported programming languages for challenges.
 *
 * Each language type defines its file extension [suffix], default source directory [srcPrefix],
 * and whether string literals use double quotes in the output display.
 *
 * @property useDoubleQuotes whether the language conventionally uses double quotes for string literals
 * @property suffix the file extension for source files (e.g., "java", "py", "kt")
 * @property srcPrefix the default source directory path within a repository
 */
enum class LanguageType(val useDoubleQuotes: Boolean, val suffix: String, val srcPrefix: String) {
  Java(true, "java", "src/main/java"),
  Python(false, "py", "python"),
  Kotlin(true, "kt", "src/main/kotlin"),
  ;

  internal val languageName = LanguageName(name.lowercase())
  internal val contentRoot = pathOf(CHALLENGE_ROOT, languageName)

  val isJava by lazy { this == Java }
  val isPython by lazy { this == Python }
  val isKotlin by lazy { this == Kotlin }

  companion object {
    val defaultLanguageType = Java
    val languageTypeList = listOf(Java, Python, Kotlin)

    /** Returns all language types, optionally reordered to put [defaultLanguage] first. */
    fun languageTypes(defaultLanguage: LanguageType? = null): List<LanguageType> =
      if (defaultLanguage == null) {
        languageTypeList
      } else {
        buildList {
          add(defaultLanguage)
          addAll(languageTypeList.filterNot { it == defaultLanguage })
        }
      }

    /** Converts a language name string (case-insensitive) to the corresponding [LanguageType], or null if not found. */
    fun String.toLanguageType() = entries.firstOrNull { it.name.equals(this, ignoreCase = true) }

    internal fun Parameters.getLanguageType(parameterName: String) =
      this[parameterName]?.let { it.toLanguageType() ?: Java } ?: Java
  }
}
