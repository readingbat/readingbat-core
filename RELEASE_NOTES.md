# Release Notes

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
