# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Development Commands

### Build and Testing

- Build project: `make build` or `./gradlew build -xtest`
- Run tests: `make tests` or `./gradlew check`
- Clean build: `make clean` or `./gradlew clean`
- Continuous build: `make cc` or `./gradlew build --continuous -x test`

### Code Quality

- Lint Kotlin code: `make lint` or `./gradlew lintKotlinMain lintKotlinTest`
- Format code: `./gradlew formatKotlinMain formatKotlinTest`

### Running the Application

- Run application: `make run` or `./gradlew run`
- Create uberjar: `make uberjar` or `./gradlew uberjar`
- Run uberjar: `make uber` (builds and runs uberjar)

### Database Operations

- Database info: `make dbinfo` or `./gradlew flywayInfo`
- Migrate database: `make dbmigrate` or `./gradlew flywayMigrate`
- Reset database: `make dbreset` (clean + migrate)
- Clean database: `make dbclean` or `./gradlew flywayClean`
- Validate database: `make dbvalidate` or `./gradlew flywayValidate`

### E2E Testing

- Open Cypress tests: `make test` or `~/node_modules/.bin/cypress open`
- Run Cypress tests: `~/node_modules/.bin/cypress run --record --key 5ee5de19-1e84-4807-a199-5c70fda2fe5d`

### Dependency Management

- Check for dependency updates: `make versioncheck` or `./gradlew dependencyUpdates`
- View dependencies: `make depends` or `./gradlew dependencies`
- Upgrade Gradle wrapper: `make upgrade-wrapper`

### Publishing

- Publish to Maven Local: `make publish` or `./gradlew publishToMavenLocal`

## Project Architecture

### Multi-module Gradle Structure

- **readingbat-core/**: Main application module containing the web server, DSL engine, and core business logic
- **readingbat-kotest/**: Testing utilities module with Kotest support

### Core Components

#### Web Server (Ktor-based)

- Main server class: `ReadingBatServer` in `com.github.readingbat.server`
- Configuration driven by HOCON files and environment variables
- Supports multiple deployment targets (localhost, Digital Ocean, Google Cloud Run)
- Uses form-based authentication with session management

#### Content DSL Engine

- **ReadingBatContent**: Core DSL class for defining programming challenges
- **Language Support**: Java, Kotlin, and Python challenge types
- **Script Execution**: Sandboxed execution using JSR-223 scripting engines
- **Content Loading**: Dynamic loading from files or GitHub repositories

#### Database Layer

- **ORM**: JetBrains Exposed with PostgreSQL
- **Connection Pooling**: HikariCP
- **Migrations**: Flyway for database versioning
- **Multi-environment**: Supports local Docker, cloud databases

#### Key Packages

- `com.github.readingbat.dsl`: Content DSL and language-specific challenge types
- `com.github.readingbat.pages`: HTML page generation using Kotlinx.html
- `com.github.readingbat.server`: Core server infrastructure and routing
- `com.github.readingbat.posts`: Form handling for user interactions
- `com.github.readingbat.common`: Shared utilities, metrics, and constants

### Technology Stack

- **Language**: Kotlin with JVM target 17
- **Web Framework**: Ktor 3.2.3
- **Database**: PostgreSQL with Exposed ORM
- **Build Tool**: Gradle with Kotlin DSL
- **Testing**: Kotest framework + Cypress for E2E
- **Deployment**: Docker, Heroku, Google Cloud Run, Digital Ocean

### Configuration

- Application configuration via HOCON files in `src/main/resources/`
- Environment-specific configs for test, development, and production
- Environment variables for sensitive data (database credentials, API keys)
- Properties system with fallback to environment variables

### Development Workflow

- Kotlin code style enforced by kotlinter plugin
- Continuous integration with Travis CI
- Docker-based local PostgreSQL development
- Live reload during development with continuous build mode
