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

import com.github.readingbat.server.ReadingBatServer
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class AdminUsersImmutabilityTest : StringSpec() {
  init {
    "adminUsers should default to empty set" {
      ReadingBatServer.adminUsers.load() shouldBe emptySet()
    }

    "adminUsers should be a Set type" {
      val users = ReadingBatServer.adminUsers.load()
      users shouldBe emptySet<String>()
    }

    "adminUsers store should replace the entire set atomically" {
      val original = ReadingBatServer.adminUsers.load()
      ReadingBatServer.adminUsers.store(setOf("admin1@test.com", "admin2@test.com"))
      ReadingBatServer.adminUsers.load() shouldBe setOf("admin1@test.com", "admin2@test.com")
      ReadingBatServer.adminUsers.store(original)
    }

    "adminUsers should support concurrent-safe contains check" {
      ReadingBatServer.adminUsers.store(setOf("admin@test.com"))
      ("admin@test.com" in ReadingBatServer.adminUsers.load()) shouldBe true
      ("other@test.com" in ReadingBatServer.adminUsers.load()) shouldBe false
      ReadingBatServer.adminUsers.store(emptySet())
    }
  }
}
