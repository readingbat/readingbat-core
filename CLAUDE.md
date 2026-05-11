# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Development Commands

### Build and Test

- List Makefile targets: `make help` (self-documenting index — every target with a `## description` annotation)
- Build project: `make build` or `./gradlew build -xtest`
- Run all tests: `make tests` or `./gradlew check`
- Run a single test class: `./gradlew :readingbat-core:test --tests "EndpointTest"`
- Run a single test by name: `./gradlew :readingbat-core:test --tests "EndpointTest.Simple endpoint tests"`
- Run application: `make run` or `./gradlew run`

Gradle 9.5.0 with `org.gradle.parallel=true` and `org.gradle.configuration-cache=true` enabled by default. The version
catalog (`gradle/libs.versions.toml`) is the single source of truth for plugin, dependency, **and toolchain** versions —
the `gradle` and `jvm` keys are read by `build.gradle.kts` (via `libs.versions.jvm`) and by the Makefile (the
`upgrade-wrapper` target derives `GRADLE_VERSION` from the catalog). Project version comes from `gradle.properties`
(`-PoverrideVersion=...` overrides on the CLI).

### Code Quality

- Lint: `make lint` (runs `lintKotlinMain`, `lintKotlinTest`, and `detekt` in a single Gradle invocation)
- Format: `./gradlew formatKotlinMain formatKotlinTest`
- Kotlinter enforces ktlint code style — run format before committing
- Detekt static analysis via the `dev.detekt` 2.0 alpha plugin; config in `config/detekt/detekt.yml`, run standalone with `make detekt` or refresh the baseline with `make detekt-baseline`

### Coverage

- HTML report: `make coverage` or `make coverage-html` (`./gradlew koverHtmlReport`)
- XML report (CI/Codecov): `./gradlew koverXmlReport` (output at `build/reports/kover/report.xml`)
- Threshold check: `make coverage-verify` (`./gradlew koverVerify`)
- Per-package breakdown: `make coverage-packages` (runs `scripts/coverage_packages.py` against the XML report)
- Aggregated at the root project across `readingbat-core` and `readingbat-kotest`
- Codecov configuration in `codecov.yml` defines `server` / `dsl` / `pages` / `common` components for per-area visibility

### Database

- Reset database: `make dbreset` (clean + migrate)
- Migrate: `make dbmigrate` or `./gradlew flywayMigrate`
- Migration SQL lives in `src/main/resources/db/migration/`
- Requires PostgreSQL running locally (Docker setup in README.md)

### Secrets

Secrets are loaded from `secrets/secrets.env` (not committed). The root `build.gradle.kts` exposes a `SecretsEnvSource`
`ValueSource` (registered via `configureSecrets()`) and wires the resulting map as a task input on every `JavaExec` and
`Test` task. Edits to `secrets/secrets.env` invalidate the configuration cache and trigger a re-run of affected tasks.

## Project Architecture

### Module Structure

Two Gradle submodules under the root project:

