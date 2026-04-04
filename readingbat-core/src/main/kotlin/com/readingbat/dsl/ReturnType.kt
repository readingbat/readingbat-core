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

/**
 * Defines the expected return type of a challenge function.
 *
 * For Java challenges, the return type is automatically inferred from the source code ([Runtime]).
 * For Python and Kotlin challenges, it must be specified explicitly in the DSL using
 * [ChallengeGroup.includeFilesWithType] or within individual challenge definitions.
 *
 * The [typeStr] is the language-agnostic type representation used for display and answer comparison.
 */
enum class ReturnType(val typeStr: String) {
  Runtime("runtime"),

  BooleanType("boolean"),
  IntType("int"),
  FloatType("float"),
  StringType("String"),
  CharType("char"),

  BooleanArrayType("boolean[]"),
  IntArrayType("int[]"),
  FloatArrayType("float[]"),
  StringArrayType("String[]"),

  BooleanListType("List<Boolean>"),
  IntListType("List<Integer>"),
  FloatListType("List<Float>"),
  StringListType("List<String>"),
  ;

  companion object {
    val String.asReturnType get() = entries.firstOrNull { this == it.typeStr }
  }
}
