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

import com.github.readingbat.common.AnswerPublisher
import com.github.readingbat.common.User
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import kotlin.reflect.full.declaredMemberFunctions

class AnswerPublisherTest : StringSpec() {
  init {
    "AnswerPublisher should declare publishAnswers" {
      val methodNames = AnswerPublisher::class.declaredMemberFunctions.map { it.name }
      methodNames shouldContain "publishAnswers"
    }

    "AnswerPublisher should declare publishLikeDislike" {
      val methodNames = AnswerPublisher::class.declaredMemberFunctions.map { it.name }
      methodNames shouldContain "publishLikeDislike"
    }

    "User should not declare publishAnswers" {
      val methodNames = User::class.declaredMemberFunctions.map { it.name }
      methodNames shouldNotContain "publishAnswers"
    }

    "User should not declare publishLikeDislike" {
      val methodNames = User::class.declaredMemberFunctions.map { it.name }
      methodNames shouldNotContain "publishLikeDislike"
    }

    "publishAnswers should accept User as first parameter" {
      val method = AnswerPublisher::class.declaredMemberFunctions.first { it.name == "publishAnswers" }
      // Parameters: extension receiver (if any), then the explicit params
      // For a top-level object function: first param is the instance, then the actual params
      val paramTypes = method.parameters.map { it.type.classifier }
      paramTypes shouldContain User::class
    }

    "publishLikeDislike should accept User as first parameter" {
      val method = AnswerPublisher::class.declaredMemberFunctions.first { it.name == "publishLikeDislike" }
      val paramTypes = method.parameters.map { it.type.classifier }
      paramTypes shouldContain User::class
    }
  }
}
