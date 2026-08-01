# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Development Commands

### Build and Test

- List Makefile targets: `make help` (self-documenting index — every target with a `## description` annotation)

Gradle 9.6.1 with `org.gradle.parallel=true` and `org.gradle.configuration-cache=true` enabled by default. The version
catalog (`gradle/libs.versions.toml`) is the single source of truth for plugin, dependency, **and toolchain** versions —
the `gradle-wrapper` and `jvm` keys are read by `build.gradle.kts` (via `libs.versions.jvm`) and by the Makefile (the
`upgrade-wrapper` target derives `GRADLE_VERSION` from the `gradle-wrapper` key in the catalog). Project version comes from `gradle.properties`
(`-PoverrideVersion=...` overrides on the CLI).

### Code Quality

- Lint: `make lint` (runs `lintKotlinMain`, `lintKotlinTest`, and `detekt` in a single Gradle invocation)
- Format: `./gradlew formatKotlinMain formatKotlinTest`
- Kotlinter enforces ktlint code style — run format before committing
- Detekt static analysis via the `dev.detekt` 2.0 alpha plugin; config in `config/detekt/detekt.yml`, run standalone with `make detekt` or refresh the baseline with `make detekt-baseline`

### Kotlin Conventions

- **Collection literals** (`[...]`) are enabled via the `-Xcollection-literals` compiler flag (set in `configureKotlin()`
  in the root `build.gradle.kts`). Prefer literal syntax over the factory functions in project sources: `emptyList()` →
  `[]`, `listOf(...)` → `[...]`, and `mutableListOf(...)` → `[...]` **with an explicit `MutableList<T>` type on the
  declaration** so mutability is not inferred away (e.g. `val xs: MutableList<String> = []`). Leave a call untouched when
  the element type can't be inferred from context (e.g. a Kotest `Any?`-typed `shouldBe` argument) — the compiler is the
  authority. **Content DSL files are exempt:** the flag applies to project sources only, not the JSR-223 script engine
  that evaluates content at runtime, so DSL content must keep using `listOf()`/`mutableListOf()`.

### Coverage

- Aggregated at the root project across `readingbat-core` and `readingbat-kotest`
- Codecov configuration in `codecov.yml` defines `server` / `dsl` / `pages` / `common` components for per-area visibility

### Database

- Migration SQL lives in `src/main/resources/db/migration/`
- Requires PostgreSQL running locally (Docker setup in README.md)

### Secrets

Secrets are loaded from `secrets/secrets.env` (not committed). The root `build.gradle.kts` exposes a `SecretsEnvSource`
`ValueSource` (registered via `configureSecrets()`) and wires the resulting map as a task input on every `JavaExec` and
`Test` task. Edits to `secrets/secrets.env` invalidate the configuration cache and trigger a re-run of affected tasks.

## Project Architecture

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

### Dual Configuration System

The app uses a two-layer configuration pattern where most settings can come from either source:

- **`Property`** (sealed class): HOCON-based properties read from Ktor's `ApplicationConfig`. Each is a singleton
  object (e.g., `Property.DBMS_URL`, `Property.IS_PRODUCTION`). Properties are initialized in `Application.module()` via
  `assignProperties()`.
- **`EnvVar`** (enum): Environment variables that override HOCON values. Pattern:
  `EnvVar.X.getEnv(Property.X.configValue(...))`.
- Properties are backed by `System.setProperty()` after initialization, making them globally accessible.

### Authentication

- Form-based auth with salted password hashes and session cookies
- Session cookies are signed and encrypted (`SessionTransportTransformerEncrypt`, AES-128 + HMAC-SHA256) in
  `ConfigureCookies.kt`. This requires a `SESSION_SECRET` (env var) that is **mandatory in production** — the server
  will not start without it. It must be identical on every node (so cookies validate across instances), and rotating it
  invalidates all existing sessions. Generate one with `openssl rand -hex 32`.
- The browser session is rotated on login to prevent session fixation.
- OAuth (GitHub, Google) via `ConfigureOAuth` — providers are auto-configured when credentials are present in env vars,
  not gated on production mode. The `OAuthProvider` enum in `OAuthRoutes.kt` provides type-safe provider identification.
  OAuth logins require a verified email from the provider.

### Page Generation

All HTML pages are generated server-side using Kotlinx.html (no templates). Each page has its own file in
`com.readingbat.pages` with a companion object function pattern (e.g., `ChallengePage.challengePage()`).
JavaScript for client-side interactivity is generated in `pages/js/`.

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

- **common-utils** (BOM from `com.github.pambrose`): `respondWith`/`redirectTo` take a `suspend` block as of 2.9.2
