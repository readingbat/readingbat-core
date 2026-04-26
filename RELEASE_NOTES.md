# Release Notes

## v3.1.4 — 2026-04-25

Build modernization, dependency bumps, and frontend test migration from Cypress to Playwright.

### Highlights

- **Build refactor.** Repository declarations centralized in `settings.gradle.kts` with `FAIL_ON_PROJECT_REPOS`. Root `build.gradle.kts` now scopes Kotlin, publishing, lint, and test configuration per-subproject via small helper functions, making each subproject's build behavior easier to reason about.
- **Dependency bumps.** Kotlin 2.3.21, Ktor 3.4.3, Exposed 1.2.0, Kotest 6.1.11, Kotlinter 5.4.2, Dokka 2.2.0, Postgres 42.7.10, Hikari 7.0.2, Flyway 12.4.0, Playwright 1.59.0, common-utils 2.8.1, plus other minor bumps. Unused entries removed from the version catalog.
- **Playwright migration.** Replaced legacy Cypress example specs, integration tests, fixtures, and `package.json` with Kotlin-based Playwright tests (`PlaywrightAuthTest`, `PlaywrightEndpointTest`) running under Kotest.
- **Cleanup.** Dropped unused `EmailUtils.kt` and `Emailer.kt`.

**Full Changelog**: https://github.com/readingbat/readingbat-core/compare/3.1.3...3.1.4

---

For history prior to 3.1.4, see [CHANGELOG.md](CHANGELOG.md).
