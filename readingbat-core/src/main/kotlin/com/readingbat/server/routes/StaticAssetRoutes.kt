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

package com.readingbat.server.routes

import com.readingbat.BuildConfig
import com.readingbat.common.Constants.STATIC
import com.readingbat.common.Endpoints.STATIC_PATH
import io.ktor.http.CacheControl
import io.ktor.http.CacheControl.Visibility.Public
import io.ktor.http.content.EntityTagVersion
import io.ktor.server.http.content.staticResources
import io.ktor.server.routing.Route

/** One year, the practical maximum for `max-age`. */
private const val STATIC_MAX_AGE_SECS = 31_536_000

/** `Cache-Control` value for static assets, shared with the explicit Tailwind CSS route. */
const val STATIC_CACHE_CONTROL = "public, max-age=$STATIC_MAX_AGE_SECS"

/**
 * Serves the static asset tree — every image, icon, and Prism file — from this app's own classpath.
 *
 * Registered from both the server and the Kotest test module, which is the point of it living here:
 * the two used to call [staticResources] separately and drifted, leaving the route mounted at an
 * absolute CDN URL and therefore unreachable.
 *
 * The one-year `max-age` is safe only because every URL pages emit carries a `?v=<version>` query
 * (see `StaticAssets.urlOf`). The filenames never change, so without that a replaced image would sit
 * behind warm caches for a year with no way to invalidate it.
 *
 * `index = null` because there are no directory indexes here — without it a request for `/static` or
 * `/static/help` looks for a nonexistent `index.html` and falls through to the not-found page, which
 * answers 200.
 */
fun Route.staticAssetRoutes(): Route =
  staticResources(STATIC_PATH, STATIC, index = null) {
    cacheControl { [CacheControl.MaxAge(maxAgeSeconds = STATIC_MAX_AGE_SECS, visibility = Public)] }
    // Only populates content.versions; ConditionalHeaders is what emits the header and answers 304.
    etag { EntityTagVersion(BuildConfig.CORE_VERSION) }
  }
