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

import kotlin.reflect.KType
import kotlin.reflect.typeOf

enum class ReturnType(val typeStr: String, val ktype: KType) {
  BooleanType("boolean", typeOf<Boolean>()),
  IntType("int", typeOf<Int>()),
  StringType("String", typeOf<String>()),

  BooleanArrayType("boolean[]", typeOf<Array<Boolean>>()),
  IntArrayType("int[]", typeOf<Array<Int>>()),
  StringArrayType("String[]", typeOf<Array<String>>()),

  BooleanListType("List<Boolean>", typeOf<List<Boolean>>()),
  IntListType("List<Integer>", typeOf<List<Int>>()),
  StringListType("List<Strixng>", typeOf<List<String>>());

  companion object {
    val String.asReturnType: ReturnType?
      get() = try {
        values().first { this == it.typeStr }
      } catch (e: NoSuchElementException) {
        null
      }
  }
}