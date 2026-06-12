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

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Tests the directory-contents cache read/write. Previously the write used a composite key
 * (`dir-contents|<md5>`) while the read used the raw path, so the read always missed and GitHub was
 * re-hit on every group load; the write also accumulated via computeIfAbsent+addAll, growing the
 * shared cache list unbounded across reloads. The read/write must use one consistent key and the
 * write must overwrite.
 */
class DirCacheTest : StringSpec() {
  init {
    "readDirCache returns what writeDirCache stored under a consistent key" {
      ChallengeGroup.writeDirCache("java/Warmup-1", listOf("a.java", "b.java"))
      ChallengeGroup.readDirCache("java/Warmup-1") shouldBe listOf("a.java", "b.java")
    }

    "rewriting the dir cache overwrites instead of accumulating duplicates" {
      ChallengeGroup.writeDirCache("python/Group-2", listOf("a.py"))
      ChallengeGroup.writeDirCache("python/Group-2", listOf("a.py", "b.py"))
      ChallengeGroup.readDirCache("python/Group-2") shouldBe listOf("a.py", "b.py")
    }
  }
}
