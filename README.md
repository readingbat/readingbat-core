# ReadingBat Core

[![Release](https://jitpack.io/v/readingbat/readingbat-core.svg)](https://jitpack.io/#readingbat/readingbat-core)
[![Build Status](https://app.travis-ci.com/readingbat/readingbat-core.svg?branch=master)](https://app.travis-ci.com/readingbat/readingbat-core)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/8a5c67f5892042559490559142af30ec)](https://www.codacy.com/gh/readingbat/readingbat-core?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=readingbat/readingbat-core&amp;utm_campaign=Badge_Grade)
[![ReadingBat](https://img.shields.io/endpoint?url=https://dashboard.cypress.io/badge/simple/g5z7vz&style=flat&logo=cypress)](https://dashboard.cypress.io/projects/g5z7vz/runs)
[![Kotlin](https://img.shields.io/badge/%20language-Kotlin-red.svg)](https://kotlinlang.org/)
[![ktlint](https://img.shields.io/badge/ktlint%20code--style-%E2%9D%A4-FF4081)](https://pinterest.github.io/ktlint/)

A Kotlin-based framework for creating interactive programming challenges and educational content, powering the
ReadingBat platform for teaching Java, Kotlin, and Python programming concepts.

## 🚀 Features

- **Multi-Language Support**: Create challenges for Java, Kotlin, and Python
- **Interactive DSL**: Expressive domain-specific language for defining programming exercises
- **Web-Based Platform**: Built on Ktor with real-time WebSocket updates
- **User Management**: Complete authentication system with class/teacher support
- **Progress Tracking**: Detailed analytics and progress monitoring
- **Scalable Architecture**: Multi-server deployment ready with database persistence

## 🏗️ Architecture

ReadingBat Core is built using modern Kotlin technologies:

- **Web Framework**: Ktor 3.2.3 with CIO engine
- **Database**: PostgreSQL with Exposed ORM and HikariCP connection pooling
- **Authentication**: Form-based auth with session management
- **Script Execution**: JSR-223 scripting engines for safe code evaluation
- **Build System**: Gradle with Kotlin DSL and multi-module structure
- **Testing**: Kotest framework with Cypress for E2E testing

## 📁 Project Structure

```
readingbat-core/
├── readingbat-core/          # Main application module
│   ├── src/main/kotlin/
│   │   └── com/github/readingbat/
│   │       ├── dsl/          # Content DSL and challenge types
│   │       ├── pages/        # HTML page generation
│   │       ├── server/       # Core server infrastructure
│   │       ├── posts/        # Form handling
│   │       └── common/       # Shared utilities
│   └── src/main/resources/   # Configuration and static assets
├── readingbat-kotest/        # Testing utilities module
├── cypress/                  # E2E test suite
├── docs/                     # Documentation
└── sql/                      # Database migration scripts
```

## 🚦 Quick Start

### Prerequisites

- Java 17+
- Docker (for PostgreSQL)
- Node.js (for Cypress testing)

### Development Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/readingbat/readingbat-core.git
   cd readingbat-core
   ```

2. **Start PostgreSQL with Docker**
   ```bash
   mkdir -p $HOME/docker/volumes/postgres
   docker run --rm --name pg-docker -e POSTGRES_PASSWORD=docker -d -p 5432:5432 \
     -v $HOME/docker/volumes/postgres:/var/lib/postgresql/data postgres
   ```

3. **Setup database**
   ```bash
   make dbreset  # or ./gradlew flywayClean flywayMigrate
   ```

4. **Build and run**
   ```bash
   make build    # or ./gradlew build -xtest
   make run      # or ./gradlew run
   ```

5. **Open browser**
   Navigate to `http://localhost:8080`

### Environment Configuration

Create application configuration file or set environment variables:

```bash
# Database
export DBMS_URL="jdbc:pgsql://localhost:5432/readingbat"
export DBMS_USERNAME="postgres"
export DBMS_PASSWORD="docker"

# Optional: External services
export GITHUB_OAUTH="your_github_token"
export SENDGRID_API_KEY="your_sendgrid_key"
export IPGEOLOCATION_KEY="your_geo_key"
```

## 🛠️ Development Commands

### Build & Test

```bash
make build          # Build project (skip tests)
make tests          # Run unit tests
make lint           # Lint Kotlin code
make cc             # Continuous build mode
```

### Database Operations

```bash
make dbmigrate      # Run database migrations
make dbreset        # Clean and migrate database
make dbinfo         # Show migration status
```

### Running & Deployment

```bash
make run            # Run development server
make uberjar        # Create standalone JAR
make uber           # Build and run JAR
```

### Testing

```bash
make test           # Open Cypress test runner
npm run cypress:run # Run headless E2E tests
```

## 🎯 Creating Content

ReadingBat uses a powerful DSL for creating programming challenges:

```kotlin
readingBatContent {
  java {
    group("Warm-Up") {
      packageName = "com.github.readingbat.java.warmup"

      challenge("simple_addition") {
        returnType = IntType
        description = "Return the sum of two integers"

        function("addTwo(int a, int b)") {
          returnType = IntType
          addToCorrectAnswers(1 + 2, 3 + 4, 5 + 6)
        }
      }
    }
  }
}
```

See the [template repository](https://github.com/readingbat/readingbat-template) for complete examples.

## 🏗️ Deployment

### Local Development

```bash
# Using Docker Compose
docker-compose up -d postgres
make run
```

### Production Deployment

ReadingBat Core supports multiple deployment targets:

- **Heroku**: Uses `Procfile` and Heroku Postgres
- **Google Cloud Run**: Cloud SQL integration with connection pooling
- **Digital Ocean**: App Platform with managed PostgreSQL
- **Docker**: Containerized deployment with environment configuration

Required environment variables for production:

- `DBMS_URL`, `DBMS_USERNAME`, `DBMS_PASSWORD`
- `AGENT_ENABLED=true` (for monitoring)
- `SENDGRID_API_KEY` (for email notifications)

## 🧪 Testing

### Unit Tests

```bash
./gradlew test  # Run Kotest unit tests
```

### End-to-End Tests

```bash
# Start test database
make dbreset

# Run Cypress tests
make test                    # Interactive mode
~/node_modules/.bin/cypress run  # Headless mode
```

### Performance Testing

```bash
# Load testing endpoints
./gradlew build
java -jar build/libs/server.jar  # Run with JVM profiling
```

## 📊 Monitoring & Metrics

ReadingBat Core includes comprehensive monitoring:

- **Prometheus Metrics**: Application metrics and JVM stats
- **Request Tracking**: Detailed request logging and timing
- **User Analytics**: Challenge completion and progress tracking
- **Database Monitoring**: Connection pool and query performance

### Code Standards

- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use kotlinter for code formatting
- Write tests for new features
- Update documentation as needed

## 📚 Related Projects

- **[ReadingBat Template](https://github.com/readingbat/readingbat-template)**: Template for creating custom content

## 📄 License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.

---

**ReadingBat Core** - Making programming education interactive and engaging through hands-on challenges.
