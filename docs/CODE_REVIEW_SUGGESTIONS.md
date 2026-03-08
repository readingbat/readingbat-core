# Code Review Suggestions - ReadingBat Core

**Date:** March 4, 2026
**Updated:** March 8, 2026
**Reviewed by:** Claude Code

## Executive Summary

ReadingBat Core is a well-architected Kotlin web application with good separation of concerns and proper use of modern
frameworks. The main concerns center on script execution security (no sandboxing or timeouts), authentication weaknesses
(SHA-256 password hashing, insecure cookie flags), and exposed admin endpoints. Code quality is high with consistent
patterns, good use of Kotlin idioms, and effective use of inline value classes for type safety. Test coverage for
security-critical paths (auth, authorization, WebSocket) is notably absent.

### Progress Since Initial Review

Of the 15 issues identified, 1 has been fully resolved, 1 is partially addressed, and 13 remain open. All 3 critical
issues and all 5 high-severity issues remain unaddressed.

## Strengths

### Architecture & Design

- Well-structured multi-module Gradle project with clear separation of concerns
- Clean DSL implementation for defining programming challenges
- Proper use of Ktor framework with interceptors and routing
- Good database schema design with proper indexes and constraints
- Script pool pattern (`ScriptPools.kt`) correctly pools expensive JSR-223 engines per language

### Security Positives

- Guava `RateLimiter` on login and account creation provides brute-force mitigation
- `KtorProperty` system with `maskFunc` properly redacts sensitive values in log output
- Environment variable obfuscation for sensitive data (`EnvVar.kt`)
- Session-based authentication with cookie configuration

### Code Quality

- Excellent use of Kotlin `@JvmInline value class` for `Password`, `Email`, `FullName`, etc., preventing type confusion
- Consistent Kotlin idioms and code style
- Good use of coroutines and structured concurrency
- HikariCP configured with `TRANSACTION_REPEATABLE_READ` isolation and explicit max lifetime
- `addImports()` in `ContentDsl.kt` surgically adds only necessary imports to DSL code
- WebSocket ping loop in `ChallengeWs.kt` uses `runCatching` to isolate per-session failures

---

## Critical Severity

### 1. Unrestricted Script Execution — No Sandbox or Timeout

**Status:** OPEN

**Files:**

- `readingbat-core/src/main/kotlin/com/github/readingbat/dsl/challenge/Challenge.kt` (lines ~304, ~356)
- `readingbat-core/src/main/kotlin/com/github/readingbat/server/ScriptPools.kt`

Challenge code from `.kt`, `.java`, and `.py` files is evaluated without any execution timeout or resource limit. The
`InfiniteLoop.kt` test content demonstrates a `while(true)` loop that would permanently occupy a script pool slot. With
a default pool size of 5, five concurrent infinite-loop requests would exhaust all script engine threads, causing
complete denial of service. Python and Java script engines execute in the same JVM without class loader isolation.

**Recommendation:** Wrap each pool `eval` call in `withTimeout(30.seconds)` and catch `TimeoutCancellationException` to
return the pool slot cleanly. Implement resource limits (CPU, memory) and restricted class access.

### 2. Auth Cookie Missing `secure` Flag in Production

**Status:** OPEN

**File:** `readingbat-core/src/main/kotlin/com/github/readingbat/server/ConfigureCookies.kt` (lines 49-50)

The `UserPrincipal` authentication cookie has `cookie.secure = true` commented out with no compensating mechanism. The
`HerokuHttpsRedirect` plugin is installed only when `production && redirectHostname.isNotBlank()`, meaning if the
redirect hostname is not configured, TLS enforcement is absent. The auth cookie can be transmitted over plain HTTP,
allowing session hijacking via network observation. The `BrowserSession` cookie has the same problem.

**Recommendation:** Unconditionally set `cookie.secure = true` in production, gated on the `production` parameter
already threaded through `installs()`.

### 3. Unauthenticated Sensitive Admin Endpoints

**Status:** OPEN

**File:** `readingbat-core/src/main/kotlin/com/github/readingbat/server/routes/AdminRoutes.kt` (lines 65-179)

The following endpoints are registered with no authentication checks:

- `GET /threaddump` — returns a full JVM thread dump (line 69)
- `GET /cookies` — exposes raw cookie data (line 84)
- `GET /clear-cookies`, `GET /clear-principal`, `GET /clear-sessionid` (lines 138-152)

The `/threaddump` endpoint reveals internal thread names, active database connections, coroutine stack frames, and other
implementation details. Unlike sys-admin routes (gated on `isProduction()` and `authenticateAdminUser()`), these routes
have no protection.

**Recommendation:** Wrap these endpoints with `authenticateAdminUser(fetchUser()) { ... }` or move them behind a
production-only routing block.

