# Release Notes

## v3.4.0 — 2026-09-21

A rendering-correctness and maintenance release. The language tabs did not read as tabs in Safari, and the rule beneath them stopped short of both screen edges. No configuration or upgrade steps are required; this is a drop-in bump from 3.3.1.

### Highlights

- **The selected tab is now open in every browser.** The folder-tab effect works by having the selected tab paint its white background over the 1px divider below the strip, nudged down with `top: 1px`. That only lands if the tab's painted box ends exactly where the divider begins — and `nav li` was `display: inline`, so its height came from the font's ascent plus descent. Blink rounds that to whole pixels and covered the rule exactly; WebKit leaves it fractional (box bottom **159.64** against a divider at **159–160**), so 0.36px of black survived — about 0.7 device pixels at 2×, a line that looks thinner but never disappears. The tabs are now bottom-aligned inline-blocks, whose box ends at the line box bottom, which is where the divider starts, in every engine.
- **The rule beneath the tabs runs edge to edge.** It is a block inside `<body>`, whose 8px margin was clipping it at both ends; it now carries a negative margin that cancels that gutter. A structural rule that stops short of the edge reads as a mistake rather than as a margin.
- **The page no longer scrolls sideways.** The tab-strip container was `min-width: 100vw`, and `100vw` counts the scrollbar — 1665px inside a 1650px viewport, 23px of stray horizontal scroll. It also undid the fix above the moment you scrolled right.
- **The tab strip is inset from the page edge** by 37px — one tab gap — so the first tab is spaced from the edge the way the tabs are spaced from each other.
- **The suite now tests in two engines.** `PlaywrightTabsTest` asserts both promises — the selected tab covers the divider, and the divider spans the viewport with no overflow — in Chromium *and* WebKit. The hairline was invisible to Chromium at every font size probed, so a Chromium-only test could never have caught it. These are the first WebKit tests in the project.

### Dependencies

Gradle 9.6.1 → 9.7.1 · Ktor 3.5.2 → 3.6.0 · Exposed 1.3.1 → 1.5.0 · Flyway 13.1.0 → 13.7.0 · Kotest 6.2.3 → 6.2.5 · Playwright 1.61.0 → 1.63.0 · Cloud SQL socket factory 1.29.0 → 1.30.0 · Resend 4.13.0 → 4.26.0 · prometheus-proxy 4.0.0 → 4.0.1 · detekt alpha.5 → alpha.6 · buildconfig 6.0.10 → 6.1.1 · versions plugin 0.57.0 → 0.64.0 · zensical 0.0.52 → 0.0.63

**Full Changelog**: https://github.com/readingbat/readingbat-core/compare/3.3.1...3.4.0

---

## v3.3.1 — 2026-08-01

An accessibility, performance, and maintenance release. The headline fix is that answer grading — the single most important thing the app tells a student — was communicated by fill color alone and was never announced to assistive technology. No configuration or upgrade steps are required; this is a drop-in bump from 3.3.0.

### Highlights

- **Answer grading is now perceivable without color or sight.** `checkAnswers` painted each result cell green or red and did nothing else: a screen-reader user received no result at all, and a colorblind user was left comparing two fills that measure **1.36:1** against each other — far below the 3:1 WCAG 2.1 AA non-text minimum, and a 1.4.1 (Use of Color) failure. Result cells now carry `✓ correct` / `✗ try again` text, and the status cell is a `role="status"` / `aria-live="polite"` region announcing `N of M correct.`
- **Controls have accessible names.** Answer inputs gained `aria-label="Return value for <invocation>"`; the like/dislike buttons gained labels with their images marked decorative; and the sign-in modal's close control — a `span` with an `onClick`, unreachable by keyboard — became a real `button`.
- **Text colors now pass AA.** The pass/fail colors met contrast as fills but not as text, so `rb-correct-text` (`#3E862E`) and `rb-wrong-text` (`#ED0000`) were added as separate text tokens and `rb-header` was darkened to `#337E9C`. Re-toning the tokens alone would have changed nothing: `HEADER_COLOR` was being emitted as an inline style on the same elements, so the inline value won. That constant and all 13 call sites are gone.
- **Spinners that were never visible now work.** The like/dislike and admin spinners used `fa-spin`, but Font Awesome is not loaded on those pages. Replaced with a CSS `.rb-spinner` that honors `prefers-reduced-motion`.
- **Images got much lighter.** The four like/dislike PNGs were 1600px wide and render at 30px (**345 KB → 12 KB**), and `nervous`/`panic` moved to JPEG (**~450 KB → ~72 KB**). All six `img` sites gained explicit dimensions, removing layout shift.
- **The generated stylesheet can no longer drift unnoticed.** `static/tailwind.css` is checked in but the Gradle wiring only regenerates it on macOS, so the accessibility pass left it stale — still carrying CSS for classes nothing emits. It has been rebuilt (80,540 → 78,905 bytes), and a new CI workflow now regenerates it and fails if the committed artifact does not match.
- **A design-system record.** `PRODUCT.md`, `DESIGN.md`, and `.impeccable/design.json` capture the product context and the design system the accessibility work was audited against.

