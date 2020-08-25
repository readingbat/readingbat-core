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

import com.github.pambrose.common.script.JavaScriptPool
import com.github.pambrose.common.script.KotlinScriptPool
import com.github.pambrose.common.script.PythonScriptPool
import com.github.readingbat.common.PropertyNames.JAVA_SCRIPTS_POOL_SIZE
import com.github.readingbat.common.PropertyNames.KOTLIN_SCRIPTS_POOL_SIZE
import com.github.readingbat.common.PropertyNames.PYTHON_SCRIPTS_POOL_SIZE
import mu.KLogging

internal object ScriptPools : KLogging() {

  internal val pythonScriptPool by lazy {
    PythonScriptPool(System.getProperty(PYTHON_SCRIPTS_POOL_SIZE).toInt()
                       .also { logger.info { "Created Python script pool with size $it" } })
  }
  internal val javaScriptPool by lazy {
    JavaScriptPool(System.getProperty(JAVA_SCRIPTS_POOL_SIZE).toInt()
                     .also { logger.info { "Created Java script pool with size $it" } })
  }
  internal val kotlinScriptPool by lazy {
    KotlinScriptPool(System.getProperty(KOTLIN_SCRIPTS_POOL_SIZE).toInt()
                       .also { logger.info { "Created Kotlin script pool with size $it" } })
  }
}