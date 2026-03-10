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

package com.github.readingbat.common

import com.github.pambrose.common.util.randomId
import com.github.readingbat.common.FormFields.DISABLED_MODE
import io.ktor.http.Parameters

data class ClassCode(val classCode: String) {
  val isNotEnabled by lazy { classCode == DISABLED_MODE || classCode.isBlank() }
  val isEnabled by lazy { !isNotEnabled }

  val displayedValue get() = if (classCode == DISABLED_MODE) "" else classCode

  override fun toString() = classCode

  companion object {
    internal val DISABLED_CLASS_CODE = ClassCode(DISABLED_MODE)

    internal fun newClassCode() = ClassCode(randomId(15))

    internal fun Parameters.getClassCode(parameterName: String) =
      this[parameterName]?.let { ClassCode(it) } ?: DISABLED_CLASS_CODE
  }
}