### Dependencies

Ktor 3.5.1 → 3.5.2 · Flyway 13.0.0 → 13.1.0 · common-utils 3.2.1 → 3.2.2 · versions plugin 0.54.0 → 0.57.0 · zensical 3.10.2 → 3.10.3 · markdown 0.0.51 → 0.0.52

**Full Changelog**: https://github.com/readingbat/readingbat-core/compare/3.3.0...3.3.1

---

## v3.3.0 — 2026-07-26

A modernization + maintenance release. It adopts Kotlin's experimental collection-literal syntax across the codebase, refreshes dependencies (with major-version bumps to Flyway 13, prometheus-proxy 4.0, and common-utils 3.x), and lands a handful of code-quality cleanups. No configuration or upgrade steps are required — this is a drop-in bump from 3.2.1, and `./gradlew check` is green against the new toolchain.

### Highlights

- **Kotlin collection literals.** Enabled the experimental `-Xcollection-literals` compiler flag and converted 191 call sites across `src` and `test`: `emptyList()` → `[]`, `listOf(...)` → `[...]`, and `mutableListOf(...)` → `[...]` — the latter with an explicit `MutableList<T>` type on the declaration so mutability is never inferred away. The flag scopes to project sources only; the JSR-223 engine that evaluates content DSL files at runtime is unaffected, so DSL content continues to use `listOf()`/`mutableListOf()`.
- **Dokka is warning-free.** Fixed the unresolved `[initProperties]` KDoc link in `ContentDsl.kt` (a `Property` companion member that wasn't in scope) using the fully-qualified custom-link-text form, so the module now generates with zero warnings. The Dokka configuration also moved to a root-level `configureDokka()` helper, and `configureVersions()` moved into an `allprojects {}` block.
- **Code-quality cleanups.** Removed redundant imports of symbols defined within a file's own object/companion (five files), and simplified an early-return conditional in `Intercepts.isBrowsableContentPath` (`if (x) return true; return y` → `return x || y`, behavior-preserving).

### Dependencies

Kotlin 2.4.0 → 2.4.10 · common-utils 2.9.3 → 3.2.1 · prometheus-proxy 3.2.0 → 4.0.0 · Flyway 12.10.0 → 13.0.0 · Kotest 6.2.1 → 6.2.3 · Logback 1.5.18 → 1.5.38 · PostgreSQL driver 42.7.12 → 42.7.13 · Cloud SQL socket factory 1.28.6 → 1.29.0 · Kotlinter 5.5.0 → 5.6.0 · Kover 0.9.8 → 0.9.9

**Full Changelog**: https://github.com/readingbat/readingbat-core/compare/3.2.1...3.3.0

---

## v3.2.1 — 2026-07-03

Maintenance release. A Gradle 9.6.1 upgrade, a routine dependency refresh, and two small build/test polish items. No functional changes to the running server, and no configuration or upgrade steps are required — this is a drop-in bump from 3.2.0.

### Highlights

- **Gradle 9.6.1.** The wrapper moves from 9.5.1 to 9.6.1.
- **Smarter pre-release version filter.** `configureVersions()` now rejects a pre-release candidate only when the *current* dependency is stable, so libraries deliberately tracked on a pre-release line (e.g. the detekt 2.0 alpha) keep surfacing newer pre-releases in `make versions`. The qualifier regex now catches both dash-style (`-alpha`) and dot-style (Netty's `.Beta1`) suffixes while leaving stable classifiers like `-jre` / `.Final` / `-macos` alone, and the `dependencyUpdates` task is marked incompatible with the configuration cache.
- **`TestSupport.forEachAnswer` takes a `suspend` block**, matching the suspend `functionInfo()` call site introduced in 3.2.0.

### Dependencies

Gradle 9.5.1 → 9.6.1 · common-utils 2.9.2 → 2.9.3 · Ktor 3.5.0 → 3.5.1 · Exposed 1.3.0 → 1.3.1 · Kotest 6.2.0 → 6.2.1 · Flyway 12.8.1 → 12.10.0 · PostgreSQL driver 42.7.11 → 42.7.12 · Cloud SQL socket factory 1.28.4 → 1.28.6 · Playwright 1.60.0 → 1.61.0 · detekt 2.0.0-alpha.4 → 2.0.0-alpha.5 · maven-publish 0.36.0 → 0.37.0

**Full Changelog**: https://github.com/readingbat/readingbat-core/compare/3.2.0...3.2.1

---

## v3.2.0 — 2026-06-15

Security-hardening release. A multi-agent security review surfaced 48 confirmed findings (7 high, 19 medium, 22 low); all 48 are addressed here. The headline items close an authentication bypass, an RCE, two IDOR/authorization gaps, and stored/reflected XSS, plus a batch of WebSocket and caching reliability fixes. The 3.1.9-era build/tooling cleanup ships in the same release.

### ⚠️ Upgrade note — `SESSION_SECRET` is now required in production

Session cookies are now signed and encrypted, which means the server **will not start in production without a `SESSION_SECRET`**. Generate one with:

```
openssl rand -hex 32
```

Set it via the `SESSION_SECRET` environment variable. It must be the **same value on every node** (so cookies validate across instances), and **rotating it invalidates all existing sessions** (users must log in again).

### Security highlights

- **Signed + encrypted session cookies.** All three session cookies use `SessionTransportTransformerEncrypt` (AES-128 + HMAC-SHA256). The cookies were previously unsigned plaintext, so the `userId` could be hand-edited for a trivial authentication bypass and full admin/sysadmin takeover.
- **Server-side code injection (RCE) removed.** Python/Kotlin answer checking no longer interpolates the user's response into an evaluated script; list/array answers are parsed and compared directly.
- **Teacher IDOR closed.** Class-management actions (including remove-from-class) now verify class ownership via `ownsClass()`.
- **XSS fixes.** Student answers streamed over WebSockets are HTML-escaped before rendering, and user-controlled names in `confirm()` / `onSubmit` handlers are escaped with `escapeEcmaScript`.
- **OAuth hardening.** Google and GitHub logins require a verified email; GitHub no longer creates blank-email accounts. Login rotates the browser session to prevent fixation.
- **Rate limiting enforced** with a global per-IP limiter (previously installed but never applied); secrets are masked in logs; the operational logging WebSocket requires admin.

### Reliability highlights

- **WebSocket robustness.** Fixed concurrent-modification crashes in the pinger loops, removed a single shared dispatcher that head-of-line-blocked every client, collapsed N+1 blocking JDBC in WS coroutines, and made the answer-dashboard flow drop-oldest under backpressure instead of suspending the submit handler.
- **Caching fixes.** Geo cache now short-circuits the DB, drops its global mutex, stops permanently caching failures, and is size-bounded; a dir-contents cache key mismatch (permanent miss + leak) is fixed; per-user answer-queue channels are bounded by a fixed worker pool.
- **Parser + startup fixes.** Nested-brace Kotlin script conversion, Java invocation ordering, quote-aware Python list parsing, a non-numeric env-var startup crash, and a bounded answer-check loop (no more attacker-triggered `IndexOutOfBoundsException`).
- **`Challenge.functionInfo()` is now `suspend`** — the blocking script eval no longer bridges through `runBlocking` inside request/WS coroutines and runs on `Dispatchers.IO`.

### Build & tooling highlights

- **`/oauth` URL deduped.** New `Endpoints.OAUTH_PREFIX` constant; `OAUTH_LOGIN_*` and `OAUTH_CALLBACK_*` endpoints derive from it, and `Intercepts.publicPrefixes` references the constant instead of a duplicated literal.
- **Allowlist cleanup.** Removed the redundant `"/static/"` entries from `publicPrefixes` / `readinessAllowedPrefixes` (already covered by `"/$STATIC/"`) and the unused `"/css.css"` entry from `publicPaths`.
- **Codecov config.** Added `codecov.yml` with auto project target (1% threshold), informational 70% patch target, ignores for build/generated/test sources, and per-area components (`server`, `dsl`, `pages`, `common`).
- **Toolchain versions in the catalog.** `gradle` and `jvm` keys land in `gradle/libs.versions.toml` so the version catalog is the single source of truth for the build toolchain. `build.gradle.kts` reads the JVM target from `libs.versions.jvm`, and `make upgrade-wrapper` derives the Gradle version from the same place — no more drift between the wrapper, Makefile, and build script.
- **Build-script literal cleanup.** Repeated string literals in the root `build.gradle.kts` (module paths, repo URL, SCM path, project name, secrets-env input key) collapsed into named `val`s.
- **Detekt 2.0 alpha.** Plugin id moves from `io.gitlab.arturbosch.detekt` to `dev.detekt` (1.23.8 → 2.0.0-alpha.3). Imports updated, the report block switches to `checkstyle.required` / `markdown.required`, and `config/detekt/detekt.yml` is migrated (obsolete `build:` block removed; `LongParameterList.functionThreshold` / `constructorThreshold` renamed to `allowedFunctionParameters` / `allowedConstructorParameters`).
- **Kotlin serialization plugin wired explicitly.** `readingbat-core/build.gradle.kts` now applies `libs.plugins.kotlin.serialization` via the catalog alias so the compiler plugin is in effect for the subproject (root keeps the `apply false` declaration).
- **Makefile tightening.** New `make help` target prints a self-documenting index of every target with a `## description` annotation, and a bare `make` invokes it. `make lint` now runs Kotlinter and detekt in a single Gradle invocation instead of double-running detekt via a prerequisite. The inline Python in `make coverage-packages` moves to `scripts/coverage_packages.py`. Inline `ifeq` version guards become explicit prerequisite targets (`_check-gpg-env`, `_require-version`, `_require-gradle-version`), and `$GPG_SIGNING_KEY_ID` is properly quoted in the signing block.

### Dependencies

common-utils → 2.9.2 · detekt → 2.0.0-alpha.4 · HikariCP → 7.1.0 · Kotest → 6.2.0 · prometheus-proxy → 3.2.0

**Full Changelog**: https://github.com/readingbat/readingbat-core/compare/3.1.8...3.2.0

---

## v3.1.8 — 2026-05-04

Hot-fix for DigitalOcean App Platform deploys: the 3.1.7 readiness gate returned `503` for user-facing paths during the loading window, but DO's edge router substitutes its own body for upstream 5xx responses, so the `ContentLoadingPage` HTML never reached the browser.

### Highlights

- **Readiness response is now `200 OK`.** The loading-page response status flips from `503 Service Unavailable` to `200 OK`. The page's `meta http-equiv="refresh"` and the `Retry-After: 5` header still drive automatic client retries, but DO no longer treats the response as a failed upstream and substitutes its own page.
- **`Cache-Control: no-store` on the loading page.** Prevents intermediate proxies and browser caches from serving the loading page after content is ready.
- **Health-check guidance.** `docs/digitalocean-notes.txt` now documents pointing the App Platform HTTP health check at `/ping` so the instance stays healthy through cold starts.

**Full Changelog**: https://github.com/readingbat/readingbat-core/compare/3.1.7...3.1.8

---

## v3.1.7 — 2026-05-04

Background DSL content loading and a readiness gate so platform health checks pass during cold starts.

### Highlights

- **Non-blocking startup.** `Application.module()` no longer waits for `readContentDsl(...)` before returning. The DSL load runs on `Dispatchers.IO`, so the Ktor engine starts serving immediately and `/ping` is reachable from the first second of the deploy. This fixes DigitalOcean (and similar platforms) timing out the deployment health check while GitHub-backed content is still being fetched and evaluated.
- **Loading page.** A new `Plugins`-phase interceptor in `Intercepts.kt` returns `503 Service Unavailable` with `Retry-After: 5` and a cached `ContentLoadingPage` for any user-facing path until the first DSL load completes. The page meta-refreshes every 5 seconds, so browsers automatically pick up the site once content is ready. A small allowlist (`/ping`, `/static/*`, favicon, robots, ktor shutdown) stays available throughout startup.
- **Heartbeat warnings.** A 10-second polling loop logs `Content not loaded after Ns` until the load finishes — useful for spotting deploys where the DSL evaluation has stalled or failed. Replaces the old one-shot `STARTUP_DELAY_SECS` warning.
- **Encapsulated readiness flag.** `ReadingBatServer.contentReadCount` is now private; the interceptor uses `isContentReady: Boolean` and the admin diagnostics page uses `contentLoadCount: Int`. `Property.STARTUP_DELAY_SECS` and its `application-test.conf` / `application-travis.conf` entries are removed.
- **Revert of the 3.1.6 health-route split.** `/ping` is folded back into `adminRoutes`; the new readiness gate makes the early `healthRoutes` registration unnecessary.
- **Test coverage.** New `ContentLoadingPageTest` pins down the rendered HTML and the lazy-cache invariant, and `ContentReadinessInterceptorTest` drives the gate end-to-end through Ktor `testApplication` (blocked path → 503 + loading page; allowlisted paths → 200; post-`markContentLoaded()` → handler reached).
- **Makefile.** New `clean-all` target runs `clean` + `clean-docs` and wipes the per-project `.gradle` caches.

**Full Changelog**: https://github.com/readingbat/readingbat-core/compare/3.1.6...3.1.7

---

## v3.1.6 — 2026-05-03

Health-check routing fix, build-script polish, and a Kover patch bump.

### Highlights

- **Health route registered early.** The `/ping` endpoint is now split out of `adminRoutes` into a dedicated `healthRoutes` function and wired into `Application.module()` before `readContentDsl(...)` runs. Liveness/readiness probes succeed while GitHub-backed content is still loading, so platforms that gate traffic on `/ping` no longer black-hole during cold starts. `TestSupport` mirrors the new registration order.
- **`releaseDate` fallback.** `releaseDate` resolution in `readingbat-core/build.gradle.kts` now falls back to today's date when the gradle property is missing or blank, instead of failing the build. The corresponding `releaseDate=` line was dropped from `gradle.properties`.
- **Build-script polish.** Hoisted the `DokkaExtension` import and added explicit types in `readingbat-core/build.gradle.kts` to keep the file readable as it grows.
- **Kover 0.9.8.** Patch bump from 0.9.1.

**Full Changelog**: https://github.com/readingbat/readingbat-core/compare/3.1.5...3.1.6

---

## v3.1.5 — 2026-05-03

Gradle 9.5.0 upgrade, Kover code coverage, Codecov integration, and a focused round of build-script hardening.

### Highlights

- **Gradle 9.5.0.** Wrapper upgraded; `foojay-resolver-convention` bumped to 1.0.0. Configuration cache and parallel project execution are now enabled by default (`org.gradle.configuration-cache=true`, `org.gradle.parallel=true`).
- **Kover + Codecov.** New `org.jetbrains.kotlinx.kover` plugin (0.9.1) applied across subprojects with aggregated reports at the root. CI now runs `./gradlew build koverXmlReport` and uploads `build/reports/kover/report.xml` via `codecov-action@v5`. New `make coverage`, `make coverage-html`, and `make coverage-verify` targets.
- **Build-script hardening.** Secrets loading from `secrets/secrets.env` now goes through a proper `ValueSource` registered as a task input on `Test`/`JavaExec`, so config-cache and up-to-date checks invalidate when secrets change. `dependencyUpdates` rejection regex anchored on separators to avoid false positives. `mustRunAfter("clean")` restored on `build`. Lazy `provider { project.description }` restored so subproject POMs publish a non-empty `<description>`.
- **`gradle.properties` consolidation.** `group`, `version`, and `releaseDate` moved out of the build script and into `gradle.properties` as the source of truth; `-PoverrideVersion=...` overrides on the CLI. The two `subprojects {}` blocks were merged.
- **Dependency bumps.** `prometheus-proxy` 3.1.1, `common-utils` 2.8.2, `flyway` 12.5.0, `postgres` 42.7.11, `versions` plugin 0.54.0. Catalog cleanup: dropped unused `kotlin-css`, renamed `java-scripting` version key to `java-scriptengine` to match the artifact, restored the kebab-case `simple-client` alias, and added a `kotest` bundle.

**Full Changelog**: https://github.com/readingbat/readingbat-core/compare/3.1.4...3.1.5

---

## v3.1.4 — 2026-04-25

Build modernization, dependency bumps, and frontend test migration from Cypress to Playwright.

### Highlights

- **Build refactor.** Repository declarations centralized in `settings.gradle.kts` with `FAIL_ON_PROJECT_REPOS`. Root `build.gradle.kts` now scopes Kotlin, publishing, lint, and test configuration per-subproject via small helper functions, making each subproject's build behavior easier to reason about.
- **Dependency bumps.** Kotlin 2.3.21, Ktor 3.4.3, Exposed 1.2.0, Kotest 6.1.11, Kotlinter 5.4.2, Dokka 2.2.0, Postgres 42.7.10, Hikari 7.0.2, Flyway 12.4.0, Playwright 1.59.0, common-utils 2.8.1, plus other minor bumps. Unused entries removed from the version catalog.
- **Playwright migration.** Replaced legacy Cypress example specs, integration tests, fixtures, and `package.json` with Kotlin-based Playwright tests (`PlaywrightAuthTest`, `PlaywrightEndpointTest`) running under Kotest.
- **Cleanup.** Dropped unused `EmailUtils.kt` and `Emailer.kt`.

**Full Changelog**: https://github.com/readingbat/readingbat-core/compare/3.1.3...3.1.4

---

For history prior to 3.1.6, see [CHANGELOG.md](CHANGELOG.md).
