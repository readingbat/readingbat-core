# Changelog

All notable changes to ReadingBat Core are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased] (3.1.2)

### Changed

- OAuth providers are now auto-configured based on credential availability instead of requiring production mode
- Replaced stringly-typed OAuth provider parameter with `OAuthProvider` enum
- Set test configuration to non-production mode
- Bumped version to 3.1.2

### Fixed

- `extractBalancedContent` now correctly handles parentheses inside single- and double-quoted strings

### Added

- `for_loop1` Python challenge
- `OAuthProvider` enum for type-safe OAuth provider identification
- Test for parentheses inside quoted strings in `ParseUtilsTest`

### Removed

- `.travis.yml` (obsolete CI configuration)
- Redundant `isOAuthConfigured` property and outer OAuth route guard
- Unnecessary self-imports in `ConfigureOAuth` and `ReadingBatServer`

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

[Unreleased]: https://github.com/readingbat/readingbat-core/compare/3.0.11...HEAD
[3.0.11]: https://github.com/readingbat/readingbat-core/compare/3.0.10...3.0.11
[3.0.10]: https://github.com/readingbat/readingbat-core/compare/3.0.9...3.0.10
[3.0.9]: https://github.com/readingbat/readingbat-core/compare/3.0.8...3.0.9
[3.0.8]: https://github.com/readingbat/readingbat-core/compare/3.0.7...3.0.8
[3.0.7]: https://github.com/readingbat/readingbat-core/compare/3.0.6...3.0.7
[3.0.6]: https://github.com/readingbat/readingbat-core/compare/3.0.5...3.0.6
[3.0.5]: https://github.com/readingbat/readingbat-core/compare/3.0.4...3.0.5
[3.0.4]: https://github.com/readingbat/readingbat-core/compare/3.0.3...3.0.4
[3.0.3]: https://github.com/readingbat/readingbat-core/releases/tag/3.0.3
