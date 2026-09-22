# Changelog

All notable changes to ReadingBat Core are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [3.4.0] - 2026-09-21

A rendering-correctness and maintenance release. The language tabs did not read as tabs in Safari — the selected tab left a hairline exactly where the divider was supposed to disappear — and the divider itself stopped short of both screen edges while the page quietly scrolled sideways. All three are fixed, the tab strip is now inset from the page edge, and the geometry is guarded by the suite's first WebKit-based tests. Also refreshes the toolchain and dependencies. No configuration or upgrade steps are required; this is a drop-in bump from 3.3.1.

### Fixed

- **The selected language tab left a hairline in WebKit.** The folder-tab effect works by having the selected tab paint its white background over the 1px divider below the strip, nudged into place with `#selected { position: relative; top: 1px }`. That only lands correctly if the tab's painted box ends exactly where the divider begins. `nav li` was `display: inline`, and an inline box is only as tall as the font's ascent plus descent — which Blink rounds to whole pixels (bottom 159, divider 158–159, exact cover) and WebKit leaves fractional (bottom **159.64**, divider **159–160**). The surviving 0.36px is ~0.7 device pixels at 2×: a line that looks thinner but never disappears. The tabs are now bottom-aligned inline-blocks, whose box ends at the line box bottom — where the divider starts — in every engine
- **The divider below the tab strip stopped 8px short of each screen edge.** It is a block inside `<body>`, which carries an 8px margin. It now carries `-mx-2` to cancel that gutter and spans the viewport exactly
- **The page scrolled sideways by 23px.** The tab-strip container carried `min-w-screen` (`min-width: 100vw`), and `100vw` includes the scrollbar — 1665px inside a 1650px viewport, so `scrollWidth` (1673) exceeded `clientWidth` (1650). Beyond the stray scrollbar, this also undid the fix above: scroll right and the full-width divider ran out again. Removed; the strip's natural width is identical

### Added

- `PlaywrightTabsTest`, four geometric regression tests that run in **both Chromium and WebKit**: the selected tab's painted box must reach past the divider's bottom edge, and the divider must start at 0, end at the viewport width, and leave the document with no horizontal overflow. The spec launches both engines deliberately — the hairline was invisible to Chromium at every font size probed, so a Chromium-only test could not have caught it. These are the first WebKit tests in the suite

### Changed

- The language tab strip is inset 37px from the left — one tab gap (the 25px + 6px margins plus the ~5.6px word space between two inline-block tabs) — so the first tab is spaced from the page edge the way the tabs are spaced from each other
- `.gitattributes` now sets `* text=auto` so the index normalizes to LF, and drops the `binary` attribute from `gradlew` and `*.bat`, which had been suppressing both diffs and the `eol` conversion those same lines requested. `gradlew.bat` is re-normalized to CRLF as a result (82 lines, no content change). The generated `static/tailwind.css` is marked `linguist-generated` and the vendored `static/prism/**` `linguist-vendored`, so GitHub collapses them in diffs
- `DESIGN.md` records the tab geometry as design rules: why the tabs must be bottom-aligned inline-blocks, why the rule runs full-bleed, and the 37px inset
- Bumped version to 3.4.0

### Dependencies

- Gradle 9.6.1 → 9.7.1
- Ktor 3.5.2 → 3.6.0
- Exposed 1.3.1 → 1.5.0
- Flyway 13.1.0 → 13.7.0
- Kotest 6.2.3 → 6.2.5
- Playwright 1.61.0 → 1.63.0
- Cloud SQL socket factory 1.29.0 → 1.30.0
- Resend 4.13.0 → 4.26.0
- prometheus-proxy 4.0.0 → 4.0.1
- detekt 2.0.0-alpha.5 → 2.0.0-alpha.6, buildconfig 6.0.10 → 6.1.1, versions plugin 0.57.0 → 0.64.0
- Website: zensical 0.0.52 → 0.0.63, pymdown-extensions 11.0.1 → 12.0.1, Pygments 2.20.0 → 2.21.0, deepmerge 2.1.0 → 3.0.1, click 8.4.2 → 8.5.0

