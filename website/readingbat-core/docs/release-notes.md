---
icon: lucide/tag
---

# Release Notes

For the complete, commit-level history see [`CHANGELOG.md`](https://github.com/readingbat/readingbat-core/blob/master/CHANGELOG.md) in the repository.

## v3.2.1 — 2026-07-03

Maintenance release: a Gradle 9.6.1 upgrade, a routine dependency refresh, and two small build/test polish items. No functional changes to the running server and no upgrade steps — a drop-in bump from 3.2.0.

### Changed

- **Gradle 9.6.1** — the wrapper moves from 9.5.1 to 9.6.1.
- **Smarter pre-release version filter** — `configureVersions()` rejects a pre-release candidate only when the *current* dependency is stable, so libraries tracked on a pre-release line (e.g. the detekt 2.0 alpha) keep surfacing newer pre-releases. The `dependencyUpdates` task is also marked incompatible with the configuration cache.
- **`TestSupport.forEachAnswer` takes a `suspend` block** — matching the suspend `functionInfo()` call site introduced in 3.2.0.

### Dependencies

Gradle 9.5.1 → 9.6.1 · common-utils 2.9.2 → 2.9.3 · Ktor 3.5.0 → 3.5.1 · Exposed 1.3.0 → 1.3.1 · Kotest 6.2.0 → 6.2.1 · Flyway 12.8.1 → 12.10.0 · PostgreSQL driver 42.7.11 → 42.7.12 · Cloud SQL socket factory 1.28.4 → 1.28.6 · Playwright 1.60.0 → 1.61.0 · detekt 2.0.0-alpha.4 → 2.0.0-alpha.5 · maven-publish 0.36.0 → 0.37.0

[Full changelog: 3.2.0…3.2.1](https://github.com/readingbat/readingbat-core/compare/3.2.0...3.2.1)

## v3.2.0 — 2026-06-15

Security-hardening release. A multi-agent security review surfaced 48 confirmed findings (7 high, 19 medium, 22 low); all 48 are addressed here, alongside a batch of WebSocket/caching reliability fixes and the 3.1.9-era build/tooling cleanup.

!!! warning "Upgrade note — `SESSION_SECRET` is now required in production"

    Session cookies are now signed and encrypted, so the server **will not start
    in production without a `SESSION_SECRET`**. Generate one with
    `openssl rand -hex 32`, set it via the `SESSION_SECRET` environment variable,
    use the **same value on every node**, and note that **rotating it invalidates
    all existing sessions**. See [Configuration › Secrets](configuration/index.md#secrets).

### Security

- **Signed + encrypted session cookies** (`SessionTransportTransformerEncrypt`, AES-128 + HMAC-SHA256). The cookies were previously unsigned plaintext, allowing the `userId` to be forged for a trivial authentication bypass and admin takeover.
- **Server-side code injection (RCE) removed** — answer checking no longer evaluates the user's response as a script; list/array answers are parsed and compared directly.
- **Teacher IDOR closed** — class-management actions verify class ownership.
- **XSS fixes** — student answers over WebSockets are HTML-escaped; user-controlled names in `confirm()`/`onSubmit` handlers are escaped.
- **OAuth hardening** — Google and GitHub logins require a verified email; login rotates the browser session to prevent fixation.
- **Rate limiting enforced** per-IP; secrets masked in logs; the operational logging WebSocket requires admin.

### Reliability

- **WebSockets** — fixed pinger concurrent-modification crashes, removed a shared dispatcher that blocked all clients, collapsed N+1 blocking JDBC, and made the answer dashboard drop-oldest under backpressure.
- **Caching** — geo cache short-circuits the DB, drops its global mutex, stops caching failures permanently, and is size-bounded; a dir-contents cache key mismatch is fixed; per-user answer channels are bounded.
- **Parsing & startup** — nested-brace Kotlin script conversion, Java invocation ordering, quote-aware Python list parsing, a non-numeric env-var startup crash, and a bounded answer-check loop.
- **`Challenge.functionInfo()` is now `suspend`** — the blocking script eval runs on `Dispatchers.IO` instead of bridging through `runBlocking` inside request/WS coroutines.

### Dependencies

common-utils → 2.9.2 · Kotlin 2.4.0 · Ktor 3.5.0 · Exposed 1.3.0 · Kotest 6.2.0 · detekt 2.0.0-alpha.4 · HikariCP 7.1.0 · prometheus-proxy 3.2.0

[Full changelog: 3.1.8…3.2.0](https://github.com/readingbat/readingbat-core/compare/3.1.8...3.2.0)
