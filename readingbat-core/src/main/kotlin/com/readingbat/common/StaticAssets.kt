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

package com.readingbat.common

import com.pambrose.common.util.pathOf
import com.readingbat.BuildConfig
import com.readingbat.common.Endpoints.STATIC_PATH

/**
 * Builds the URLs pages emit for static assets.
 *
 * Assets are served from this app's own classpath by default — every one of them is packaged in the
 * jar under `static/`. [Property.STATIC_URL_PREFIX] can point them at a CDN origin instead without a
 * rebuild. Route registration never goes through here: the static tree always mounts at
 * [STATIC_PATH], so a CDN-configured deployment still serves the files itself.
 */
object StaticAssets {
  /**
   * Cache-busting token appended to every asset URL. The files are served with a one-year
   * `max-age`, which is only safe because this changes each release — the filenames themselves
   * never do, so a replaced image would otherwise be unreachable behind warm caches.
   */
  private const val CACHE_VERSION = BuildConfig.CORE_VERSION

  /**
   * The configured prefix, normalized.
   *
   * `trimEnd` because [pathOf] joins with `/` without collapsing duplicates, so a prefix with a
   * trailing slash would emit `//`. `ifBlank` because [pathOf] drops empty elements, so a prefix of
   * `"/"` trims to `""` and would silently produce a relative URL.
   *
   * Read with `errorOnNonInit = false` deliberately: the 404 and error pages both emit asset URLs,
   * so a throw here before property initialization would recurse through the error handler.
   */
  internal fun urlPrefix(): String =
    Property.STATIC_URL_PREFIX
      .getProperty(STATIC_PATH, errorOnNonInit = false)
      .trimEnd('/')
      .ifBlank { STATIC_PATH }

  /** Builds a versioned URL for a static asset, e.g. `urlOf(ICONS, "favicon.ico")`. */
  fun urlOf(vararg elems: Any): String = "${pathOf(urlPrefix(), *elems)}?v=$CACHE_VERSION"
}
