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

import com.github.pambrose.common.util.join

internal object CommonUtils {

  internal fun pathOf(vararg elems: Any): String = elems.toList().map { it.toString() }.join("/")

  internal fun String.maskUrl() =
    if ("://" in this && "@" in this) {
      val scheme = split("://")
      val uri = split("@")
      "${scheme[0]}://*****:*****@${uri[1]}"
    }
    else {
      this
    }

  internal fun String.obfuscate(freq: Int = 2) =
    mapIndexed { i, v -> if (i % freq == 0) '*' else v }.joinToString("")
}
