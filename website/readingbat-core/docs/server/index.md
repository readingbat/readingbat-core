---
icon: lucide/server
---

# Server

ReadingBat runs on a [Ktor](https://ktor.io/) web server using the CIO (Coroutine I/O) engine.
The server handles HTTP requests, serves HTML pages, manages WebSocket connections, and
integrates with PostgreSQL for persistence.

## Entry Point

The server is launched via `ReadingBatServer.start()`, which invokes Ktor's `EngineMain`.
The `Application.module()` function is the Ktor entry point that initializes all components:

1. Reads HOCON configuration and environment variables
2. Initializes the database connection pool (HikariCP)
3. Loads and evaluates the Content DSL
4. Registers HTTP routes, WebSocket endpoints, and static resources
5. Starts the Prometheus metrics agent (if enabled)

## Routing Architecture

ReadingBat uses Ktor's type-safe routing with `@Resource` annotations for content
navigation:

```
/content/{language}/{group}/{challenge}
```

The nested resource hierarchy:

| Resource | URL Pattern | Example |
|----------|-------------|---------|
| `Language` | `/content/{lang}` | `/content/java` |
| `Language.Group` | `/content/{lang}/{group}` | `/content/java/Warmup-1` |
| `Language.Group.Challenge` | `/content/{lang}/{group}/{challenge}` | `/content/java/Warmup-1/SleepIn` |

### Route Categories

| Category | Description |
|----------|-------------|
| **User routes** | Challenge pages, answer submission, account management |
| **Admin routes** | Class creation, student enrollment, progress dashboards |
| **SysAdmin routes** | System configuration, monitoring |
| **OAuth routes** | GitHub and Google authentication callbacks |
| **WebSocket routes** | Real-time updates for answers, progress, and dashboards |

## Page Generation

All HTML pages are generated server-side using [Kotlinx.html](https://github.com/Kotlin/kotlinx.html).
Each page has its own file in the `com.readingbat.pages` package following a companion
object function pattern:

```kotlin
// Example pattern (simplified)
object ChallengePage {
  fun challengePage(
    content: ReadingBatContent,
    challenge: Challenge,
    user: User?,
    // ...
  ): String = // returns HTML string
}
```

Client-side interactivity (answer checking, real-time updates) is handled by JavaScript
generated in the `pages/js/` package.

## WebSocket Endpoints

Real-time features use WebSocket connections:

| Endpoint | Purpose |
|----------|---------|
| Challenge WS | Live answer checking and feedback |
| Challenge Group WS | Group-level completion status |
| Class Summary WS | Teacher view of class progress |
| Student Summary WS | Individual student progress |
| Clock WS | Server time synchronization |
| Logging WS | Real-time log streaming |

WebSocket connections are validated for proper language, group, class code, and
enrollment status before establishing.

## Database

ReadingBat uses PostgreSQL with the [Exposed](https://github.com/JetBrains/Exposed) ORM.
Connection pooling is managed by HikariCP.

### Key Tables

| Table | Purpose |
|-------|---------|
| `UsersTable` | User accounts with salted password hashes |
| `BrowserSessionsTable` | Anonymous browser session tracking |
| `UserSessionsTable` | Maps sessions to authenticated users |
| `UserChallengeInfoTable` | Current answer state per user per challenge |
| `UserAnswerHistoryTable` | Historical record of all answer attempts |
| `ClassesTable` | Teacher-created classes |
| `EnrolleesTable` | Student enrollments in classes |
| `ServerRequestsTable` | Request logging |
| `GeoInfosTable` | IP geolocation data |

### Database Commands

```bash
# Reset database (clean + migrate)
make dbreset

# Run migrations only
make dbmigrate
# or
./gradlew flywayMigrate
```

Migration SQL files are located in `src/main/resources/db/migration/`.

## Script Engines

Challenge code is evaluated using JSR-223 script engine pools:

| Pool | Engine | Default Size |
|------|--------|-------------|
| `javaScriptPool` | Java scripting | 5 |
| `kotlinScriptPool` | Kotlin scripting | 5 |
| `pythonScriptPool` | Python (Jython) | 5 |
| `kotlinEvaluatorPool` | Kotlin expression eval | 5 |
| `pythonEvaluatorPool` | Python expression eval | 5 |

Pool sizes are configurable via HOCON properties (e.g., `readingbat.scripts.javaPoolSize`).

## Metrics

ReadingBat exports Prometheus metrics including:

- Content load counts and durations
- Language page view counts
- WebSocket connection metrics
- Cache size gauges
- Active session counts
- Request timing summaries

All metrics are labeled with the agent launch ID for multi-instance deployments.

## Running the Server

```bash
# Via Gradle
./gradlew run

# Via Make
make run

# With continuous build (excludes tests)
make cc
```

The server reads its configuration from `application.conf` specified via the
`-config=` JVM argument.
