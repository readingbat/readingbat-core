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

package com.github.readingbat

import TestContent
import com.github.readingbat.config.installs
import com.github.readingbat.config.intercepts
import com.github.readingbat.config.locations
import com.github.readingbat.config.routes
import com.github.readingbat.dsl.ReadingBatContent
import io.ktor.application.Application
import io.ktor.server.cio.CIO
import io.ktor.server.engine.commandLineEnvironment
import io.ktor.server.engine.embeddedServer


object ReadingBatServer {
  lateinit var myContent: ReadingBatContent

  fun start(userContent: ReadingBatContent) {
    myContent = userContent
    val port = Integer.parseInt(System.getProperty("PORT") ?: "8080")
    val clargs = commandLineEnvironment(emptyArray())
    embeddedServer(CIO, clargs).start(wait = true)
    //port = port,
    //watchPaths = listOf("readingbat-core"),
    // module = Application::mymodule

  }
}

internal fun Application.mymodule() {
  val readingBatContent = TestContent.content

  installs()
  intercepts()
  locations(readingBatContent)
  routes(readingBatContent)
}

internal class InvalidPathException(msg: String) : RuntimeException(msg)

internal class InvalidConfigurationException(msg: String) : Exception(msg)