## [3.3.1] - 2026-08-01

An accessibility, performance, and maintenance release. The headline fix is that answer grading was communicated by fill color alone and was never announced to assistive technology — it now carries text and a live region. Also lands image-weight reductions, a design-system record, a stale-artifact guard in CI, and a dependency refresh. No configuration or upgrade steps are required; this is a drop-in bump from 3.3.0.

### Fixed

- **Answer grading was inaccessible.** `checkAnswers` painted each result cell green or red and did nothing else, so a screen-reader user received no result at all and a colorblind user was left comparing two fills that measure **1.36:1** against each other — far below the 3:1 WCAG 2.1 AA non-text minimum, and a 1.4.1 (Use of Color) failure. Result cells now carry `✓ correct` / `✗ try again` text, and the status cell is a `role="status"` / `aria-live="polite"` region announcing `N of M correct.`
- Answer inputs had no accessible name; each now carries `aria-label="Return value for <invocation>"`
- Like/dislike controls had no accessible name, and their images were announced as content; the buttons now carry aria-labels and the images `alt=""`
- The sign-in modal's close control was a `span` with an `onClick`, so it was unreachable by keyboard; it is now a real `button` with `aria-label="Close sign-in dialog"` sized 28×28
- Pass/fail colors met contrast as fills but not as text. Added `rb-correct-text` (`#3E862E`) and `rb-wrong-text` (`#ED0000`) as separate text tokens, and darkened `rb-header` to `#337E9C` for AA at its rendered size
- `HEADER_COLOR` was emitted as an inline style on the same elements that already carried `text-rb-header`, so the inline value won and re-toning the token alone would have been a silent no-op. The constant and all 13 call sites are gone
- Link hover was `color: red`, colliding with the wrongness signal; it is now an underline
- The like/dislike and admin spinners used `fa-spin`, but Font Awesome is not loaded on those pages, so they never rendered. Replaced with a CSS `.rb-spinner` that honors `prefers-reduced-motion`
- The page wordmark is now an `h1`, the language nav carries `aria-label="Languages"`, and pages emit `<meta charset>` and a viewport tag
- Documentation referenced a `make dbreset` target that does not exist — in `README.md` (twice), `llms.txt`, and the docs site. All now name the real Flyway targets (`dbmigrate`, `dbclean`, `dbinfo`, `dbvalidate`). `llms.txt` also claimed a `releaseDate` key that is not in `gradle.properties`

### Performance

- Resized the four like/dislike PNGs from 1600px wide to 60px — they render at 30px. **345 KB → 12 KB**
- Converted `nervous.png` / `panic.png` to JPEG. **~450 KB → ~72 KB**
- Added explicit `width`/`height` (removing layout shift) and `loading` hints at all six `img` sites

### Added

- `PRODUCT.md`, `DESIGN.md`, and `.impeccable/design.json` record the product context and the design system the accessibility work was audited against
- A `Tailwind CSS` CI workflow that regenerates the stylesheet and fails if the checked-in artifact is stale. It runs on macOS because the vendored CLI is a Mach-O arm64 binary and the Gradle wiring only regenerates on macOS — which is how the artifact went stale in the first place
- `make check-site` / `make upgrade-site` / `make clean-docs` for managing the website dependencies

### Changed

- Regenerated `static/tailwind.css`, which had drifted from its source: the accessibility pass deleted rules and utility classes, but the minified artifact was never rebuilt, so it still carried CSS for classes nothing emits (80,540 → 78,905 bytes)
- Trimmed 57 lines of codebase-derivable content from `CLAUDE.md` (9,935 → 6,538 chars). Two of the removed lines were also wrong: a `make dbreset` target that does not exist, and a common-utils version that had drifted from the catalog
- The versions plugin is now applied by its catalog id rather than a hardcoded legacy string, matching how the Kotlin, Kotlinter, detekt, and Kover plugin ids are already derived — the catalog id change alone had left the deprecation warning in place
- Bumped version to 3.3.1

