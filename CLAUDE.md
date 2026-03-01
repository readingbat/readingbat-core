# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Development Commands

### Build and Test

- Build project: `make build` or `./gradlew build -xtest`
- Run all tests: `make tests` or `./gradlew check`
- Run a single test class: `./gradlew :readingbat-core:test --tests "EndpointTest"`
- Run a single test by name: `./gradlew :readingbat-core:test --tests "EndpointTest.Simple endpoint tests"`
- Continuous build: `make cc` or `./gradlew build --continuous -x test`
- Run application: `make run` or `./gradlew run`

### Code Quality

- Lint: `make lint` or `./gradlew lintKotlinMain lintKotlinTest`
- Format: `./gradlew formatKotlinMain formatKotlinTest`
- Kotlinter enforces ktlint code style — run format before committing

### Database

- Reset database: `make dbreset` (clean + migrate)
- Migrate: `make dbmigrate` or `./gradlew flywayMigrate`
- Migration SQL lives in `src/main/resources/db/migration/`
- Requires PostgreSQL running locally (Docker setup in README.md)

### Secrets

Secrets are loaded from `secrets/secrets.env` (not committed). The root `build.gradle.kts` `configureSecrets()` function
reads this file and injects env vars into `JavaExec` and `Test` tasks.

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
- **Admin routes** in `AdminRoutes.kt` and **SysAdmin routes** in `SysAdminRoutes.kt`
- **WebSocket routes** in `server/ws/`: real-time updates for challenge answers, class summaries, student progress

### Page Generation

All HTML pages are generated server-side using Kotlinx.html (no templates). Each page has its own file in
`com.github.readingbat.pages` with a companion object function pattern (e.g., `ChallengePage.challengePage()`).
JavaScript for client-side interactivity is generated in `pages/js/`.

### Database Schema

Exposed ORM table definitions in `PostgresTables.kt`. Key tables:

- `UsersTable` — user accounts with salted password hashes
- `BrowserSessionsTable` / `UserSessionsTable` — session tracking
- `UserChallengeInfoTable` / `SessionChallengeInfoTable` — answer state per challenge
- `UserAnswerHistoryTable` / `SessionAnswerHistoryTable` — answer history
- `ClassesTable` / `EnrolleesTable` — teacher class management
- `ServerRequestsTable` / `GeoInfosTable` — request logging with geolocation

### Value Types

The codebase uses Kotlin `@JvmInline value class` extensively for type safety: `LanguageName`, `GroupName`,
`ChallengeName`, `ChallengeMd5`, `Password`, `FullName`, `ResetId` (all in `Locations.kt`).

### Testing

Tests use Kotest with `StringSpec` style. The `readingbat-kotest` module provides `TestSupport` with helpers:

- `testModule()` — sets up a Ktor test application with content
- `forEachLanguage` / `forEachGroup` / `forEachChallenge` — DSL for iterating content
- `answerAllWith()` / `answerAllWithCorrectAnswer()` — integration test helpers for checking answers via HTTP
- Test content is defined in `readingbat-core/src/test/kotlin/TestData.kt`

### Key Dependencies

- **common-utils** (BOM from `com.github.pambrose`): shared utility library providing core-utils, email-utils,
  exposed-utils, ktor-client/server-utils, script-utils, etc.
- **prometheus-proxy**: metrics collection
- Dependency versions managed in `gradle/libs.versions.toml`
