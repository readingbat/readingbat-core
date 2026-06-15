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

package com.readingbat.posts

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Per-user coroutine-based answer processing queue that serializes database writes
 * for each user to prevent concurrent requests from overwriting each other's data.
 *
 * Each user gets a dedicated [Channel] that processes answer jobs sequentially,
 * ensuring consistent `incorrectAttempts` counts and answer history.
 */
internal object UserAnswerQueue {
  private val logger = KotlinLogging.logger {}
  private const val MAX_QUEUE_SIZE = 50

  /** Number of worker coroutines/channels in the fixed pool. */
  const val WORKER_COUNT = 16
  private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

  // A fixed-size pool of worker channels, each drained by one long-lived consumer coroutine.
  // Replaces the previous unbounded ConcurrentHashMap<userDbmsId, Channel> that retained one
  // channel + coroutine per distinct user forever. Each user is mapped to exactly one worker by
  // workerIndex(userDbmsId), so all of a given user's jobs are serialized on a single worker
  // (preserving per-user write ordering) while total channels/coroutines stay bounded at WORKER_COUNT.
  private val workers: List<Channel<AnswerJob<*>>> =
    List(WORKER_COUNT) { i ->
      Channel<AnswerJob<*>>(MAX_QUEUE_SIZE).also { ch ->
        scope.launch(CoroutineName("answer-queue-worker-$i")) {
          for (job in ch) {
            job.run()
          }
        }
      }
    }

  private class AnswerJob<T>(
    val block: () -> T,
    val result: CompletableDeferred<Result<T>> = CompletableDeferred(),
  ) {
    fun run() {
      result.complete(runCatching { block() })
    }
  }

  class QueueFullException : Exception("Answer queue is full, request dropped")

  /** The pool worker that owns a given user's serialized work. Stable per user, always in range. */
  internal fun workerIndex(userDbmsId: Long) = userDbmsId.mod(WORKER_COUNT)

  /** Submits a block of work to the user's assigned worker and suspends until it completes. */
  suspend fun <T> submitForUser(userDbmsId: Long, block: () -> T): T {
    val channel = workers[workerIndex(userDbmsId)]
    val job = AnswerJob(block)
    if (channel.trySend(job).isFailure) {
      logger.warn { "Answer queue full for user $userDbmsId, dropping request" }
      throw QueueFullException()
    }
    return job.result.await().getOrThrow()
  }
}
