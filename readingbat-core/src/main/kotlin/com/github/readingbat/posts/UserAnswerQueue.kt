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

package com.github.readingbat.posts

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

internal object UserAnswerQueue {
  private val logger = KotlinLogging.logger {}
  private const val MAX_QUEUE_SIZE = 50
  private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
  private val userChannels = ConcurrentHashMap<Long, Channel<AnswerJob<*>>>()

  private class AnswerJob<T>(
    val block: () -> T,
    val result: CompletableDeferred<Result<T>> = CompletableDeferred(),
  ) {
    fun run() {
      result.complete(runCatching { block() })
    }
  }

  class QueueFullException : Exception("Answer queue is full, request dropped")

  suspend fun <T> submitForUser(userDbmsId: Long, block: () -> T): T {
    val channel =
      userChannels.computeIfAbsent(userDbmsId) { id ->
        Channel<AnswerJob<*>>(MAX_QUEUE_SIZE).also { ch ->
          scope.launch(CoroutineName("answer-queue-user-$id")) {
            for (job in ch) {
              job.run()
            }
          }
        }
      }
    val job = AnswerJob(block)
    if (channel.trySend(job).isFailure) {
      logger.warn { "Answer queue full for user $userDbmsId, dropping request" }
      throw QueueFullException()
    }
    return job.result.await().getOrThrow()
  }
}
