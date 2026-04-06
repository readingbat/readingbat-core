# Contributing to ReadingBat

Thanks for your interest in contributing to ReadingBat! This guide will help you get started.

## Getting Started

1. Fork the repository
2. Clone your fork: `git clone git@github.com:<your-username>/readingbat-core.git`
3. Create a branch: `git checkout -b my-feature`
4. Set up the development environment (see below)

## Development Setup

### Prerequisites

- JDK 21+
- PostgreSQL (or Docker for a local instance)
- Gradle (wrapper included)

### Build and Run

```bash
# Build the project
./gradlew build -xtest

# Run tests
./gradlew check

# Run the application
./gradlew run

# Format code
./gradlew formatKotlinMain formatKotlinTest

# Lint
./gradlew lintKotlinMain lintKotlinTest
```

### Database

A local PostgreSQL instance is required. See the README for Docker setup. Run migrations with:

```bash
./gradlew flywayMigrate
```

### Secrets

Copy `.env.example` to `.env` and fill in the required values. This file is not committed.

## Making Changes

1. Make sure tests pass before and after your changes: `./gradlew check`
2. Run the formatter before committing: `./gradlew formatKotlinMain formatKotlinTest`
3. Write tests for new functionality
4. Keep commits focused — one logical change per commit

## Code Style

- This project uses [ktlint](https://pinterest.github.io/ktlint/) enforced via Kotlinter
- Use `StringSpec()` with `init {}` blocks for Kotest tests (not the constructor lambda)
- Use `@JvmInline value class` for domain-specific types where the codebase already does so

## Pull Requests

- Open PRs against the `master` branch
- Fill out the PR template
- Keep PRs focused on a single concern
- Ensure CI passes before requesting review

## Reporting Issues

Use the [issue templates](https://github.com/readingbat/readingbat-core/issues/new/choose) to report bugs or request features.

## License

By contributing, you agree that your contributions will be licensed under the [Apache License 2.0](../LICENSE).