### Dependencies

- Ktor 3.5.1 → 3.5.2
- Flyway 13.0.0 → 13.1.0
- common-utils 3.2.1 → 3.2.2
- versions plugin 0.54.0 → 0.57.0 (id `com.github.ben-manes.versions` → `io.github.ben-manes.versions`)
- zensical 3.10.2 → 3.10.3, markdown 0.0.51 → 0.0.52 (website)

## [3.3.0] - 2026-07-26

A modernization + maintenance release. Adopts Kotlin's experimental collection-literal syntax across the codebase, refreshes dependencies (including major-version bumps to Flyway 13, prometheus-proxy 4.0, and common-utils 3.x), and lands a few code-quality cleanups. No configuration or upgrade steps are required — `./gradlew check` is green against the new toolchain.

### Changed

- Adopted Kotlin collection literals (an experimental 2.4 feature, enabled via the `-Xcollection-literals` compiler flag in `configureKotlin()`). 191 call sites across `src` and `test` were converted: `emptyList()` → `[]`, `listOf(...)` → `[...]`, and `mutableListOf(...)` → `[...]` with an explicit `MutableList<T>` type added to the declaration so mutability is never inferred away. The flag applies to project sources only — the JSR-223 script engine that evaluates content DSL files at runtime is unaffected, so DSL content keeps using `listOf()`/`mutableListOf()`
- Moved the Dokka configuration into a root-level `configureDokka()` helper and `configureVersions()` into an `allprojects {}` block in the root `build.gradle.kts`
- Removed redundant imports of symbols defined within a file's own object/companion (`Property.kt`, `ConfigureOAuth.kt`, `GeoInfo.kt`, `ReadingBatServer.kt`, `AdminRoutes.kt`)
- Simplified an early-return conditional in `Intercepts.isBrowsableContentPath` (`if (x) return true; return y` → `return x || y`, behavior-preserving)
- Bumped version to 3.3.0

### Fixed

- Resolved the unresolved `[initProperties]` KDoc link in `ContentDsl.kt` — it referenced a `Property` companion member not in scope, so Dokka couldn't resolve it and it rendered as plain text. Now uses the fully-qualified custom-link-text form, and Dokka module generation is warning-free

### Dependencies

- Kotlin 2.4.0 → 2.4.10
- common-utils 2.9.3 → 3.2.1
- prometheus-proxy 3.2.0 → 4.0.0
- Flyway 12.10.0 → 13.0.0
- Kotest 6.2.1 → 6.2.3
- Logback 1.5.18 → 1.5.38
- PostgreSQL driver 42.7.12 → 42.7.13
- Cloud SQL socket factory 1.28.6 → 1.29.0
- Kotlinter 5.5.0 → 5.6.0
- Kover 0.9.8 → 0.9.9

## [3.2.1] - 2026-07-03

A maintenance release: a Gradle 9.6.1 upgrade, a routine dependency refresh, and two build/test polish items. No functional changes to the running server.

### Changed

