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

package com.github.readingbat.dsl

enum class ReturnType(val typeStr: String) {

  Runtime("runtime"),

  BooleanType("boolean"),
  IntType("int"),
  StringType("String"),

  BooleanArrayType("boolean[]"),
  IntArrayType("int[]"),
  StringArrayType("String[]"),

  BooleanListType("List<Boolean>"),
  IntListType("List<Integer>"),
  StringListType("List<Strixng>");

  companion object {
    val String.asReturnType: ReturnType?
      get() = try {
        values().first { this == it.typeStr }
      } catch (e: NoSuchElementException) {
        null
      }
  }
}