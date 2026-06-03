# Changelog

All notable changes to ReadingBat Core are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [3.2.0] - Unreleased

### Changed

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