- Reworked the `configureVersions()` pre-release filter in the root `build.gradle.kts`: a candidate is now rejected only when the *current* version is stable, so dependencies intentionally tracked on a pre-release line (e.g. the detekt 2.0 alpha) still surface newer pre-releases. The qualifier regex now matches both dash-style (`-alpha`) and dot-style (Netty's `.Beta1`) suffixes while leaving stable classifiers like `-jre` / `.Final` / `-macos` alone
- Marked the `dependencyUpdates` task incompatible with the configuration cache so `make versions` no longer trips the cache
- `TestSupport.forEachAnswer` now accepts a `suspend` block, matching the suspend `functionInfo()` call site introduced in 3.2.0
- Bumped version to 3.2.1

### Dependencies

- Gradle wrapper 9.5.1 → 9.6.1
- common-utils 2.9.2 → 2.9.3
- Ktor 3.5.0 → 3.5.1
- Exposed 1.3.0 → 1.3.1
- Kotest 6.2.0 → 6.2.1
- Flyway 12.8.1 → 12.10.0
- PostgreSQL driver 42.7.11 → 42.7.12
- Google Cloud SQL socket factory 1.28.4 → 1.28.6
- Playwright 1.60.0 → 1.61.0
- detekt 2.0.0-alpha.4 → 2.0.0-alpha.5
- Vanniktech maven-publish 0.36.0 → 0.37.0

## [3.2.0] - 2026-06-15

A security-hardening release. A multi-agent security review surfaced 48 confirmed findings (7 high, 19 medium, 22 low); all 48 are addressed here, alongside the build/tooling cleanup below.

### Security

- Signed and encrypted all three session cookies (browser session, auth principal, OAuth return URL) with `SessionTransportTransformerEncrypt` (AES-128 + HMAC-SHA256). Previously the cookies were unsigned plaintext, so the `userId` could be forged for a trivial authentication bypass and full admin takeover
- Introduced a required `SESSION_SECRET` in production: the server refuses to start without it. It must be shared across all nodes; rotating it invalidates existing cookies. Generate with `openssl rand -hex 32`
- Enforced class-ownership authorization on teacher class-management actions via `ownsClass()`, closing an IDOR that let any teacher modify any class (including remove-from-class)
- Eliminated server-side code injection (RCE): Python/Kotlin answer comparison no longer interpolates the user response into an evaluated script — list/array answers are parsed and compared directly
- Fixed stored XSS in the teacher dashboard: student answers streamed over WebSockets are now HTML-escaped before rendering
- Fixed JS injection via the student full name in the remove-from-class `confirm()` handler and the other user-prefs `onSubmit` handlers (escaped with `escapeEcmaScript`)
- Enforced rate limiting with a global per-IP limiter (the RateLimit plugin was installed but never applied)
- Rotate the browser session cookie on login to prevent session fixation
- Require a verified email for both Google and GitHub OAuth logins; GitHub no longer creates blank-email user accounts
- Mask secrets in logs instead of revealing 75% of their characters
- Require admin access to subscribe to the operational logging WebSocket

### Fixed

- Bounded the answer-check loop by the challenge's invocation count, so an attacker-supplied response count can no longer trigger an `IndexOutOfBoundsException`
- Startup no longer crashes when an integer environment variable holds a non-numeric value
- WebSocket reliability: fixed concurrent-modification crashes in the ping/clock pinger loops, replaced the single shared dispatcher that head-of-line-blocked all clients, and collapsed N+1 blocking JDBC calls in WS coroutines; the answer-dashboard flow now drops the oldest message under backpressure instead of suspending the answer-submit handler
- DSL parser fixes: nested-brace handling in `convertToKotlinScript`, and source-line ordering in `extractJavaInvocations` (invocations now align with computed answers)
- `extractJavaFunction` reports a named "malformed challenge" error instead of an opaque crash when source has fewer than two `static` declarations
- Geo cache: short-circuits the DB on a cached hit, no longer serializes all lookups behind a global mutex, no longer caches transient failures permanently, and is now size-bounded (LRU)
- Fixed a dir-contents cache key mismatch that caused a permanent miss plus a leak
- Fixed an enrollment-rollback desync, an OAuth-link insert race (now an upsert), and a blocking fetch performed under a `computeIfAbsent` lock
- Quote-aware parsing of Python list answers so elements containing commas are no longer split and corrupted
- Background content-load failures are logged (with the failing source) instead of leaving the server permanently "not ready"
- Coalesced the request-logging path into a single transaction (was 3–4 transactions per request)

### Changed

- `Challenge.functionInfo()` is now `suspend`: the blocking JSR-223 script eval no longer bridges through `runBlocking` inside request/WS coroutines and runs on `Dispatchers.IO` (depends on the suspend-block `respondWith` in common-utils 2.9.2)
- Bounded the per-user answer-queue channels with a fixed-size worker pool keyed by user id (was an unbounded map of channels/coroutines)
- Per-property initialization tracking so the config "not initialized" error reflects the specific property
- Converted `upsert()` calls to Exposed's native `upsert()` and dropped the local upsert overload
- Deduplicated repeated `fetchClassTeacherId()` lookups in the class/student authorization paths and removed dead code (the `repo` getter guard, an unused content-root sentinel)
- Extracted shared WebSocket client JS (origin rewrite + summary `onmessage`) into `PageUtils`, and split `displayStudentProgress` into a data pass and a render pass
- Deduplicated the `/oauth` URL literal: introduced `Endpoints.OAUTH_PREFIX` and derived `OAUTH_LOGIN_*` / `OAUTH_CALLBACK_*` from it; `Intercepts.publicPrefixes` now references `OAUTH_PREFIX` instead of a duplicated string
- Trimmed redundant `"/static/"` entries from `publicPrefixes` and `readinessAllowedPrefixes` (already covered by `"/$STATIC/"`)
- Removed the unused `"/css.css"` entry from `Intercepts.publicPaths`
- Centralized toolchain versions in `gradle/libs.versions.toml`: added `gradle` and `jvm` keys so the version catalog is the single source of truth. The root `build.gradle.kts` now reads the JVM target from `libs.versions.jvm`, and the Makefile derives `GRADLE_VERSION` from `libs.versions.toml` so `make upgrade-wrapper` stays in sync with the catalog
- Extracted repeated string literals in the root `build.gradle.kts` into named `val`s (module paths, repo URL, SCM path, project name, secrets-env input key, JVM target)
- Migrated detekt from `io.gitlab.arturbosch.detekt` 1.23.8 to `dev.detekt` 2.0.0-alpha.3: updated plugin id and imports, switched the report block to `checkstyle.required` / `markdown.required`, dropped the obsolete top-level `build:` block from `config/detekt/detekt.yml`, and renamed `LongParameterList.functionThreshold` / `constructorThreshold` to `allowedFunctionParameters` / `allowedConstructorParameters`
- Applied the `kotlin.serialization` plugin to `readingbat-core/build.gradle.kts` via the catalog alias (root declares it `apply false`); the subproject already pulled in the `serialization` runtime dependency, this wires the compiler plugin so adapters generate correctly
- `make lint` now runs `lintKotlinMain`, `lintKotlinTest`, and `detekt` in a single Gradle invocation instead of invoking detekt twice via a prerequisite target
- Quoted `$GPG_SIGNING_KEY_ID` in the Makefile `GPG_ENV` block so a key id containing spaces or shell metacharacters can't break the export
- Bumped version to 3.2.0

### Added

- `codecov.yml` with project/patch status checks, ignore rules for build/generated/test sources, and `server` / `dsl` / `pages` / `common` components for per-area coverage visibility
- Self-documenting `make help` target that lists all public targets with their `## description` annotations; `default` now invokes `help` so a bare `make` prints the index
- `scripts/coverage_packages.py` — extracted the inline Python in `make coverage-packages` into a standalone script for readability and reuse
- Makefile private helper targets (`_check-gpg-env`, `_require-version`, `_require-gradle-version`) replacing inline `ifeq` error guards; publish targets now declare them as prerequisites

### Dependencies

- common-utils → 2.9.2 (suspend-block `respondWith`/`redirectTo`, native Exposed `upsert`, fixed upsert conflict indexes)
- detekt 2.0.0-alpha.3 → 2.0.0-alpha.4
- HikariCP 7.0.2 → 7.1.0
- Kotest 6.1.11 → 6.2.0
- prometheus-proxy 3.1.1 → 3.2.0

## [3.1.8] - 2026-05-04

### Changed

- Readiness interceptor now responds with `200 OK` instead of `503 Service Unavailable` so DigitalOcean's edge router does not substitute its own error body for the `ContentLoadingPage`. The page's meta-refresh and the `Retry-After` header still drive client retries
- Added `Cache-Control: no-store` to the loading-page response so proxies and browsers don't cache it past the loading window
- `ContentReadinessInterceptorTest` updated to assert the new `200` status and `Cache-Control` header
- Documented the recommended DigitalOcean App Platform health-check path (`/ping`) in `docs/digitalocean-notes.txt`
- Bumped version to 3.1.8

## [3.1.7] - 2026-05-04

### Changed

- `Application.module()` no longer blocks on `readContentDsl(...)`. The DSL load runs on `Dispatchers.IO` so the Ktor engine starts serving immediately and platform health checks (`/ping`) are reachable during cold starts
- New `Plugins`-phase interceptor in `Intercepts.kt` returns `503 Service Unavailable` + `Retry-After` and a cached `ContentLoadingPage` for any user-facing path until the first DSL load completes; `/ping`, `/static/*`, favicon, and robots stay available
- Replaced the one-shot `STARTUP_DELAY_SECS` warning with a 10-second polling loop that logs `Content not loaded after Ns` until the load finishes
- `ContentLoadingPage` HTML built once via `by lazy`; the meta-refresh interval and the `Retry-After` header share a single `RETRY_AFTER_SECS` constant
- `ReadingBatServer.contentReadCount` is now `private`; readers use `isContentReady: Boolean` (interceptor + warning loop) and `contentLoadCount: Int` (admin diagnostics page); `markContentLoaded()` encapsulates the increment
- Reverted the separate `healthRoutes` registration introduced in 3.1.6 — `/ping` is back inside `adminRoutes` because the new readiness gate makes the early registration unnecessary
- Bumped version to 3.1.7

### Added

- `ContentLoadingPageTest` covering the `RETRY_AFTER_SECS` constant, rendered HTML markers, and the by-lazy cache invariant
- `ContentReadinessInterceptorTest` exercising the gate end-to-end: 503 + `Retry-After` + loading body for blocked paths, allowlisted `/ping` and `/static/*` pass-through, and post-`markContentLoaded()` request flow
- `clean-all` Makefile target that runs `clean` + `clean-docs` and removes per-project `.gradle` caches

### Removed

- `Property.STARTUP_DELAY_SECS` and the matching `startupMaxDelaySecs` keys from `application-test.conf` and `application-travis.conf`

## [3.1.6] - 2026-05-03

### Changed

- Health/liveness route (`/ping`) split out of `adminRoutes` into a new `healthRoutes` and registered in `Application.module()` before `readContentDsl(...)`, so probes succeed while GitHub-backed content is still loading
- `TestSupport` updated to mirror the new health route registration order
- `releaseDate` resolution in `readingbat-core/build.gradle.kts` softened: falls back to today's date when the gradle property is absent or blank instead of erroring
- Removed now-redundant `releaseDate` from `gradle.properties`
- Hoisted `DokkaExtension` import and added explicit types in `readingbat-core/build.gradle.kts` for clarity
- Bumped Kover to 0.9.8
- Bumped version to 3.1.6

## [3.1.5] - 2026-05-03

### Changed

- Upgraded Gradle wrapper to 9.5.0 and `foojay-resolver-convention` to 1.0.0
- Enabled `org.gradle.configuration-cache=true` and `org.gradle.parallel=true`
- Moved `group`, `version`, and `releaseDate` from `build.gradle.kts` into `gradle.properties` as the source of truth (`-PoverrideVersion=...` overrides on the CLI)
- Consolidated the two `subprojects {}` blocks and inlined the kover plugin application
- Replaced ad-hoc secrets loading with a `ValueSource` registered as a task input on `Test`/`JavaExec`, so config-cache and up-to-date checks invalidate on `secrets/secrets.env` changes
- Tightened `dependencyUpdates` unstable-version detection with an anchored regex
- Restored lazy `provider { project.description }` so subproject POMs publish a non-empty `<description>`
- Restored `mustRunAfter("clean")` on `build` to keep `clean build` safe under parallel execution
- Use assignment form `mainClass = "TestMain"` and modern `tasks.named("build")` for the `stage` Heroku task
- Bumped dependencies: `prometheus-proxy` 3.1.1, `common-utils` 2.8.2, `flyway` 12.5.0, `postgres` 42.7.11, `versions` plugin 0.54.0
- Catalog: introduced a `kotest` bundle, factored out `flyway-core`/`flyway-postgres`, renamed `java-scripting` version key to `java-scriptengine` to match the artifact, restored kebab-case `simple-client` alias
- `dependencyUpdates` now invoked with `--no-parallel`
- Bumped version to 3.1.5

### Added

- `org.jetbrains.kotlinx.kover` 0.9.1 plugin with aggregated coverage reports at the root project
- CI step running `koverXmlReport` and uploading coverage to Codecov via `codecov-action@v5`
- `make coverage`, `make coverage-html`, and `make coverage-verify` targets

### Removed

- Unused `kotlin-css` dependency from the version catalog
- `useMavenLocal` flag and corresponding `mavenLocal()` repository declarations
- `local-build` Makefile target and the inline `-PreleaseDate=...` flag (now read from `gradle.properties`)
- `cc` Makefile target

## [3.1.4] - 2026-04-25

### Changed

- Centralized repository declarations in `settings.gradle.kts` (`FAIL_ON_PROJECT_REPOS`)
- Refactored root `build.gradle.kts` to scope Kotlin/publishing/lint/test config per-subproject via helpers
- Bumped dependencies: Kotlin 2.3.21, Ktor 3.4.3, Exposed 1.2.0, Kotest 6.1.11, Kotlinter 5.4.2, Dokka 2.2.0, Postgres 42.7.10, Hikari 7.0.2, Flyway 12.4.0, Playwright 1.59.0, common-utils 2.8.1, plus other minor bumps
- Cleaned up unused libraries from the version catalog
- Bumped version to 3.1.4

### Added

- Playwright-based browser tests (`PlaywrightAuthTest`, `PlaywrightEndpointTest`) replacing legacy Cypress specs
- `.claude/skills/playwright-cli/` documentation and references for Playwright CLI workflows

### Removed

- Legacy Cypress example specs, integration tests, fixtures, and `package.json`
- Unused `EmailUtils.kt` and `Emailer.kt`

## [3.1.3] - 2026-04-12

### Changed

- Migrated Exposed ORM from Joda-Time (`exposed-jodatime`) to kotlinx-datetime (`exposed-kotlin-datetime`)
- All datetime columns now use `timestamp()` with `kotlin.time.Instant` instead of Joda `datetime()` with `DateTime`
- OAuth providers are now auto-configured based on credential availability instead of requiring production mode
- Replaced stringly-typed OAuth provider parameter with `OAuthProvider` enum moved to `common` package
- Deduplicated `queryActiveTeachingClassCode`/`queryPreviousTeacherClassCode` into shared `queryClassCode` helper
- Extracted `completeOAuthLogin` from duplicate GitHub/Google OAuth callback sequences
- Replaced like/dislike magic numbers (0/1/2) with `LikeDislike` enum
- Replaced per-enrollee UPDATE loop with bulk `inList` update in `unenrollEnrolleesClassCode`
- Replaced 6 materializing queries with COUNT queries in `deleteUser` logging
- Pre-fetched active session counts before HTML rendering in SessionsPage
- Set test configuration to non-production mode
- Bumped version to 3.1.3

### Fixed

- `extractBalancedContent` now correctly handles parentheses inside single- and double-quoted strings
- Added cache invalidation for `userIdCache` and `emailCache` in `deleteUser`
- `safeRedirectPath` now normalizes backslashes before validation, preventing potential open redirect via browser backslash-to-slash normalization

### Added

- `InstantExpr.kt` with `nowInstant()` and `instantExpr()` helpers for kotlinx-datetime migration
- `LikeDislike` enum for type-safe like/dislike state
- `OAuthProvider` enum for type-safe OAuth provider identification
- `withTestApp` test helper eliminating repeated 5-line setup boilerplate across test files
- `DateTimeMigrationTest` with 8 tests covering datetime migration
- `CodeQualityFixesTest` with 14 tests covering code quality fixes
- `for_loop1` Python challenge
- Test for parentheses inside quoted strings in `ParseUtilsTest`
- Flyway V004 migration to drop unused `salt` and `digest` columns
- Flyway V005 migration to drop unused `access_token` column from `oauth_links`
- Test coverage for backslash-based open redirect bypass in `SafeRedirectTest`

### Removed

- Joda-Time dependency (`exposed-jodatime`)
- Unused `salt` and `digest` columns from `UsersTable`
- Commented-out dead code constants in `User.companion`
- Redundant `apply true` from Gradle plugin declarations
- `.travis.yml` (obsolete CI configuration)
- Redundant `isOAuthConfigured` property and outer OAuth route guard
- Unnecessary self-imports in `ConfigureOAuth` and `ReadingBatServer`
- Plaintext `access_token` column from `OAuthLinksTable` — tokens were stored but never read back after OAuth login

## [Unreleased] (3.0.12)

### Changed

- Renamed package from `com.github.readingbat` to `com.readingbat`
- Organized tests into subpackages
- Renamed root project for Dokka aggregation
- Upgraded Kotest to 6.1.11

### Added

- KDoc documentation across the codebase
- GitHub Actions workflows for CI tests, KDoc generation, and CodeQL analysis
- Dokka aggregation configuration for multi-module KDoc output
- GitHub community health files (CONTRIBUTING, CODE_OF_CONDUCT, etc.)

### Removed

- reCAPTCHA support

## [3.0.11] - 2026-03-30

### Changed

- Replaced SERIALIZABLE retry with per-user answer queue for concurrent answer handling
- Renamed "Caller version" to "Site version" on system configuration page
- Upgraded Ktor to 3.4.2

### Added

- OAuth fallback error handling with user-facing error messages

## [3.0.10] - 2026-03-28

### Fixed

- Serialization error on rapid answer submissions (#66)

## [3.0.9] - 2026-03-27

### Changed

- Filtered request logs to known users only (#65)

## [3.0.8] - 2026-03-27

### Changed

- Cleaned up package.json and included root in logged endpoints
- Removed commented-out code in Installs.kt

## [3.0.7] - 2026-03-27

### Added

- Playwright Java browser tests for Kotest integration

## [3.0.6] - 2026-03-26

### Changed

- Upgraded Gradle to 9.4.1
- Upgraded Kotest to 6.1.8
- Replaced Gson with kotlinx.serialization (#63)

## [3.0.5] - 2026-03-20

### Added

- CharType support for challenge return values (#62)

## [3.0.4] - 2026-03-20

### Changed

- Disabled OAuth authentication in dev mode (#61)

## [3.0.3] - 2026-03-19

- Initial tracked release

[Unreleased]: https://github.com/readingbat/readingbat-core/compare/3.1.4...HEAD
[3.1.4]: https://github.com/readingbat/readingbat-core/compare/3.1.3...3.1.4
[3.1.3]: https://github.com/readingbat/readingbat-core/compare/3.1.2...3.1.3
[3.0.11]: https://github.com/readingbat/readingbat-core/compare/3.0.10...3.0.11
[3.0.10]: https://github.com/readingbat/readingbat-core/compare/3.0.9...3.0.10
[3.0.9]: https://github.com/readingbat/readingbat-core/compare/3.0.8...3.0.9
[3.0.8]: https://github.com/readingbat/readingbat-core/compare/3.0.7...3.0.8
[3.0.7]: https://github.com/readingbat/readingbat-core/compare/3.0.6...3.0.7
[3.0.6]: https://github.com/readingbat/readingbat-core/compare/3.0.5...3.0.6
[3.0.5]: https://github.com/readingbat/readingbat-core/compare/3.0.4...3.0.5
[3.0.4]: https://github.com/readingbat/readingbat-core/compare/3.0.3...3.0.4
[3.0.3]: https://github.com/readingbat/readingbat-core/releases/tag/3.0.3
