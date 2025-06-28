/*
 * Copyright © 2025 Paul Ambrose (pambrose@mac.com)
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

description = "readingbat-kotest"

//mainClassName = 'TestMain' // This prevents a gradle error

dependencies {
  implementation(project(":readingbat-core"))

  implementation(libraries["gson"]!!)

  implementation(libraries["ktor_server_tests"]!!)
  implementation(libraries["ktor_server_test_host"]!!)

  implementation(libraries["kotest_runner_junit5"]!!)
  implementation(libraries["kotest_assertions_core"]!!)
  implementation(libraries["kotest_assertions_ktor"]!!)
//    implementation(libraries["kotest_property"]!!)
}

//kotlinter {
//  ignoreFailures = false
//  reporters = ['checkstyle', 'plain']
////        disabledRules = ["no-wildcard-imports",
////                         "indent",
////                         "final-newline",
////                         "comment-spacing",
////                         "max-line-length",
////                         "no-multi-spaces",
////                         "wrapping",
////                         "multiline-if-else",]
//}
