# Release Notes

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
