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

package com.github.readingbat.testcontent

fun combine2(strs: Array<String>): Array<String> {
  val retval = arrayOf(*strs)
  for (i in strs.indices) retval[i] = strs[i].uppercase()
  return retval
}

fun main() {
  println(combine2(arrayOf("Car", "wash")))
  println(combine2(arrayOf("Hello", " world")))
  println(combine2(arrayOf("Hello")))
  println(combine2(arrayOf()))
}
