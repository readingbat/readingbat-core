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

package com.readingbat.common

import com.pambrose.common.util.randomId
import com.readingbat.common.FormFields.DISABLED_MODE
import io.ktor.http.Parameters

/**
 * Represents a class code that identifies a teacher's class for student enrollment and progress tracking.
 *
 * A class code is a random string identifier that students use to join a teacher's class.
 * The special [DISABLED_CLASS_CODE] sentinel indicates that no class is active.
 *
 * @property classCode The raw string value of the class code.
 */
data class ClassCode(val classCode: String) {
  /** Whether this class code is disabled (sentinel value) or blank. */
  val isNotEnabled by lazy { classCode == DISABLED_MODE || classCode.isBlank() }

  /** Whether this class code represents an active, usable class. */
  val isEnabled by lazy { !isNotEnabled }

  /** The value to display in the UI, returning empty string for disabled codes. */
  val displayedValue get() = if (classCode == DISABLED_MODE) "" else classCode

  override fun toString() = classCode

  companion object {
    /** Sentinel class code indicating no class is active or assigned. */
    internal val DISABLED_CLASS_CODE = ClassCode(DISABLED_MODE)

    /** Generates a new random class code with a 15-character identifier. */
    internal fun newClassCode() = ClassCode(randomId(15))

    /** Extracts a [ClassCode] from HTTP query parameters, defaulting to [DISABLED_CLASS_CODE] if absent. */
    internal fun Parameters.getClassCode(parameterName: String) =
      this[parameterName]?.let { ClassCode(it) } ?: DISABLED_CLASS_CODE
  }
}
