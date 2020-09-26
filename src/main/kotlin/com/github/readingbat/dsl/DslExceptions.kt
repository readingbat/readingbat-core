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

package com.github.readingbat.dsl

import redis.clients.jedis.exceptions.JedisException

internal class InvalidRequestException(msg: String) : Exception(msg)

internal class InvalidConfigurationException(msg: String) : Exception(msg)

internal class MissingBrowserSessionException(msg: String) : Exception(msg)

internal class RedisUnavailableException(msg: String) : Exception(msg)

internal class DataException(val msg: String) : JedisException(msg)