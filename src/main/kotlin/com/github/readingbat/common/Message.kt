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

package com.github.readingbat.common

import com.github.readingbat.common.Constants.CORRECT_COLOR
import com.github.readingbat.common.Constants.WRONG_COLOR

internal data class Message(val value: String, val isError: Boolean = false) {
  val isBlank get() = value.isBlank()
  val isNotBlank get() = value.isNotBlank()

  val color get() = if (isError) WRONG_COLOR else CORRECT_COLOR

  fun isAssigned() = this != EMPTY_MESSAGE
  fun isUnassigned() = this == EMPTY_MESSAGE

  override fun toString() = value

  companion object {
    val EMPTY_MESSAGE = Message("")
  }
}