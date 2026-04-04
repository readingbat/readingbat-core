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

import com.readingbat.common.Constants.CORRECT_COLOR
import com.readingbat.common.Constants.WRONG_COLOR

/**
 * A user-facing message with an associated error/success state, used for flash messages and status displays.
 *
 * @property value The message text to display.
 * @property isError Whether this is an error message (displayed in red) or a success message (displayed in green).
 */
data class Message(val value: String, val isError: Boolean = false) {
  val isBlank get() = value.isBlank()
  val isNotBlank get() = value.isNotBlank()

  val color get() = if (isError) WRONG_COLOR else CORRECT_COLOR

  fun isAssigned() = this != EMPTY_MESSAGE

  @Suppress("unused")
  fun isUnassigned() = this == EMPTY_MESSAGE

  override fun toString() = value

  companion object {
    /** Sentinel representing the absence of a message. */
    val EMPTY_MESSAGE = Message("")
  }
}
