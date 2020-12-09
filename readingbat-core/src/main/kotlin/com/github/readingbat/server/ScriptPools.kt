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

package com.github.readingbat.server

import com.github.pambrose.common.script.JavaScriptPool
import com.github.pambrose.common.script.KotlinScriptPool
import com.github.pambrose.common.script.PythonScriptPool
import com.github.readingbat.common.Property.JAVA_SCRIPTS_POOL_SIZE
import com.github.readingbat.common.Property.KOTLIN_SCRIPTS_POOL_SIZE
import com.github.readingbat.common.Property.PYTHON_SCRIPTS_POOL_SIZE
import mu.KLogging

internal object ScriptPools : KLogging() {

  internal val javaScriptPool by lazy {
    // Global context cannot be null for the java script engine
    JavaScriptPool(JAVA_SCRIPTS_POOL_SIZE.getProperty(5), false)
      .also { logger.info { "Created Java script pool with size ${it.size}" } }
  }

  internal val pythonScriptPool by lazy {
    PythonScriptPool(PYTHON_SCRIPTS_POOL_SIZE.getProperty(5), true)
      .also { logger.info { "Created Python script pool with size ${it.size}" } }
  }

  internal val kotlinScriptPool by lazy {
    KotlinScriptPool(KOTLIN_SCRIPTS_POOL_SIZE.getProperty(5), true)
      .also { logger.info { "Created Kotlin script pool with size ${it.size}" } }
  }
}