---

## High Severity

### 4. Password Hashing with SHA-256 — Insufficient for Passwords

**Status:** OPEN

**Files:**

- `readingbat-core/src/main/kotlin/com/github/readingbat/common/User.kt` (line ~699)
- `readingbat-core/src/main/kotlin/com/github/readingbat/server/ConfigureFormAuth.kt` (line ~85)

Passwords are stored as `sha256(password + salt)`. SHA-256 is a general-purpose hash, not a key derivation function. It
can be computed at billions of hashes per second on commodity GPUs, making brute-force of stolen hashes practical even
with a salt. Industry standard is bcrypt, scrypt, Argon2, or PBKDF2 with high iteration counts.

**Recommendation:** Replace with Argon2 or bcrypt. Provide a migration path: on next successful login with the old
SHA-256 digest, re-hash with the new algorithm.

### 5. `isValidUser()` Issues Database Query on Every Call

**Status:** OPEN

**File:** `readingbat-core/src/main/kotlin/com/github/readingbat/common/User.kt` (lines ~235-243)

`isInDbms()` issues a `SELECT COUNT(id)` query every invocation. `isValidUser()` is called transitively by
`isAdminUser()`, `isNotAdminUser()`, `isNotValidUser()`, and `fetchUser()`. In `authenticateAdminUser()` alone there are
two calls per request. A simple page render may hit the database 3-5 times just to validate the user.

**Recommendation:** Cache the `isInDbms()` result as a field on `User` at construction time. A once-loaded `User` object
with a valid `userDbmsId` does not need to re-query existence.

### 6. Unbounded Open Redirect at Logout

**Status:** OPEN

**File:** `readingbat-core/src/main/kotlin/com/github/readingbat/server/routes/UserRoutes.kt` (line ~247)

The `RETURN_PARAM` value is taken directly from the query string and used as a redirect destination with no validation.
An attacker can craft `GET /logout?returnPath=https://evil.com` to phish users post-logout. The same pattern appears in
`CreateAccountPost.kt` and `PasswordResetPost.kt`.

**Recommendation:** Validate that the redirect target is a relative path (starts with `/` and does not start with `//`).
Reject or default to `/` for any absolute URL.

### 7. `clearChallengeAnswers` / `clearGroupAnswers` — No Authorization Check

**Status:** OPEN

**File:** `readingbat-core/src/main/kotlin/com/github/readingbat/posts/ChallengePost.kt` (lines ~258-268)

Both functions accept a compound key from the POST body (e.g., `correct-answers:auth:userId:md5`) and split it to
determine which user's data to delete. There is no verification that the authenticated user matches the `userId`
embedded in the key. An authenticated user could craft a POST request with another user's `userId` to delete their
answer history.

**Recommendation:** After splitting the key, compare the `userId` from the key against `user?.userId` and reject
mismatches.

### 8. `newSingleThreadContext` — Deprecated API, Resource Leak

**Status:** OPEN

**Files:**

- `readingbat-core/src/main/kotlin/com/github/readingbat/server/ws/ChallengeWs.kt` (lines 121, 140)
- `readingbat-core/src/main/kotlin/com/github/readingbat/server/ws/LoggingWs.kt` (line 73)

`newSingleThreadContext()` is `@DelicateCoroutinesApi` and creates threads that are never `close()`d. Each context holds
an OS thread and a thread pool. If `initThreads()` were called more than once (e.g., in tests), leaks would accumulate.

**Recommendation:** Use a named `CoroutineScope` backed by `Dispatchers.IO` or a fixed-thread-pool dispatcher. Cancel it
on application shutdown via `ApplicationStopped` monitor.

---

## Medium Severity

### 9. ~~Dead Code — `configureSessionAuth()` Never Called~~

**Status:** RESOLVED

~~**File:** `readingbat-core/src/main/kotlin/com/github/readingbat/server/ConfigureFormAuth.kt`~~

The `configureSessionAuth()` function has been removed from the codebase.

### 10. `System.setProperty` for Configuration — Global Mutable State

**Status:** OPEN

**File:** `readingbat-core/src/main/kotlin/com/github/readingbat/common/Property.kt` (lines 91-93)

All configuration is stored via `System.setProperty`, making it process-global mutable state. The
`KtorProperty.instances` list accumulates instances at class load time. Re-initializing across multiple test
applications in the same JVM can produce double-initialization side effects.

**Recommendation:** Consider using Ktor's built-in `ApplicationConfig` passed through the call pipeline. At minimum,
ensure tests call a cleanup/reset function between runs.

### 11. Rate Limiting is Global, Not Per-IP or Per-Endpoint

**Status:** OPEN

