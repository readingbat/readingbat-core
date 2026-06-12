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

package com.readingbat.server.ws

/**
 * Returns a copy of this set taken while holding its intrinsic monitor, safe to iterate without a
 * `ConcurrentModificationException` even if other threads/coroutines add or remove entries
 * afterward. Intended for the `Collections.synchronizedSet` connection sets used by the WebSocket
 * pingers and dispatcher, whose iterators require external synchronization.
 */
internal fun <T> MutableSet<T>.snapshotUnderMonitor(): List<T> = synchronized(this) { toList() }