- **readingbat-core/**: Main application — Ktor web server, DSL engine, database layer, HTML page generation
- **readingbat-kotest/**: Test utilities module providing `TestSupport` helpers for Kotest-based integration tests

### Content DSL Pipeline

The core innovation is a Kotlin DSL that defines programming challenges, which are evaluated at runtime via JSR-223
script engines:

1. **`Content.kt`** (in readingbat-core/src/main/kotlin/) defines content using `readingBatContent { }` DSL
2. **`ReadingBatContent`** holds three `LanguageGroup`s: `java`, `python`, `kotlin`
3. Each `LanguageGroup` contains `ChallengeGroup`s, each containing `Challenge`s
4. Content can be loaded from local files (`FileSystemSource`) or GitHub repos (`GitHubContent`)
5. **`ContentDsl.kt`** handles reading and evaluating DSL code — `readContentDsl()` reads source, `evalContentDsl()`
   evaluates it via Kotlin scripting
6. The DSL file and variable name are configured via `Property.DSL_FILE_NAME` and `Property.DSL_VARIABLE_NAME` (HOCON
   properties)

### Server Entry Point

- `ReadingBatServer.start()` launches the Ktor CIO engine via `EngineMain`
- `Application.module()` is the Ktor entry point — initializes properties, database, metrics, content DSL, and routing
- Configuration is supplied via HOCON `-config=` argument (defaults to `src/main/resources/application.conf`)

### Dual Configuration System

The app uses a two-layer configuration pattern where most settings can come from either source:

- **`Property`** (sealed class): HOCON-based properties read from Ktor's `ApplicationConfig`. Each is a singleton
  object (e.g., `Property.DBMS_URL`, `Property.IS_PRODUCTION`). Properties are initialized in `Application.module()` via
  `assignProperties()`.
- **`EnvVar`** (enum): Environment variables that override HOCON values. Pattern:
  `EnvVar.X.getEnv(Property.X.configValue(...))`.
- Properties are backed by `System.setProperty()` after initialization, making them globally accessible.

### Routing Architecture

- **Type-safe routing** via Ktor `@Resource` annotations in `Locations.kt`: `Language` → `Language.Group` →
  `Language.Group.Challenge` (nested resources mapping to URL paths like `/content/java/Warmup-1/hello`)
- **User routes** in `UserRoutes.kt`: standard GET/POST endpoints for pages, authentication, admin
- **OAuth routes** in `OAuthRoutes.kt`: GitHub and Google login/callback, conditionally registered per provider via
  `ConfigureOAuth.githubOAuthConfigured` / `googleOAuthConfigured` flags
- **Admin routes** in `AdminRoutes.kt` and **SysAdmin routes** in `SysAdminRoutes.kt`
- **WebSocket routes** in `server/ws/`: real-time updates for challenge answers, class summaries, student progress

### Authentication

- Form-based auth with salted password hashes and session cookies
- OAuth (GitHub, Google) via `ConfigureOAuth` — providers are auto-configured when credentials are present in env vars,
  not gated on production mode. The `OAuthProvider` enum in `OAuthRoutes.kt` provides type-safe provider identification.

### Page Generation

All HTML pages are generated server-side using Kotlinx.html (no templates). Each page has its own file in
`com.readingbat.pages` with a companion object function pattern (e.g., `ChallengePage.challengePage()`).
JavaScript for client-side interactivity is generated in `pages/js/`.

### Database Schema

Exposed ORM table definitions in `PostgresTables.kt`. Key tables:

- `UsersTable` — user accounts with salted password hashes
- `BrowserSessionsTable` / `UserSessionsTable` — session tracking
- `UserChallengeInfoTable` / `SessionChallengeInfoTable` — answer state per challenge
- `UserAnswerHistoryTable` / `SessionAnswerHistoryTable` — answer history
- `OAuthLinksTable` — links OAuth provider accounts to users
- `ClassesTable` / `EnrolleesTable` — teacher class management
- `ServerRequestsTable` / `GeoInfosTable` — request logging with geolocation

### Value Types

The codebase uses Kotlin `@JvmInline value class` extensively for type safety: `LanguageName`, `GroupName`,
`ChallengeName`, `ChallengeMd5`, `Password`, `FullName`, `ResetId` (all in `Locations.kt`).

### Testing

Tests use Kotest with `StringSpec` style. Use `StringSpec()` with an `init {}` block (not the constructor lambda pattern):

```kotlin
class FooTest : StringSpec() {
  init {
    "test name" {
      // ...
    }
  }
}
```

The `readingbat-kotest` module provides `TestSupport` with helpers:

- `testModule()` — sets up a Ktor test application with content
- `forEachLanguage` / `forEachGroup` / `forEachChallenge` — DSL for iterating content
- `answerAllWith()` / `answerAllWithCorrectAnswer()` — integration test helpers for checking answers via HTTP
- Test content is defined in `readingbat-core/src/test/kotlin/TestData.kt`
- Browser tests use Playwright (`com.microsoft.playwright:playwright`) and live in
  `readingbat-core/src/test/kotlin/com/readingbat/playwright/` (e.g., `PlaywrightAuthTest`, `PlaywrightEndpointTest`).
  These replaced the old Cypress specs.

### Key Dependencies

- **common-utils** 2.8.2 (BOM from `com.github.pambrose`): shared utility library providing core-utils, email-utils,
  exposed-utils, ktor-client/server-utils, script-utils, etc.
- **prometheus-proxy** 3.1.1: metrics collection
- **Kover** 0.9.1: code coverage, applied to every subproject and aggregated at the root; CI uploads
  `build/reports/kover/report.xml` to Codecov via `codecov-action@v5`
- Dependency versions managed in `gradle/libs.versions.toml`