**File:** `readingbat-core/src/main/kotlin/com/github/readingbat/server/Installs.kt` (lines 145-151)

The `RateLimit` plugin is installed with a single global `register {}` block with no `requestKey`. Without a
`requestKey`, one fast legitimate user can exhaust the limit for all users. The rate limit is not applied at the route
level for sensitive endpoints like login, account creation, or password reset.

**Recommendation:** Configure with `requestKey { call -> call.request.origin.remoteHost }` and apply tighter limits to
authentication endpoints specifically.

### 12. `runBlocking` Inside Ktor Route Configuration

**Status:** OPEN

**File:** `readingbat-core/src/main/kotlin/com/github/readingbat/server/ServerUtils.kt` (lines ~119-128)

`runBlocking` is called inside the Ktor route configuration lambda (not inside the handler). If
`measureEndpointRequest` ever does any real I/O during setup, this blocks the startup thread. `runBlocking` inside a
coroutine context can deadlock if the outer scope and blocked thread share the same dispatcher.

**Recommendation:** Route configuration is synchronous and does not need `runBlocking`. If metrics setup is
asynchronous,
do it at a higher level before routing configuration.

### 13. No Test Coverage for Auth, Authorization, or WebSocket Flows

**Status:** OPEN

**Files:** All files under `readingbat-core/src/test/kotlin/`

The test suite has three test classes (`EndpointTest`, `UserAnswersTest`, `InvokesTest`). There are no tests for:

- Login / logout flows
- Admin-only endpoint access control
- Password reset flow
- Account creation with invalid inputs
- WebSocket connection and message delivery
- Database interactions (all tests run without DBMS)
- Rate limiting behavior
- The `clearChallengeAnswers` / `clearGroupAnswers` authorization gap

**Recommendation:** Add integration tests for authentication and authorization flows as a priority. These are the most
security-critical code paths.

---

## Low Severity

### 14. `GeoInfosTable` Index on Primary Key is Redundant

**Status:** OPEN

**File:** `readingbat-core/src/main/kotlin/com/github/readingbat/server/PostgresTables.kt` (line ~36)

```kotlin
val geoInfosUnique = Index(listOf(GeoInfosTable.id), true, "geo_info_unique")
```

Creates a unique index on `id`, which is already the primary key. The actual desired unique constraint appears to be on
the `ip` column (the upsert in `GeoInfo.insert()` uses `geoInfosUnique` as the conflict index).

**Recommendation:** Change to `Index(listOf(GeoInfosTable.ip), true, "geo_info_unique")`.

### 15. Database Connection Pool Tuning

**Status:** PARTIALLY ADDRESSED

HikariCP now has `maximumPoolSize` and `maxLifetime` configured via properties in `ReadingBatServer.kt`. However, these
use hardcoded defaults with no environment-specific tuning.

**Recommendation:** Add environment-specific connection pool tuning with configurable connection timeout and idle
timeout.

---

## Resolution Summary

| #  | Issue                              | Severity | Status              |
|----|------------------------------------|----------|---------------------|
| 1  | Script sandboxing                  | Critical | OPEN                |
| 2  | Cookie secure flag                 | Critical | OPEN                |
| 3  | Admin endpoint auth                | Critical | OPEN                |
| 4  | Password hashing (SHA-256)         | High     | OPEN                |
| 5  | User validation N+1 queries        | High     | OPEN                |
| 6  | Open redirect                      | High     | OPEN                |
| 7  | Authorization check gap            | High     | OPEN                |
| 8  | Thread context leak                | High     | OPEN                |
| 9  | Dead code (`configureSessionAuth`) | Medium   | RESOLVED            |
| 10 | Global mutable state               | Medium   | OPEN                |
| 11 | Global rate limiting               | Medium   | OPEN                |
| 12 | `runBlocking` in routes            | Medium   | OPEN                |
| 13 | Security test coverage             | Medium   | OPEN                |
| 14 | Redundant index                    | Low      | OPEN                |
| 15 | Connection pool tuning             | Low      | PARTIALLY ADDRESSED |

## Priority Summary

| Priority | Items                                                                                                                   | Effort      |
|----------|-------------------------------------------------------------------------------------------------------------------------|-------------|
| Critical | Script sandboxing (#1), Cookie security (#2), Admin endpoints (#3)                                                      | Medium      |
| High     | Password hashing (#4), User validation N+1 (#5), Open redirect (#6), Authorization check (#7), Thread context leak (#8) | Medium-High |
| Medium   | Global state (#10), Rate limiting (#11), runBlocking (#12), Test coverage (#13)                                         | High        |
| Low      | Index fix (#14), Connection pool tuning (#15)                                                                           | Low         |

---

*This review was conducted using static code analysis. A full security audit and penetration testing is recommended
before production deployment.*
